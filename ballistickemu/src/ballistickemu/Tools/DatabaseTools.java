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

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 *
 * @author Simon
 */
public class DatabaseTools {
	private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseTools.class);
	private static HikariDataSource dataSource;
	public static String user;
	public static String pass;
	public static String server;
	public static String database;

	public static void initializeConnectionPool() {
		HikariConfig config = new HikariConfig();
		config.setJdbcUrl("jdbc:mysql://" + server + "/" + database);
		config.setUsername(user);
		config.setPassword(pass);
		config.setMaximumPoolSize(10);
		config.setMinimumIdle(5);
		config.setIdleTimeout(1200000);
		config.setConnectionTimeout(10000);
		config.addDataSourceProperty("cachePrepStmts", "true");
		config.addDataSourceProperty("prepStmtCacheSize", "250");
		config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
		
		dataSource = new HikariDataSource(config);
	}

	public static Connection getDbConnection() throws SQLException {
		if (dataSource == null) {
			initializeConnectionPool();
		}
		return dataSource.getConnection();
	}

	public static void closeConnectionPool() {
		if (dataSource != null && !dataSource.isClosed()) {
			dataSource.close();
		}
	}

	public static void dbConnect() {
		try {
			initializeConnectionPool();
			LOGGER.info("Database connection pool initialized successfully");
			// Start a background thread to monitor pool stats
			startPoolMonitoring();
		} catch (Exception e) {
			LOGGER.error("Failed to initialize database connection pool", e);
			throw new RuntimeException("Database connection failed", e);
		}
	}

	private static void startPoolMonitoring() {
		Thread monitorThread = new Thread(() -> {
			while (!Thread.currentThread().isInterrupted()) {
				try {
					if (dataSource != null) {
						LOGGER.info("Pool Stats - Active: {}, Idle: {}, Total: {}, Waiting: {}", 
							dataSource.getHikariPoolMXBean().getActiveConnections(),
							dataSource.getHikariPoolMXBean().getIdleConnections(),
							dataSource.getHikariPoolMXBean().getTotalConnections(),
							dataSource.getHikariPoolMXBean().getThreadsAwaitingConnection());
					}
					Thread.sleep(300000); // Log every 5 minutes
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					break;
				}
			}
		});
		monitorThread.setDaemon(true);
		monitorThread.start();
	}

	// Helper method for executing queries with proper resource management
	public static void executeQuery(String sql, QueryCallback callback) {
		try (Connection conn = getDbConnection();
			 PreparedStatement ps = conn.prepareStatement(sql)) {
			callback.execute(ps);
		} catch (SQLException e) {
			LOGGER.error("Error executing query: " + sql, e);
			throw new RuntimeException("Query execution failed", e);
		}
	}

	// Interface for query callbacks
	public interface QueryCallback {
		void execute(PreparedStatement ps) throws SQLException;
	}

	public static int executeQuery(String Query) {
		try {
			if (dataSource.isClosed() || dataSource == null)
				dbConnect();

		} catch (SQLException e) {
		}
		try {

			return dataSource.getConnection().prepareStatement(Query).executeUpdate();
		} catch (SQLException e) {
			LOGGER.warn("There was an error executing query: " + Query + ". The exception returned was:", e);
		}
		return -1;
	}

	public static int executeQuery(PreparedStatement ps) {
		try {
			if (dataSource.isClosed() || dataSource == null)
				dbConnect();

		} catch (SQLException e) {
		}
		try {
			return ps.executeUpdate();
		} catch (SQLException e) {
			LOGGER.warn("There was an error executing query: " + ps.toString() + ". The exception returned was:", e);
		}
		return -1;
	}

	public static ResultSet executeSelectQuery(String Query) {
		try {
			if (dataSource.isClosed() || dataSource == null)
				dbConnect();

		} catch (SQLException e) {
		}

		try {
			return dataSource.getConnection().createStatement().executeQuery(Query);
		} catch (SQLException e) {
			LOGGER.warn("There was an error executing query: " + Query + ". The exception returned was:", e);
			return null;
		}
	}

	public static int getRowCount(PreparedStatement ps) {
		try {
			ResultSet rs = ps.executeQuery();
			rs.last();
			return rs.getRow();
		} catch (SQLException e) {
			LOGGER.warn("Exception executing query: + ", e);
		}
		return -1;
	}

}
