package cn.jens.mybatis.type;

import cn.jens.mybatis.exception.PersistenceException;

import java.sql.Types;
import java.util.Arrays;
import java.util.Locale;

/** JDBC 类型及其 {@link Types} 编码。 */
public enum JdbcType {

    ARRAY(Types.ARRAY),
    BIGINT(Types.BIGINT),
    BINARY(Types.BINARY),
    BIT(Types.BIT),
    BLOB(Types.BLOB),
    BOOLEAN(Types.BOOLEAN),
    CHAR(Types.CHAR),
    CLOB(Types.CLOB),
    DATE(Types.DATE),
    DECIMAL(Types.DECIMAL),
    DOUBLE(Types.DOUBLE),
    FLOAT(Types.FLOAT),
    INTEGER(Types.INTEGER),
    LONGVARBINARY(Types.LONGVARBINARY),
    LONGVARCHAR(Types.LONGVARCHAR),
    NULL(Types.NULL),
    NUMERIC(Types.NUMERIC),
    OTHER(Types.OTHER),
    REAL(Types.REAL),
    SMALLINT(Types.SMALLINT),
    TIME(Types.TIME),
    TIMESTAMP(Types.TIMESTAMP),
    TINYINT(Types.TINYINT),
    VARBINARY(Types.VARBINARY),
    VARCHAR(Types.VARCHAR);

    private final int code;

    JdbcType(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static JdbcType fromCode(int code) {
        return Arrays.stream(values())
                .filter(jdbcType -> jdbcType.code == code)
                .findFirst()
                .orElse(OTHER);
    }

    public static JdbcType fromName(String name) {
        if (name == null || name.isBlank()) {
            throw new PersistenceException("JDBC type name must not be blank");
        }
        try {
            return valueOf(name.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new PersistenceException("Unknown JDBC type: " + name, e);
        }
    }
}
