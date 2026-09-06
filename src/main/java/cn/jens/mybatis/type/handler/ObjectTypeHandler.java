package cn.jens.mybatis.type.handler;

import cn.jens.mybatis.type.BaseTypeHandler;
import cn.jens.mybatis.type.JdbcType;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** 未找到更具体处理器时使用 JDBC 的通用对象方法。 */
public class ObjectTypeHandler extends BaseTypeHandler<Object> {

    @Override
    protected void setNonNullParameter(
            PreparedStatement statement,
            int parameterIndex,
            Object parameter,
            JdbcType jdbcType) throws SQLException {
        if (jdbcType == null) {
            statement.setObject(parameterIndex, parameter);
        } else {
            statement.setObject(parameterIndex, parameter, jdbcType.getCode());
        }
    }

    @Override
    protected Object getNullableResult(ResultSet resultSet, String columnName)
            throws SQLException {
        return resultSet.getObject(columnName);
    }

    @Override
    protected Object getNullableResult(ResultSet resultSet, int columnIndex)
            throws SQLException {
        return resultSet.getObject(columnIndex);
    }
}
