package cn.jens.mybatis.executor.statement;

import cn.jens.mybatis.scripting.BoundSql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

/** 负责 PreparedStatement 的创建、参数化与执行。 */
public interface StatementHandler {

    PreparedStatement prepare(Connection connection) throws SQLException;

    void parameterize(PreparedStatement statement) throws SQLException;

    <T> List<T> query(PreparedStatement statement) throws SQLException;

    int update(PreparedStatement statement) throws SQLException;

    BoundSql getBoundSql();
}
