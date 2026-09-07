package cn.jens.mybatis.datasource;

/**
 * 连接池在某一时刻的只读状态快照。
 *
 * @param activeConnectionCount 活跃连接数
 * @param idleConnectionCount 空闲连接数
 * @param requestCount 累计获取次数
 * @param waitCount 因连接不足而等待的次数
 * @param accumulatedWaitTimeMillis 累计等待毫秒数
 * @param badConnectionCount 累计丢弃的坏连接数
 * @param reclaimedConnectionCount 累计回收的超时连接数
 * @author YumJens
 */
public record PoolStateSnapshot(
        int activeConnectionCount,
        int idleConnectionCount,
        long requestCount,
        long waitCount,
        long accumulatedWaitTimeMillis,
        long badConnectionCount,
        long reclaimedConnectionCount) {
}
