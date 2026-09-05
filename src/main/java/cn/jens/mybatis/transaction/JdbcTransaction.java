package cn.jens.mybatis.transaction;

import cn.jens.mybatis.exception.PersistenceException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/** 基于 JDBC Connection 的事务实现。 */
public class JdbcTransaction implements Transaction {

    private final DataSource dataSource;

    private final boolean autoCommit;

    private Connection connection;

    public JdbcTransaction(DataSource dataSource, boolean autoCommit) {
        this.dataSource = dataSource;
        this.autoCommit = autoCommit;
    }

    @Override
    public Connection getConnection() {
        if (connection == null) {
            connection = openConnection();
        }
        return connection;
    }

    @Override
    public void commit() {
        if (connection == null || autoCommit) {
            return;
        }
        try {
            connection.commit();
        } catch (SQLException e) {
            throw new PersistenceException("Failed to commit JDBC transaction", e);
        }
    }

    @Override
    public void rollback() {
        if (connection == null || autoCommit) {
            return;
        }
        try {
            connection.rollback();
        } catch (SQLException e) {
            throw new PersistenceException("Failed to roll back JDBC transaction", e);
        }
    }

    @Override
    public void close() {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException e) {
            throw new PersistenceException("Failed to close JDBC connection", e);
        } finally {
            connection = null;
        }
    }

    private Connection openConnection() {
        try {
            Connection newConnection = dataSource.getConnection();
            newConnection.setAutoCommit(autoCommit);
            return newConnection;
        } catch (SQLException e) {
            throw new PersistenceException("Failed to open JDBC connection", e);
        }
    }
}
