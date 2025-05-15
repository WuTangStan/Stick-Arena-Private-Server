package ballistickemu.Tools;
import java.util.concurrent.locks.ReentrantLock;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;

/**
 * Database utility using HikariCP
 */
public class DatabaseTools {
	private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseTools.class);
	private static HikariDataSource dataSource;
	public static final ReentrantLock lock = new ReentrantLock();

	public static String user;
	public static String pass;
	public static String server;
	public static String database;

	public static void dbConnect() {
		try {
			initializeConnectionPool();
			LOGGER.info("Database connection pool initialized successfully");
			startPoolMonitoring();
		} catch (Exception e) {
			LOGGER.error("Failed to initialize database connection pool", e);
			throw new RuntimeException("Database connection failed", e);
		}
	}

	private static void initializeConnectionPool() {
		HikariConfig config = new HikariConfig();
		config.setJdbcUrl("jdbc:mysql://" + server + "/" + database);
		config.setUsername(user);
		config.setPassword(pass);
		config.setMaximumPoolSize(10);
		config.setMinimumIdle(5);
		config.setIdleTimeout(1200000);
		config.setConnectionTimeout(10000);
		config.setLeakDetectionThreshold(10000);
		config.addDataSourceProperty("cachePrepStmts", "true");
		config.addDataSourceProperty("prepStmtCacheSize", "250");
		config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

		dataSource = new HikariDataSource(config);
	}

	private static void startPoolMonitoring() {
		Thread monitorThread = new Thread(() -> {
			while (!Thread.currentThread().isInterrupted()) {
				try {
					if (dataSource != null) {
						LOGGER.info("Pool Stats - Active: {}, Idle: {}, Total: {}, Waiting: {}",
							new Object[] {
								dataSource.getHikariPoolMXBean().getActiveConnections(),
								dataSource.getHikariPoolMXBean().getIdleConnections(),
								dataSource.getHikariPoolMXBean().getTotalConnections(),
								dataSource.getHikariPoolMXBean().getThreadsAwaitingConnection()
							});
					}
					Thread.sleep(300000); // Every 5 mins
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}
		});
		monitorThread.setDaemon(true);
		monitorThread.start();
	}

	public static Connection getDbConnection() throws SQLException {
		return dataSource.getConnection();
	}

	public static void closeConnectionPool() {
		if (dataSource != null && !dataSource.isClosed()) {
			dataSource.close();
			LOGGER.info("Database connection pool shut down.");
		}
	}

	// === Execute Query with Callback ===
	public static void executeQuery(String sql, QueryCallback callback) {
		try (Connection conn = getDbConnection();
			 PreparedStatement ps = conn.prepareStatement(sql)) {
			callback.execute(ps);
		} catch (SQLException e) {
			LOGGER.error("Error executing query with callback: {}", sql, e);
			throw new RuntimeException("Query execution failed", e);
		}
	}

	// === Execute Update (INSERT/UPDATE/DELETE) ===
	public static int executeQuery(String query) {
		try (Connection conn = getDbConnection();
			 PreparedStatement stmt = conn.prepareStatement(query)) {
			return stmt.executeUpdate();
		} catch (SQLException e) {
			LOGGER.error("Error executing update query: {}", query, e);
			return -1;
		}
	}

	// === Execute Prepared Update ===
	public static int executeQuery(PreparedStatement ps) {
		try {
			return ps.executeUpdate();
		} catch (SQLException e) {
			LOGGER.error("Error executing prepared update: {}", ps, e);
			return -1;
		}
	}

	// === Execute Select Query ===
	public static ResultSet executeSelectQuery(String query) {
		try {
			Connection conn = getDbConnection();
			return conn.createStatement().executeQuery(query);
		} catch (SQLException e) {
			LOGGER.error("Error executing select query: {}", query, e);
			return null;
		}
	}

	public static int getRowCount(PreparedStatement ps) {
		try {
			ResultSet rs = ps.executeQuery();
			rs.last();
			return rs.getRow();
		} catch (SQLException e) {
			LOGGER.error("Error getting row count", e);
			return -1;
		}
	}

	public interface QueryCallback {
		void execute(PreparedStatement ps) throws SQLException;
	}
}
