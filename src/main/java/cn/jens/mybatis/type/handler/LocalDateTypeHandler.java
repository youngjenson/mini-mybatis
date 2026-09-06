package cn.jens.mybatis.type.handler;

import cn.jens.mybatis.type.BaseTypeHandler;
import cn.jens.mybatis.type.JdbcType;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

/** LocalDate 与 JDBC DATE 之间的转换。 */
public class LocalDateTypeHandler extends BaseTypeHandler<LocalDate> {

    @Override
    protected void setNonNullParameter(
            PreparedStatement statement,
            int parameterIndex,
            LocalDate parameter,
            JdbcType jdbcType) throws SQLException {
        statement.setObject(parameterIndex, parameter);
    }

    @Override
    protected LocalDate getNullableResult(ResultSet resultSet, String columnName)
            throws SQLException {
        return resultSet.getObject(columnName, LocalDate.class);
    }

    @Override
    protected LocalDate getNullableResult(ResultSet resultSet, int columnIndex)
            throws SQLException {
        return resultSet.getObject(columnIndex, LocalDate.class);
    }
}
