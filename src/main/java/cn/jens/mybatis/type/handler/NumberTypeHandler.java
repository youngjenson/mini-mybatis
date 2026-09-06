package cn.jens.mybatis.type.handler;

import cn.jens.mybatis.exception.PersistenceException;
import cn.jens.mybatis.type.BaseTypeHandler;
import cn.jens.mybatis.type.JdbcType;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Java 数值包装类型与 JDBC 数值列之间的转换。 */
public class NumberTypeHandler<T extends Number> extends BaseTypeHandler<T> {

    private final Class<T> javaType;

    public NumberTypeHandler(Class<T> javaType) {
        this.javaType = javaType;
    }

    @Override
    protected void setNonNullParameter(
            PreparedStatement statement,
            int parameterIndex,
            T parameter,
            JdbcType jdbcType) throws SQLException {
        switch (javaType.getName()) {
            case "java.lang.Byte" -> statement.setByte(parameterIndex, parameter.byteValue());
            case "java.lang.Short" -> statement.setShort(parameterIndex, parameter.shortValue());
            case "java.lang.Integer" -> statement.setInt(parameterIndex, parameter.intValue());
            case "java.lang.Long" -> statement.setLong(parameterIndex, parameter.longValue());
            case "java.lang.Float" -> statement.setFloat(parameterIndex, parameter.floatValue());
            case "java.lang.Double" -> statement.setDouble(parameterIndex, parameter.doubleValue());
            case "java.math.BigDecimal" -> statement.setBigDecimal(
                    parameterIndex,
                    (BigDecimal) parameter
            );
            default -> throw unsupportedType();
        }
    }

    @Override
    protected T getNullableResult(ResultSet resultSet, String columnName)
            throws SQLException {
        Object value = switch (javaType.getName()) {
            case "java.lang.Byte" -> resultSet.getByte(columnName);
            case "java.lang.Short" -> resultSet.getShort(columnName);
            case "java.lang.Integer" -> resultSet.getInt(columnName);
            case "java.lang.Long" -> resultSet.getLong(columnName);
            case "java.lang.Float" -> resultSet.getFloat(columnName);
            case "java.lang.Double" -> resultSet.getDouble(columnName);
            case "java.math.BigDecimal" -> resultSet.getBigDecimal(columnName);
            default -> throw unsupportedType();
        };
        return castNullable(resultSet, value);
    }

    @Override
    protected T getNullableResult(ResultSet resultSet, int columnIndex)
            throws SQLException {
        Object value = switch (javaType.getName()) {
            case "java.lang.Byte" -> resultSet.getByte(columnIndex);
            case "java.lang.Short" -> resultSet.getShort(columnIndex);
            case "java.lang.Integer" -> resultSet.getInt(columnIndex);
            case "java.lang.Long" -> resultSet.getLong(columnIndex);
            case "java.lang.Float" -> resultSet.getFloat(columnIndex);
            case "java.lang.Double" -> resultSet.getDouble(columnIndex);
            case "java.math.BigDecimal" -> resultSet.getBigDecimal(columnIndex);
            default -> throw unsupportedType();
        };
        return castNullable(resultSet, value);
    }

    private T castNullable(ResultSet resultSet, Object value) throws SQLException {
        if (resultSet.wasNull()) {
            return null;
        }
        return javaType.cast(value);
    }

    private PersistenceException unsupportedType() {
        return new PersistenceException("Unsupported numeric Java type: " + javaType.getName());
    }
}
