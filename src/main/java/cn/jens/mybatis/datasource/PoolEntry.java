package cn.jens.mybatis.datasource;

import java.sql.Connection;

/** 池中保存的物理连接及其最近归还时间。 */
final class PoolEntry {

    private final Connection physicalConnection;

    private long lastUsedNanos;

    PoolEntry(Connection physicalConnection, long lastUsedNanos) {
        this.physicalConnection = physicalConnection;
        this.lastUsedNanos = lastUsedNanos;
    }

    Connection getPhysicalConnection() {
        return physicalConnection;
    }

    long getLastUsedNanos() {
        return lastUsedNanos;
    }

    void markUsed(long nowNanos) {
        lastUsedNanos = nowNanos;
    }
}
