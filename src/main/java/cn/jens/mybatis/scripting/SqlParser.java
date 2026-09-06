package cn.jens.mybatis.scripting;

import cn.jens.mybatis.exception.PersistenceException;
import cn.jens.mybatis.type.JdbcType;
import cn.jens.mybatis.type.TypeAliasRegistry;
import cn.jens.mybatis.type.TypeHandler;
import cn.jens.mybatis.type.TypeHandlerRegistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 把 #{} 占位符转换成 JDBC 的 ? 占位符。
 *
 * @author YumJens
 */
public final class SqlParser {

    private static final Pattern PARAM_PATTERN = Pattern.compile("#\\{\\s*([^{}]+?)\\s*}");

    private static final Pattern PROPERTY_PATTERN = Pattern.compile(
            "[A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)*"
    );

    private static final TypeHandlerRegistry DEFAULT_TYPE_HANDLER_REGISTRY =
            new TypeHandlerRegistry();

    private static final TypeAliasRegistry DEFAULT_TYPE_ALIAS_REGISTRY =
            new TypeAliasRegistry();

    private SqlParser() {
    }

    public static BoundSql parse(String sql, Object parameterObject) {
        return parse(sql, parameterObject, Collections.emptyMap());
    }

    public static BoundSql parse(
            String sql,
            Object parameterObject,
            Map<String, Object> additionalParameters) {
        return parse(
                sql,
                parameterObject,
                additionalParameters,
                DEFAULT_TYPE_HANDLER_REGISTRY,
                DEFAULT_TYPE_ALIAS_REGISTRY
        );
    }

    public static BoundSql parse(
            String sql,
            Object parameterObject,
            Map<String, Object> additionalParameters,
            TypeHandlerRegistry typeHandlerRegistry,
            TypeAliasRegistry typeAliasRegistry) {
        Matcher matcher = PARAM_PATTERN.matcher(sql);
        StringBuilder jdbcSql = new StringBuilder();
        var parameterMappings = new ArrayList<ParameterMapping>();

        while (matcher.find()) {
            ParameterExpression expression = parseExpression(matcher.group(1));
            Object value = PropertyAccessor.getValue(
                    parameterObject,
                    additionalParameters,
                    expression.property()
            );
            Class<?> javaType = expression.javaType() == null
                    ? inferJavaType(value)
                    : typeAliasRegistry.resolveAlias(expression.javaType());
            JdbcType jdbcType = expression.jdbcType() == null
                    ? null
                    : JdbcType.fromName(expression.jdbcType());
            TypeHandler<Object> typeHandler = expression.typeHandler() == null
                    ? null
                    : typeHandlerRegistry.resolveTypeHandler(expression.typeHandler());
            parameterMappings.add(new ParameterMapping(
                    expression.property(),
                    value,
                    javaType,
                    jdbcType,
                    typeHandler
            ));
            matcher.appendReplacement(jdbcSql, "?");
        }
        matcher.appendTail(jdbcSql);
        return new BoundSql(jdbcSql.toString(), parameterMappings);
    }

    private static ParameterExpression parseExpression(String content) {
        String[] parts = content.split(",", -1);
        String property = parts[0].strip();
        if (!PROPERTY_PATTERN.matcher(property).matches()) {
            throw new PersistenceException("Invalid SQL parameter property: " + property);
        }

        Map<String, String> options = new LinkedHashMap<>();
        for (int index = 1; index < parts.length; index++) {
            String option = parts[index].strip();
            int separatorIndex = option.indexOf('=');
            if (separatorIndex <= 0 || separatorIndex == option.length() - 1) {
                throw new PersistenceException("Invalid SQL parameter option: " + option);
            }
            String name = option.substring(0, separatorIndex).strip();
            String value = option.substring(separatorIndex + 1).strip();
            if (!isSupportedOption(name)) {
                throw new PersistenceException("Unsupported SQL parameter option: " + name);
            }
            if (options.putIfAbsent(name, value) != null) {
                throw new PersistenceException("Duplicate SQL parameter option: " + name);
            }
        }
        return new ParameterExpression(
                property,
                options.get("javaType"),
                options.get("jdbcType"),
                options.get("typeHandler")
        );
    }

    private static boolean isSupportedOption(String name) {
        return "javaType".equals(name)
                || "jdbcType".equals(name)
                || "typeHandler".equals(name);
    }

    private static Class<?> inferJavaType(Object value) {
        return value == null ? Object.class : value.getClass();
    }

    private record ParameterExpression(
            String property,
            String javaType,
            String jdbcType,
            String typeHandler) {
    }
}
