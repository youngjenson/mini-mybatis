package cn.jens.mybatis.type.handler;

import cn.jens.mybatis.type.BaseTypeHandler;
import cn.jens.mybatis.type.JdbcType;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Boolean 与 JDBC 布尔列之间的转换。 */
public class BooleanTypeHandler extends BaseTypeHandler<Boolean> {

    @Override
    protected void setNonNullParameter(
            PreparedStatement statement,
            int parameterIndex,
            Boolean parameter,
            JdbcType jdbcType) throws SQLException {
        statement.setBoolean(parameterIndex, parameter);
    }

    @Override
    protected Boolean getNullableResult(ResultSet resultSet, String columnName)
            throws SQLException {
        boolean value = resultSet.getBoolean(columnName);
        return resultSet.wasNull() ? null : value;
    }

    @Override
    protected Boolean getNullableResult(ResultSet resultSet, int columnIndex)
            throws SQLException {
        boolean value = resultSet.getBoolean(columnIndex);
        return resultSet.wasNull() ? null : value;
    }
}
