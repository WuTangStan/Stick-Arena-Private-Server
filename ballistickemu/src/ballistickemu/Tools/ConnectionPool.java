package ballistickemu.Tools;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConnectionPool {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConnectionPool.class);
    
    private static final int DEFAULT_POOL_SIZE = 10;
    private static final int MAX_POOL_SIZE = 20;
    private static final long CONNECTION_TIMEOUT_MS = 30000; // 30 seconds
    private static final long VALIDATION_TIMEOUT_MS = 5000; // 5 seconds
    
    private final BlockingQueue<PooledConnection> connectionPool;
    private final AtomicInteger activeConnections = new AtomicInteger(0);
    private final AtomicInteger totalConnections = new AtomicInteger(0);
    private final ReentrantLock poolLock = new ReentrantLock();
    
    private final String url;
    private final String username;
    private final String password;
    private final int maxPoolSize;
    
    private volatile boolean shutdown = false;
    
    public ConnectionPool(String url, String username, String password) {
        this(url, username, password, DEFAULT_POOL_SIZE);
    }
    
    public ConnectionPool(String url, String username, String password, int initialPoolSize) {
        this.url = url;
        this.username = username;
        this.password = password;
        this.maxPoolSize = Math.min(initialPoolSize, MAX_POOL_SIZE);
        
        this.connectionPool = new ArrayBlockingQueue<>(maxPoolSize);
        
        // Initialize the connection pool
        initializePool(initialPoolSize);
        
        // Start connection validation thread
        startValidationThread();
        
        LOGGER.info("Connection pool initialized with {} connections", initialPoolSize);
    }
    
    private void initializePool(int initialSize) {
        for (int i = 0; i < initialSize; i++) {
            try {
                PooledConnection conn = createConnection();
                if (conn != null) {
                    connectionPool.offer(conn);
                    totalConnections.incrementAndGet();
                }
            } catch (SQLException e) {
                LOGGER.error("Failed to create initial connection", e);
            }
        }
    }
    
    private PooledConnection createConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(url, username, password);
        return new PooledConnection(conn, this);
    }
    
    public Connection getConnection() throws SQLException {
        if (shutdown) {
            throw new SQLException("Connection pool is shutdown");
        }
        
        long startTime = System.currentTimeMillis();
        
        try {
            // Try to get an existing connection from the pool
            PooledConnection pooledConn = connectionPool.poll();
            
            if (pooledConn != null) {
                // Validate the connection before returning it
                if (isConnectionValid(pooledConn.getConnection())) {
                    activeConnections.incrementAndGet();
                    LOGGER.debug("Reused connection from pool. Active: {}", activeConnections.get());
                    return pooledConn;
                } else {
                    // Connection is invalid, close it and create a new one
                    closeConnection(pooledConn);
                    totalConnections.decrementAndGet();
                }
            }
            
            // Create a new connection if pool is not full
            if (totalConnections.get() < maxPoolSize) {
                PooledConnection newConn = createConnection();
                totalConnections.incrementAndGet();
                activeConnections.incrementAndGet();
                LOGGER.debug("Created new connection. Total: {}, Active: {}", 
                    totalConnections.get(), activeConnections.get());
                return newConn;
            }
            
            // Wait for a connection to become available
            pooledConn = connectionPool.poll(CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (pooledConn != null) {
                if (isConnectionValid(pooledConn.getConnection())) {
                    activeConnections.incrementAndGet();
                    LOGGER.debug("Got connection after waiting. Active: {}", activeConnections.get());
                    return pooledConn;
                } else {
                    closeConnection(pooledConn);
                    totalConnections.decrementAndGet();
                }
            }
            
            // If we still don't have a connection, throw an exception
            long waitTime = System.currentTimeMillis() - startTime;
            throw new SQLException("Could not get connection from pool after " + waitTime + "ms");
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SQLException("Interrupted while waiting for connection", e);
        }
    }
    
    private boolean isConnectionValid(Connection conn) {
        try {
            return conn != null && !conn.isClosed() && conn.isValid((int) VALIDATION_TIMEOUT_MS / 1000);
        } catch (SQLException e) {
            return false;
        }
    }
    
    void returnConnection(PooledConnection conn) {
        if (conn == null || shutdown) {
            return;
        }
        
        try {
            // Reset connection state
            if (!conn.getConnection().isClosed()) {
                conn.getConnection().setAutoCommit(true);
                conn.getConnection().clearWarnings();
            }
            
            // Return to pool if it's not full
            if (connectionPool.size() < maxPoolSize) {
                connectionPool.offer(conn);
                LOGGER.debug("Returned connection to pool. Pool size: {}", connectionPool.size());
            } else {
                // Pool is full, close the connection
                closeConnection(conn);
                totalConnections.decrementAndGet();
                LOGGER.debug("Pool full, closed connection. Total: {}", totalConnections.get());
            }
        } catch (SQLException e) {
            LOGGER.warn("Error returning connection to pool", e);
            closeConnection(conn);
            totalConnections.decrementAndGet();
        } finally {
            activeConnections.decrementAndGet();
        }
    }
    
    private void closeConnection(PooledConnection conn) {
        try {
            if (conn != null && !conn.getConnection().isClosed()) {
                conn.getConnection().close();
            }
        } catch (SQLException e) {
            LOGGER.warn("Error closing connection", e);
        }
    }
    
    private void startValidationThread() {
        Thread validationThread = new Thread(() -> {
            while (!shutdown) {
                try {
                    Thread.sleep(30000); // Check every 30 seconds
                    validateConnections();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        validationThread.setDaemon(true);
        validationThread.setName("ConnectionPool-Validator");
        validationThread.start();
    }
    
    private void validateConnections() {
        poolLock.lock();
        try {
            int invalidConnections = 0;
            int poolSize = connectionPool.size();
            
            for (int i = 0; i < poolSize; i++) {
                PooledConnection conn = connectionPool.poll();
                if (conn != null) {
                    if (isConnectionValid(conn.getConnection())) {
                        connectionPool.offer(conn);
                    } else {
                        closeConnection(conn);
                        totalConnections.decrementAndGet();
                        invalidConnections++;
                    }
                }
            }
            
            if (invalidConnections > 0) {
                LOGGER.info("Removed {} invalid connections from pool. Pool size: {}", 
                    invalidConnections, connectionPool.size());
            }
        } finally {
            poolLock.unlock();
        }
    }
    
    public void shutdown() {
        shutdown = true;
        poolLock.lock();
        try {
            PooledConnection conn;
            while ((conn = connectionPool.poll()) != null) {
                closeConnection(conn);
                totalConnections.decrementAndGet();
            }
        } finally {
            poolLock.unlock();
        }
        LOGGER.info("Connection pool shutdown complete");
    }
    
    public String getPoolStatus() {
        return String.format("Pool Status - Total: %d, Active: %d, Available: %d, Max: %d",
            totalConnections.get(), activeConnections.get(), connectionPool.size(), maxPoolSize);
    }
    
    // Inner class to wrap connections and track their lifecycle
    private static class PooledConnection implements Connection {
        private final Connection delegate;
        private final ConnectionPool pool;
        private final long createdTime;
        
        public PooledConnection(Connection delegate, ConnectionPool pool) {
            this.delegate = delegate;
            this.pool = pool;
            this.createdTime = System.currentTimeMillis();
        }
        
        public Connection getConnection() {
            return delegate;
        }
        
        @Override
        public void close() throws SQLException {
            pool.returnConnection(this);
        }
        
        // Delegate all Connection methods to the wrapped connection
        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException { return delegate.unwrap(iface); }
        @Override
        public boolean isWrapperFor(Class<?> iface) throws SQLException { return delegate.isWrapperFor(iface); }
        @Override
        public java.sql.Statement createStatement() throws SQLException { return delegate.createStatement(); }
        @Override
        public java.sql.PreparedStatement prepareStatement(String sql) throws SQLException { return delegate.prepareStatement(sql); }
        @Override
        public java.sql.CallableStatement prepareCall(String sql) throws SQLException { return delegate.prepareCall(sql); }
        @Override
        public String nativeSQL(String sql) throws SQLException { return delegate.nativeSQL(sql); }
        @Override
        public void setAutoCommit(boolean autoCommit) throws SQLException { delegate.setAutoCommit(autoCommit); }
        @Override
        public boolean getAutoCommit() throws SQLException { return delegate.getAutoCommit(); }
        @Override
        public void commit() throws SQLException { delegate.commit(); }
        @Override
        public void rollback() throws SQLException { delegate.rollback(); }
        @Override
        public boolean isClosed() throws SQLException { return delegate.isClosed(); }
        @Override
        public java.sql.DatabaseMetaData getMetaData() throws SQLException { return delegate.getMetaData(); }
        @Override
        public void setReadOnly(boolean readOnly) throws SQLException { delegate.setReadOnly(readOnly); }
        @Override
        public boolean isReadOnly() throws SQLException { return delegate.isReadOnly(); }
        @Override
        public void setCatalog(String catalog) throws SQLException { delegate.setCatalog(catalog); }
        @Override
        public String getCatalog() throws SQLException { return delegate.getCatalog(); }
        @Override
        public void setTransactionIsolation(int level) throws SQLException { delegate.setTransactionIsolation(level); }
        @Override
        public int getTransactionIsolation() throws SQLException { return delegate.getTransactionIsolation(); }
        @Override
        public java.sql.SQLWarning getWarnings() throws SQLException { return delegate.getWarnings(); }
        @Override
        public void clearWarnings() throws SQLException { delegate.clearWarnings(); }
        @Override
        public java.sql.Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException { return delegate.createStatement(resultSetType, resultSetConcurrency); }
        @Override
        public java.sql.PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) throws SQLException { return delegate.prepareStatement(sql, resultSetType, resultSetConcurrency); }
        @Override
        public java.sql.CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException { return delegate.prepareCall(sql, resultSetType, resultSetConcurrency); }
        @Override
        public java.sql.Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException { return delegate.createStatement(resultSetType, resultSetConcurrency, resultSetHoldability); }
        @Override
        public java.sql.PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException { return delegate.prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability); }
        @Override
        public java.sql.CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException { return delegate.prepareCall(sql, resultSetType, resultSetConcurrency, resultSetHoldability); }
        @Override
        public java.sql.PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException { return delegate.prepareStatement(sql, autoGeneratedKeys); }
        @Override
        public java.sql.PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException { return delegate.prepareStatement(sql, columnIndexes); }
        @Override
        public java.sql.PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException { return delegate.prepareStatement(sql, columnNames); }
        @Override
        public java.sql.Clob createClob() throws SQLException { return delegate.createClob(); }
        @Override
        public java.sql.Blob createBlob() throws SQLException { return delegate.createBlob(); }
        @Override
        public java.sql.NClob createNClob() throws SQLException { return delegate.createNClob(); }
        @Override
        public java.sql.SQLXML createSQLXML() throws SQLException { return delegate.createSQLXML(); }
        @Override
        public boolean isValid(int timeout) throws SQLException { return delegate.isValid(timeout); }
        @Override
        public void setClientInfo(String name, String value) throws SQLException { delegate.setClientInfo(name, value); }
        @Override
        public void setClientInfo(java.util.Properties properties) throws SQLException { delegate.setClientInfo(properties); }
        @Override
        public String getClientInfo(String name) throws SQLException { return delegate.getClientInfo(name); }
        @Override
        public java.util.Properties getClientInfo() throws SQLException { return delegate.getClientInfo(); }
        @Override
        public java.sql.Array createArrayOf(String typeName, Object[] elements) throws SQLException { return delegate.createArrayOf(typeName, elements); }
        @Override
        public java.sql.Struct createStruct(String typeName, Object[] attributes) throws SQLException { return delegate.createStruct(typeName, attributes); }
        @Override
        public void setSchema(String schema) throws SQLException { delegate.setSchema(schema); }
        @Override
        public String getSchema() throws SQLException { return delegate.getSchema(); }
        @Override
        public void abort(java.util.concurrent.Executor executor) throws SQLException { delegate.abort(executor); }
        @Override
        public void setNetworkTimeout(java.util.concurrent.Executor executor, int milliseconds) throws SQLException { delegate.setNetworkTimeout(executor, milliseconds); }
        @Override
        public int getNetworkTimeout() throws SQLException { return delegate.getNetworkTimeout(); }
        @Override
        public void beginRequest() throws SQLException { delegate.beginRequest(); }
        @Override
        public void endRequest() throws SQLException { delegate.endRequest(); }
        @Override
        public boolean setShardingKeyIfValid(java.sql.ShardingKey shardingKey, java.sql.ShardingKey superShardingKey, int timeout) throws SQLException { return delegate.setShardingKeyIfValid(shardingKey, superShardingKey, timeout); }
        @Override
        public boolean setShardingKeyIfValid(java.sql.ShardingKey shardingKey, int timeout) throws SQLException { return delegate.setShardingKeyIfValid(shardingKey, timeout); }
        @Override
        public void setShardingKey(java.sql.ShardingKey shardingKey, java.sql.ShardingKey superShardingKey) throws SQLException { delegate.setShardingKey(shardingKey, superShardingKey); }
        @Override
        public void setShardingKey(java.sql.ShardingKey shardingKey) throws SQLException { delegate.setShardingKey(shardingKey); }
        @Override
        public java.sql.ShardingKey getShardingKey() throws SQLException { return delegate.getShardingKey(); }
        @Override
        public java.sql.ShardingKey getSuperShardingKey() throws SQLException { return delegate.getSuperShardingKey(); }
    }
}

