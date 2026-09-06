package cn.jens.demo.typehandler;

import cn.jens.demo.type.EmailAddress;
import cn.jens.mybatis.type.BaseTypeHandler;
import cn.jens.mybatis.type.JdbcType;
import cn.jens.mybatis.type.MappedJdbcTypes;
import cn.jens.mybatis.type.MappedTypes;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;

/** EmailAddress 与 VARCHAR 之间的示例转换器。 */
@MappedTypes(EmailAddress.class)
@MappedJdbcTypes(value = JdbcType.VARCHAR, includeNullJdbcType = true)
public class EmailAddressTypeHandler extends BaseTypeHandler<EmailAddress> {

    @Override
    protected void setNonNullParameter(
            PreparedStatement statement,
            int parameterIndex,
            EmailAddress parameter,
            JdbcType jdbcType) throws SQLException {
        statement.setString(
                parameterIndex,
                parameter.value().strip().toLowerCase(Locale.ROOT)
        );
    }

    @Override
    protected EmailAddress getNullableResult(ResultSet resultSet, String columnName)
            throws SQLException {
        return toEmailAddress(resultSet.getString(columnName));
    }

    @Override
    protected EmailAddress getNullableResult(ResultSet resultSet, int columnIndex)
            throws SQLException {
        return toEmailAddress(resultSet.getString(columnIndex));
    }

    private EmailAddress toEmailAddress(String value) {
        return value == null ? null : new EmailAddress(value);
    }
}
