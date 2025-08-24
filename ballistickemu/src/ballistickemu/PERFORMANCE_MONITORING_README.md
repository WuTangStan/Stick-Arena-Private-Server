# Performance Monitoring System for BallistickEMU

## Overview
This system has been designed to identify and resolve lag spikes and performance bottlenecks in your multiplayer game server. It includes comprehensive monitoring, connection pooling, and performance optimization features.

## New Features Added

### 1. Performance Monitor (`PerformanceMonitor.java`)
- **Real-time monitoring** of memory usage, thread count, and database performance
- **Automatic detection** of lag spikes, memory leaks, and performance issues
- **Detailed logging** to `performance_metrics.txt` for trend analysis
- **Configurable thresholds** for different types of performance issues

### 2. Connection Pool (`ConnectionPool.java`)
- **Eliminates database connection leaks** that were causing memory issues
- **Reuses connections** instead of creating new ones for each operation
- **Automatic connection validation** and cleanup
- **Configurable pool size** (default: 15 connections, max: 20)

### 3. Enhanced Game Packet Processing
- **Packet caching** to reduce processing overhead
- **Lag spike detection** during packet broadcasting
- **Performance metrics** for packet processing

### 4. Console Commands
- `performance` - Shows current performance metrics
- `performance reset` - Resets performance counters
- `performance dbpool` - Shows database connection pool status

## How to Use

### Starting the Server
The performance monitoring starts automatically when you start the server. You'll see:
```
Performance monitoring started
Database connection pool initialized successfully with 15 connections
```

### Monitoring Performance
1. **Check performance metrics**: Type `performance` in the console
2. **Reset counters**: Type `performance reset` to clear performance counters
3. **Check database pool**: Type `performance dbpool` to see connection pool status

### Performance Logs
- **`performance_metrics.txt`** - Contains detailed performance issue logs
- **`memory_metrics.txt`** - Contains memory usage logs (existing feature)

## Configuration

Edit `performance_config.properties` to tune performance parameters:

```properties
# Memory thresholds (as percentages)
memory.high_threshold=0.80      # 80% memory usage triggers warning
memory.spike_threshold=0.15     # 15% increase triggers warning

# Database connection pool
db.pool.initial_size=15         # Initial pool size
db.pool.max_size=20            # Maximum pool size

# Performance monitoring intervals
monitoring.memory_check_interval_ms=60000    # Check memory every minute
monitoring.thread_check_interval_ms=30000    # Check threads every 30 seconds
```

## Identifying Performance Issues

### Lag Spikes
- **Check**: `performance` command shows lag spike count
- **Logs**: Look for "LAG_SPIKE" entries in `performance_metrics.txt`
- **Common causes**: Database slow queries, memory pressure, high thread count

### Memory Issues
- **Check**: Memory usage percentage in performance summary
- **Logs**: Look for "MEMORY_SPIKE" or "HIGH_MEMORY" entries
- **Common causes**: Connection leaks, object accumulation, insufficient GC

### Database Issues
- **Check**: `performance dbpool` command shows pool status
- **Logs**: Look for "SLOW_DB_CONNECTION" or "DB_CONNECTION_ERROR"
- **Common causes**: Slow queries, connection timeouts, database server issues

### Thread Issues
- **Check**: Thread count in performance summary
- **Logs**: Look for "THREAD_SPIKE" or "HIGH_THREAD_COUNT"
- **Common causes**: Thread leaks, excessive concurrent operations

## Troubleshooting Common Issues

### High Memory Usage
1. Check if database connections are being properly closed
2. Look for memory leaks in the logs
3. Consider increasing JVM heap size
4. Check for object accumulation in caches

### Frequent Lag Spikes
1. Check database performance with `performance dbpool`
2. Look for slow packet processing in logs
3. Check network buffer settings
4. Monitor thread count for spikes

### Database Connection Issues
1. Check connection pool status
2. Verify database server performance
3. Check for slow queries
4. Consider increasing pool size if needed

## Performance Optimization Tips

### 1. Database Optimization
- Use the connection pool (already implemented)
- Monitor slow queries and optimize them
- Consider database indexing for frequently accessed data

### 2. Memory Management
- Monitor memory usage trends
- Set appropriate JVM heap size
- Use the `-XX:+UseG1GC` garbage collector for better performance

### 3. Network Optimization
- Monitor packet processing times
- Check for network buffer issues
- Optimize packet broadcasting for large rooms

### 4. Thread Management
- Monitor thread count trends
- Check for thread leaks
- Optimize concurrent operations

## JVM Recommendations

Add these JVM flags for optimal performance:

```bash
java -Xms2g -Xmx4g -XX:+UseG1GC -XX:+UseStringDeduplication -XX:MaxGCPauseMillis=200 -jar ballistickemu.jar
```

- `-Xms2g -Xmx4g`: Set initial and maximum heap size
- `-XX:+UseG1GC`: Use G1 garbage collector for better performance
- `-XX:+UseStringDeduplication`: Reduce memory usage for duplicate strings
- `-XX:MaxGCPauseMillis=200`: Limit GC pause time to 200ms

## Monitoring Dashboard

For production servers, consider setting up monitoring dashboards:

1. **Log aggregation** (ELK Stack, Graylog)
2. **Metrics collection** (Prometheus, Grafana)
3. **Alerting** for performance thresholds
4. **Trend analysis** for capacity planning

## Support

If you encounter issues:
1. Check the performance logs first
2. Use console commands to diagnose problems
3. Review the configuration file
4. Check JVM settings and system resources

The performance monitoring system will help you identify the root cause of lag spikes and optimize your server for better player experience.

