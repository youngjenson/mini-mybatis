package cn.jens.mybatis.executor.statement;

import cn.jens.mybatis.executor.parameter.ParameterHandler;
import cn.jens.mybatis.executor.resultset.ResultSetHandler;
import cn.jens.mybatis.mapping.MappedStatement;
import cn.jens.mybatis.scripting.BoundSql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/** 基于 JDBC PreparedStatement 的语句处理器。 */
public class PreparedStatementHandler implements StatementHandler {

    private final MappedStatement mappedStatement;

    private final BoundSql boundSql;

    private final ParameterHandler parameterHandler;

    private final ResultSetHandler resultSetHandler;

    public PreparedStatementHandler(
            MappedStatement mappedStatement,
            BoundSql boundSql,
            ParameterHandler parameterHandler,
            ResultSetHandler resultSetHandler) {
        this.mappedStatement = mappedStatement;
        this.boundSql = boundSql;
        this.parameterHandler = parameterHandler;
        this.resultSetHandler = resultSetHandler;
    }

    @Override
    public PreparedStatement prepare(Connection connection) throws SQLException {
        return connection.prepareStatement(boundSql.getSql());
    }

    @Override
    public void parameterize(PreparedStatement statement) throws SQLException {
        parameterHandler.setParameters(statement);
    }

    @Override
    public <T> List<T> query(PreparedStatement statement) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery()) {
            return resultSetHandler.handle(
                    resultSet,
                    mappedStatement.resultType(),
                    mappedStatement.resultMap()
            );
        }
    }

    @Override
    public int update(PreparedStatement statement) throws SQLException {
        return statement.executeUpdate();
    }

    @Override
    public BoundSql getBoundSql() {
        return boundSql;
    }
}
