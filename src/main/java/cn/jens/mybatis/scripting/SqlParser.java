package cn.jens.mybatis.scripting;

import cn.jens.mybatis.exception.PersistenceException;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 把 #{} 占位符转换成 JDBC 的 ? 占位符。
 *
 * @author YumJens
 */
public final class SqlParser {

    private static final Pattern PARAM_PATTERN = Pattern.compile("#\\{\\s*([^},\\s]+)[^}]*}");

    private SqlParser() {
    }

    public static BoundSql parse(String sql, Object parameterObject) {
        Matcher matcher = PARAM_PATTERN.matcher(sql);
        StringBuilder jdbcSql = new StringBuilder();
        var parameterMappings = new ArrayList<ParameterMapping>();

        while (matcher.find()) {
            String property = matcher.group(1);
            parameterMappings.add(
                    new ParameterMapping(property, resolveValue(parameterObject, property))
            );
            matcher.appendReplacement(jdbcSql, "?");
        }
        matcher.appendTail(jdbcSql);
        return new BoundSql(jdbcSql.toString(), parameterMappings);
    }

    private static Object resolveValue(Object parameterObject, String propertyPath) {
        if (parameterObject == null) {
            throw new PersistenceException(
                    "Cannot resolve SQL parameter '" + propertyPath + "' from null"
            );
        }
        if (isSimpleType(parameterObject.getClass())) {
            return parameterObject;
        }

        Object currentValue = parameterObject;
        for (String property : propertyPath.split("\\.")) {
            currentValue = readProperty(currentValue, property, propertyPath);
        }
        return currentValue;
    }

    private static Object readProperty(Object target, String property, String propertyPath) {
        if (target == null) {
            return null;
        }
        if (target instanceof Map<?, ?> map) {
            if (!map.containsKey(property)) {
                throw new PersistenceException("SQL parameter not found: " + propertyPath);
            }
            return map.get(property);
        }

        String getterName = "get" + Character.toUpperCase(property.charAt(0)) + property.substring(1);
        try {
            Method getter = target.getClass().getMethod(getterName);
            return getter.invoke(target);
        } catch (ReflectiveOperationException ignored) {
            return readField(target, property, propertyPath);
        }
    }

    private static Object readField(Object target, String property, String propertyPath) {
        Class<?> type = target.getClass();
        while (type != null && type != Object.class) {
            try {
                Field field = type.getDeclaredField(property);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new PersistenceException("Cannot read SQL parameter: " + propertyPath, e);
            }
        }
        throw new PersistenceException("SQL parameter not found: " + propertyPath);
    }

    private static boolean isSimpleType(Class<?> type) {
        return type.isPrimitive()
                || Number.class.isAssignableFrom(type)
                || CharSequence.class.isAssignableFrom(type)
                || Boolean.class.equals(type)
                || Character.class.equals(type)
                || Enum.class.isAssignableFrom(type);
    }
}
