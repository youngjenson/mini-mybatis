package cn.jens.mybatis.executor.parameter;

import cn.jens.mybatis.scripting.BoundSql;
import cn.jens.mybatis.scripting.ParameterMapping;
import cn.jens.mybatis.type.JdbcType;
import cn.jens.mybatis.type.TypeHandler;
import cn.jens.mybatis.type.TypeHandlerRegistry;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

/** 按 BoundSql 中的占位符顺序绑定参数。 */
public class DefaultParameterHandler implements ParameterHandler {

    private final BoundSql boundSql;

    private final TypeHandlerRegistry typeHandlerRegistry;

    private final JdbcType jdbcTypeForNull;

    public DefaultParameterHandler(BoundSql boundSql) {
        this(boundSql, new TypeHandlerRegistry(), JdbcType.OTHER);
    }

    public DefaultParameterHandler(
            BoundSql boundSql,
            TypeHandlerRegistry typeHandlerRegistry,
            JdbcType jdbcTypeForNull) {
        this.boundSql = boundSql;
        this.typeHandlerRegistry = typeHandlerRegistry;
        this.jdbcTypeForNull = jdbcTypeForNull;
    }

    @Override
    public void setParameters(PreparedStatement statement) throws SQLException {
        List<ParameterMapping> mappings = boundSql.getParameterMappings();
        for (int index = 0; index < mappings.size(); index++) {
            ParameterMapping mapping = mappings.get(index);
            TypeHandler<Object> typeHandler = mapping.typeHandler() == null
                    ? typeHandlerRegistry.getTypeHandler(
                            mapping.javaType(),
                            mapping.jdbcType()
                    )
                    : mapping.typeHandler();
            JdbcType jdbcType = mapping.value() == null && mapping.jdbcType() == null
                    ? jdbcTypeForNull
                    : mapping.jdbcType();
            typeHandler.setParameter(
                    statement,
                    index + 1,
                    mapping.value(),
                    jdbcType
            );
        }
    }
}
