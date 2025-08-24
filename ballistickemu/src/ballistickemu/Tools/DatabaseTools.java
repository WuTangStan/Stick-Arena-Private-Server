/*
 *     THIS FILE AND PROJECT IS SUPPLIED FOR EDUCATIONAL PURPOSES ONLY.
 *
 *     This program is free software; you can redistribute it
 *     and/or modify it under the terms of the GNU General
 *     Public License as published by the Free Software
 *     Foundation; either version 2 of the License, or (at your
 *     option) any later version.
 *
 *     This program is distributed in the hope that it will be
 *     useful, but WITHOUT ANY WARRANTY; without even the
 *     implied warranty of MERCHANTABILITY or FITNESS FOR A
 *     PARTICULAR PURPOSE. See the GNU General Public License
 *     for more details.
 *
 *     You should have received a copy of the GNU General
 *     Public License along with this program; if not, write to
 *     the Free Software Foundation, Inc., 59 Temple Place,
 */
package ballistickemu.Tools;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Simon
 */
public class DatabaseTools {
	private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseTools.class);
	public static final ReentrantLock lock = new ReentrantLock();
	
	public static String user;
	public static String pass;
	public static String server;
	public static String database;
	private static ConnectionPool connectionPool;
	
	public static void dbConnect() {
		try {
			Class.forName("com.mysql.jdbc.Driver");
			
			// Initialize connection pool
			String url = "jdbc:mysql://" + server + "/" + database;
			connectionPool = new ConnectionPool(url, user, pass, 15); // Start with 15 connections
			
			LOGGER.info("Database connection pool initialized successfully with 15 connections");
		} catch (ClassNotFoundException e) {
			LOGGER.error("Failed to initialize database connection", e);
		}
	}
	
	public static Connection getDbConnection() throws SQLException {
		if (connectionPool == null) {
			LOGGER.error("Connection pool not initialized, falling back to direct connection");
			return DriverManager.getConnection(
				"jdbc:mysql://" + server + "/" + database,
				user,
				pass
			);
		}
		
		long startTime = System.currentTimeMillis();
		try {
			Connection conn = connectionPool.getConnection();
			long duration = System.currentTimeMillis() - startTime;
			
			// Record performance metrics
			PerformanceMonitor.recordDbOperation(duration);
			
			return conn;
		} catch (SQLException e) {
			long duration = System.currentTimeMillis() - startTime;
			PerformanceMonitor.recordDbOperation(duration);
			throw e;
		}
	}
	
	public static void executeQuery(String query, QueryCallback callback) {
		lock.lock();
		try (Connection conn = getDbConnection();
			 PreparedStatement stmt = conn.prepareStatement(query);
			 ResultSet rs = stmt.executeQuery()) {
			callback.process(rs);
		} catch (SQLException e) {
			LOGGER.error("Error executing query: {}", query, e);
		} finally {
			lock.unlock();
		}
	}
	
	public static int executeQuery(String query) {
		lock.lock();
		try (Connection conn = getDbConnection();
			 PreparedStatement stmt = conn.prepareStatement(query)) {
			return stmt.executeUpdate();
		} catch (SQLException e) {
			LOGGER.error("Error executing query: {}", query, e);
			return -1;
		} finally {
			lock.unlock();
		}
	}
	
	public static int executeQuery(PreparedStatement ps) {
		lock.lock();
		try {
			return ps.executeUpdate();
		} catch (SQLException e) {
			LOGGER.error("Error executing prepared statement: {}", ps, e);
			return -1;
		} finally {
			lock.unlock();
		}
	}
	
	public static String getConnectionPoolStatus() {
		if (connectionPool != null) {
			return connectionPool.getPoolStatus();
		}
		return "Connection pool not initialized";
	}
	
	public static void shutdownConnectionPool() {
		if (connectionPool != null) {
			connectionPool.shutdown();
			LOGGER.info("Connection pool shutdown complete");
		}
	}
	
	public static ResultSet executeSelectQuery(String query) {
		lock.lock();
		try {
			Connection conn = getDbConnection();
			return conn.createStatement().executeQuery(query);
		} catch (SQLException e) {
			LOGGER.error("Error executing select query: {}", query, e);
			return null;
		} finally {
			lock.unlock();
		}
	}
	
	public static int getRowCount(PreparedStatement ps) {
		lock.lock();
		try {
			ResultSet rs = ps.executeQuery();
			rs.last();
			return rs.getRow();
		} catch (SQLException e) {
			LOGGER.error("Error getting row count: {}", e.getMessage());
			return -1;
		} finally {
			lock.unlock();
		}
	}
	
	public interface QueryCallback {
		void process(ResultSet rs) throws SQLException;
	}
}