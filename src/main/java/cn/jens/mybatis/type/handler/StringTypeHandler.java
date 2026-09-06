package cn.jens.mybatis.type.handler;

import cn.jens.mybatis.type.BaseTypeHandler;
import cn.jens.mybatis.type.JdbcType;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** String 与字符型 JDBC 列之间的转换。 */
public class StringTypeHandler extends BaseTypeHandler<String> {

    @Override
    protected void setNonNullParameter(
            PreparedStatement statement,
            int parameterIndex,
            String parameter,
            JdbcType jdbcType) throws SQLException {
        statement.setString(parameterIndex, parameter);
    }

    @Override
    protected String getNullableResult(ResultSet resultSet, String columnName)
            throws SQLException {
        return resultSet.getString(columnName);
    }

    @Override
    protected String getNullableResult(ResultSet resultSet, int columnIndex)
            throws SQLException {
        return resultSet.getString(columnIndex);
    }
}
