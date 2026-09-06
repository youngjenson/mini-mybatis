package cn.jens.mybatis.type.handler;

import cn.jens.mybatis.type.BaseTypeHandler;
import cn.jens.mybatis.type.JdbcType;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** 默认按枚举常量名称读写枚举。 */
public class EnumTypeHandler<E extends Enum<E>> extends BaseTypeHandler<E> {

    private final Class<E> enumType;

    public EnumTypeHandler(Class<E> enumType) {
        this.enumType = enumType;
    }

    @Override
    protected void setNonNullParameter(
            PreparedStatement statement,
            int parameterIndex,
            E parameter,
            JdbcType jdbcType) throws SQLException {
        statement.setString(parameterIndex, parameter.name());
    }

    @Override
    protected E getNullableResult(ResultSet resultSet, String columnName)
            throws SQLException {
        return parse(resultSet.getString(columnName));
    }

    @Override
    protected E getNullableResult(ResultSet resultSet, int columnIndex)
            throws SQLException {
        return parse(resultSet.getString(columnIndex));
    }

    private E parse(String value) {
        return value == null ? null : Enum.valueOf(enumType, value);
    }
}
