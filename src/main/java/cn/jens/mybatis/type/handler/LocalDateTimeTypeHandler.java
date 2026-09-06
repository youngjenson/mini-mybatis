package cn.jens.mybatis.type.handler;

import cn.jens.mybatis.type.BaseTypeHandler;
import cn.jens.mybatis.type.JdbcType;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;

/** LocalDateTime 与 JDBC TIMESTAMP 之间的转换。 */
public class LocalDateTimeTypeHandler extends BaseTypeHandler<LocalDateTime> {

    @Override
    protected void setNonNullParameter(
            PreparedStatement statement,
            int parameterIndex,
            LocalDateTime parameter,
            JdbcType jdbcType) throws SQLException {
        statement.setObject(parameterIndex, parameter);
    }

    @Override
    protected LocalDateTime getNullableResult(ResultSet resultSet, String columnName)
            throws SQLException {
        return resultSet.getObject(columnName, LocalDateTime.class);
    }

    @Override
    protected LocalDateTime getNullableResult(ResultSet resultSet, int columnIndex)
            throws SQLException {
        return resultSet.getObject(columnIndex, LocalDateTime.class);
    }
}
