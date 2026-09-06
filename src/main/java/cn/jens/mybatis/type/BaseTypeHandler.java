package cn.jens.mybatis.type;

import cn.jens.mybatis.exception.PersistenceException;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** 统一处理空值，把非空值转换留给具体处理器。 */
public abstract class BaseTypeHandler<T> implements TypeHandler<T> {

    @Override
    public final void setParameter(
            PreparedStatement statement,
            int parameterIndex,
            T parameter,
            JdbcType jdbcType) throws SQLException {
        if (parameter == null) {
            if (jdbcType == null) {
                throw new PersistenceException(
                        "JDBC type is required when binding a null parameter"
                );
            }
            statement.setNull(parameterIndex, jdbcType.getCode());
            return;
        }
        setNonNullParameter(statement, parameterIndex, parameter, jdbcType);
    }

    @Override
    public final T getResult(ResultSet resultSet, String columnName) throws SQLException {
        return getNullableResult(resultSet, columnName);
    }

    @Override
    public final T getResult(ResultSet resultSet, int columnIndex) throws SQLException {
        return getNullableResult(resultSet, columnIndex);
    }

    protected abstract void setNonNullParameter(
            PreparedStatement statement,
            int parameterIndex,
            T parameter,
            JdbcType jdbcType) throws SQLException;

    protected abstract T getNullableResult(
            ResultSet resultSet,
            String columnName) throws SQLException;

    protected abstract T getNullableResult(
            ResultSet resultSet,
            int columnIndex) throws SQLException;
}
