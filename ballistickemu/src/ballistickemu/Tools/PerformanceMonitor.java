package ballistickemu.Tools;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.sql.Connection;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PerformanceMonitor {
    private static final Logger LOGGER = LoggerFactory.getLogger(PerformanceMonitor.class);
    private static final String PERFORMANCE_LOG_FILE = "performance_metrics.txt";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    
    // Performance thresholds
    private static final double HIGH_MEMORY_THRESHOLD = 0.80; // 80% of max memory
    private static final double MEMORY_SPIKE_THRESHOLD = 0.15; // 15% increase in memory usage
    private static final int HIGH_THREAD_COUNT_THRESHOLD = 150;
    private static final long LAG_SPIKE_THRESHOLD_MS = 100; // 100ms threshold for lag detection
    
    // Performance counters
    private static final AtomicLong lastMemoryCheck = new AtomicLong(0);
    private static final AtomicLong lastThreadCheck = new AtomicLong(0);
    private static final AtomicLong lastDbCheck = new AtomicLong(0);
    private static final AtomicInteger lagSpikeCount = new AtomicInteger(0);
    private static final AtomicInteger memorySpikeCount = new AtomicInteger(0);
    private static final AtomicInteger threadSpikeCount = new AtomicInteger(0);
    
    // Historical data for trend analysis
    private static final ConcurrentHashMap<String, Long> performanceHistory = new ConcurrentHashMap<>();
    private static long lastUsedMemory = 0;
    private static long lastThreadCount = 0;
    
    // Database performance tracking
    private static final AtomicLong totalDbOperations = new AtomicLong(0);
    private static final AtomicLong slowDbOperations = new AtomicLong(0);
    private static final AtomicLong dbConnectionErrors = new AtomicLong(0);
    
    public static void logPerformanceMetrics() {
        long currentTime = System.currentTimeMillis();
        
        // Check memory usage
        if (currentTime - lastMemoryCheck.get() >= 60000) { // Every minute
            checkMemoryUsage();
            lastMemoryCheck.set(currentTime);
        }
        
        // Check thread count
        if (currentTime - lastThreadCheck.get() >= 30000) { // Every 30 seconds
            checkThreadCount();
            lastThreadCheck.set(currentTime);
        }
        
        // Check database performance
        if (currentTime - lastDbCheck.get() >= 120000) { // Every 2 minutes
            checkDatabasePerformance();
            lastDbCheck.set(currentTime);
        }
        
        // Log system health summary every 5 minutes
        if (currentTime % 300000 < 60000) { // Every 5 minutes
            logSystemHealthSummary();
        }
    }
    
    private static void logSystemHealthSummary() {
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long maxMemory = runtime.maxMemory() / (1024 * 1024);
        double memoryUsage = (usedMemory * 100.0) / maxMemory;
        
        StringBuilder summary = new StringBuilder();
        summary.append("=== SYSTEM HEALTH SUMMARY ===\n");
        summary.append(String.format("Timestamp: %s%n", DATE_FORMAT.format(new Date())));
        summary.append(String.format("Memory Usage: %.1f%% (%d MB / %d MB)%n", memoryUsage, usedMemory, maxMemory));
        summary.append(String.format("Active Threads: %d%n", Thread.activeCount()));
        summary.append(String.format("Total DB Operations: %d%n", totalDbOperations.get()));
        summary.append(String.format("Slow DB Operations: %d%n", slowDbOperations.get()));
        summary.append(String.format("Lag Spikes Detected: %d%n", lagSpikeCount.get()));
        summary.append(String.format("Memory Spikes: %d%n", memorySpikeCount.get()));
        summary.append(String.format("Thread Spikes: %d%n", threadSpikeCount.get()));
        summary.append(String.format("DB Connection Errors: %d%n", dbConnectionErrors.get()));
        summary.append("=============================\n");
        
        logToFile(summary.toString());
    }
    
    private static void checkMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory() / (1024 * 1024);
        long freeMemory = runtime.freeMemory() / (1024 * 1024);
        long usedMemory = totalMemory - freeMemory;
        long maxMemory = runtime.maxMemory() / (1024 * 1024);
        
        double memoryUsagePercent = (usedMemory * 100.0) / maxMemory;
        
        // Check for high memory usage
        if (memoryUsagePercent > (HIGH_MEMORY_THRESHOLD * 100)) {
            memorySpikeCount.incrementAndGet();
            logPerformanceIssue("HIGH_MEMORY", 
                String.format("Memory usage: %.1f%% (%.1f MB used of %.1f MB max)", 
                    memoryUsagePercent, usedMemory, maxMemory));
        }
        
        // Check for memory spikes
        if (lastUsedMemory > 0) {
            double memoryIncrease = (usedMemory - lastUsedMemory) * 100.0 / lastUsedMemory;
            if (memoryIncrease > MEMORY_SPIKE_THRESHOLD * 100) {
                logPerformanceIssue("MEMORY_SPIKE", 
                    String.format("Memory spike: %.1f%% increase (%.1f MB to %.1f MB)", 
                        memoryIncrease, lastUsedMemory, usedMemory));
            }
        }
        
        lastUsedMemory = usedMemory;
        
        // Log memory metrics to file (not console)
        logToFile(String.format("Memory Usage - Used: %d MB (%.1f%%), Free: %d MB, Total: %d MB, Max: %d MB",
            usedMemory, memoryUsagePercent, freeMemory, totalMemory, maxMemory));
    }
    
    private static void checkThreadCount() {
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        int threadCount = threadBean.getThreadCount();
        
        // Check for high thread count
        if (threadCount > HIGH_THREAD_COUNT_THRESHOLD) {
            threadSpikeCount.incrementAndGet();
            logPerformanceIssue("HIGH_THREAD_COUNT", 
                String.format("High thread count: %d threads", threadCount));
        }
        
        // Check for thread count spikes
        if (lastThreadCount > 0) {
            double threadIncrease = (threadCount - lastThreadCount) * 100.0 / lastThreadCount;
            if (threadIncrease > 20) { // 20% increase threshold
                logPerformanceIssue("THREAD_SPIKE", 
                    String.format("Thread spike: %.1f%% increase (%d to %d threads)", 
                        threadIncrease, lastThreadCount, threadCount));
            }
        }
        
        lastThreadCount = threadCount;
        
        // Log thread metrics to file (not console)
        logToFile(String.format("Thread Count: %d (Peak: %d)", threadCount, threadBean.getPeakThreadCount()));
    }
    
    private static void checkDatabasePerformance() {
        // Test database connection performance
        long startTime = System.currentTimeMillis();
        try (Connection conn = DatabaseTools.getDbConnection()) {
            long connectionTime = System.currentTimeMillis() - startTime;
            
            if (connectionTime > 100) { // Connection taking more than 100ms
                logPerformanceIssue("SLOW_DB_CONNECTION", 
                    String.format("Slow database connection: %d ms", connectionTime));
            }
            
            // Test a simple query
            startTime = System.currentTimeMillis();
            try (var stmt = conn.createStatement();
                 var rs = stmt.executeQuery("SELECT 1")) {
                long queryTime = System.currentTimeMillis() - startTime;
                
                if (queryTime > 50) { // Query taking more than 50ms
                    logPerformanceIssue("SLOW_DB_QUERY", 
                        String.format("Slow database query: %d ms", queryTime));
                }
            }
            
        } catch (SQLException e) {
            dbConnectionErrors.incrementAndGet();
            logPerformanceIssue("DB_CONNECTION_ERROR", 
                String.format("Database connection error: %s", e.getMessage()));
        }
        
        // Log detailed database performance to file (not console)
        logToFile("=== DATABASE PERFORMANCE CHECK ===");
        logToFile(String.format("Total Operations: %d", totalDbOperations.get()));
        logToFile(String.format("Slow Operations (>100ms): %d", slowDbOperations.get()));
        logToFile(String.format("Connection Errors: %d", dbConnectionErrors.get()));
        
        // Get connection pool status if available
        try {
            String poolStatus = DatabaseTools.getConnectionPoolStatus();
            logToFile(String.format("Connection Pool: %s", poolStatus));
        } catch (Exception e) {
            logToFile("Connection Pool: Status unavailable");
        }
        logToFile("=================================");
    }
    
    public static void recordDbOperation(long durationMs) {
        totalDbOperations.incrementAndGet();
        if (durationMs > 100) { // Operations taking more than 100ms
            slowDbOperations.incrementAndGet();
        }
    }
    
    public static void recordLagSpike(long durationMs) {
        if (durationMs > LAG_SPIKE_THRESHOLD_MS) {
            lagSpikeCount.incrementAndGet();
            logPerformanceIssue("LAG_SPIKE", 
                String.format("Lag spike detected: %d ms", durationMs));
        }
    }
    
    private static void logPerformanceIssue(String issueType, String details) {
        String timestamp = DATE_FORMAT.format(new Date());
        String logMessage = String.format("[%s] %s: %s%n", timestamp, issueType, details);
        
        try (PrintWriter writer = new PrintWriter(new FileWriter(PERFORMANCE_LOG_FILE, true))) {
            writer.print(logMessage);
        } catch (IOException e) {
            LOGGER.error("Failed to write performance log", e);
        }
        
        // Only log critical issues to console, everything else goes to file
        if (issueType.contains("ERROR") || issueType.contains("CRITICAL")) {
            LOGGER.warn("Performance Issue - {}: {}", issueType, details);
        }
    }
    
    private static void logToFile(String message) {
        String timestamp = DATE_FORMAT.format(new Date());
        String logMessage = String.format("[%s] INFO: %s%n", timestamp, message);
        
        try (PrintWriter writer = new PrintWriter(new FileWriter(PERFORMANCE_LOG_FILE, true))) {
            writer.print(logMessage);
        } catch (IOException e) {
            LOGGER.error("Failed to write to performance log", e);
        }
    }
    
    public static String getPerformanceSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("=== Performance Summary ===\n");
        summary.append(String.format("Lag Spikes: %d\n", lagSpikeCount.get()));
        summary.append(String.format("Memory Spikes: %d\n", memorySpikeCount.get()));
        summary.append(String.format("Thread Spikes: %d\n", threadSpikeCount.get()));
        summary.append(String.format("Total DB Operations: %d\n", totalDbOperations.get()));
        summary.append(String.format("Slow DB Operations: %d\n", slowDbOperations.get()));
        summary.append(String.format("DB Connection Errors: %d\n", dbConnectionErrors.get()));
        
        // Current system state
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long maxMemory = runtime.maxMemory() / (1024 * 1024);
        double memoryUsage = (usedMemory * 100.0) / maxMemory;
        
        summary.append(String.format("Current Memory Usage: %.1f%% (%d MB / %d MB)\n", 
            memoryUsage, usedMemory, maxMemory));
        summary.append(String.format("Current Thread Count: %d\n", Thread.activeCount()));
        
        return summary.toString();
    }
    
    public static void resetCounters() {
        lagSpikeCount.set(0);
        memorySpikeCount.set(0);
        threadSpikeCount.set(0);
        totalDbOperations.set(0);
        slowDbOperations.set(0);
        dbConnectionErrors.set(0);
        
        String timestamp = DATE_FORMAT.format(new Date());
        logToFile(String.format("[%s] PERFORMANCE COUNTERS RESET", timestamp));
        LOGGER.info("Performance counters reset");
    }
    
    public static void logServerStart() {
        String timestamp = DATE_FORMAT.format(new Date());
        logToFile(String.format("[%s] ========================================", timestamp));
        logToFile(String.format("[%s] SERVER STARTED - Performance monitoring active", timestamp));
        logToFile(String.format("[%s] ========================================", timestamp));
    }
    
    public static void logServerStop() {
        String timestamp = DATE_FORMAT.format(new Date());
        logToFile(String.format("[%s] ========================================", timestamp));
        logToFile(String.format("[%s] SERVER STOPPING - Final performance summary", timestamp));
        logToFile(String.format("[%s] %s", timestamp, getPerformanceSummary()));
        logToFile(String.format("[%s] ========================================", timestamp));
    }
}

