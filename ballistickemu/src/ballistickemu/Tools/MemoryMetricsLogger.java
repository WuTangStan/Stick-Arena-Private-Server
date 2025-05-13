package ballistickemu.Tools;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MemoryMetricsLogger {
    private static final Logger LOGGER = LoggerFactory.getLogger(MemoryMetricsLogger.class);
    private static final String METRICS_FILE = "memory_metrics.txt";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final AtomicLong lastLogTime = new AtomicLong(0);
    private static final long LOG_INTERVAL = 300000; // 5 minutes in milliseconds
    
    // Memory thresholds
    private static final double HIGH_MEMORY_THRESHOLD = 0.85; // 85% of max memory
    private static final double MEMORY_SPIKE_THRESHOLD = 0.20; // 20% increase in memory usage
    private static long lastUsedMemory = 0;
    
    public static void logMemoryMetrics() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastLogTime.get() < LOG_INTERVAL) {
            return;
        }
        lastLogTime.set(currentTime);
        
        try (PrintWriter writer = new PrintWriter(new FileWriter(METRICS_FILE, true))) {
            Runtime runtime = Runtime.getRuntime();
            long totalMemory = runtime.totalMemory() / (1024 * 1024);
            long freeMemory = runtime.freeMemory() / (1024 * 1024);
            long usedMemory = totalMemory - freeMemory;
            long maxMemory = runtime.maxMemory() / (1024 * 1024);
            
            // Get thread count
            int threadCount = Thread.activeCount();
            
            // Get timestamp
            String timestamp = DATE_FORMAT.format(new Date());
            
            boolean shouldLog = false;
            StringBuilder logMessage = new StringBuilder();
            
            // Check for high memory usage
            if (usedMemory > (maxMemory * HIGH_MEMORY_THRESHOLD)) {
                shouldLog = true;
                logMessage.append(String.format("[%s] WARNING: High memory usage detected (%.1f%% of max)%n",
                    timestamp, (usedMemory * 100.0 / maxMemory)));
            }
            
            // Check for memory spike
            if (lastUsedMemory > 0) {
                double memoryIncrease = (usedMemory - lastUsedMemory) * 100.0 / lastUsedMemory;
                if (memoryIncrease > MEMORY_SPIKE_THRESHOLD) {
                    shouldLog = true;
                    logMessage.append(String.format("[%s] WARNING: Memory spike detected (%.1f%% increase)%n",
                        timestamp, memoryIncrease));
                }
            }
            
            // Check for high thread count
            if (threadCount > 100) {
                shouldLog = true;
                logMessage.append(String.format("[%s] WARNING: High thread count detected (%d threads)%n",
                    timestamp, threadCount));
            }
            
            // Only log if there's something significant to report
            if (shouldLog) {
                // Add the current memory state
                logMessage.append(String.format("[%s] Current Memory State - Used: %dMB, Free: %dMB, Total: %dMB, Max: %dMB, Threads: %d%n",
                    timestamp, usedMemory, freeMemory, totalMemory, maxMemory, threadCount));
                
                writer.print(logMessage.toString());
            }
            
            lastUsedMemory = usedMemory;
            
        } catch (IOException e) {
            LOGGER.error("Failed to write memory metrics to file", e);
        }
    }
} 