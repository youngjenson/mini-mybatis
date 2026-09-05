package cn.jens.mybatis.transaction;

import java.sql.Connection;

/** 管理一个 SqlSession 使用的 JDBC 连接与事务。 */
public interface Transaction extends AutoCloseable {

    Connection getConnection();

    void commit();

    void rollback();

    @Override
    void close();
}
