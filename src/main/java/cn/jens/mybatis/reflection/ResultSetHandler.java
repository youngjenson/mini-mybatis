package cn.jens.mybatis.reflection;

import cn.jens.mybatis.exception.PersistenceException;
import cn.jens.mybatis.mapping.ResultMap;
import cn.jens.mybatis.mapping.ResultMapping;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 把 ResultSet 的每一行映射为 Java 对象。
 *
 * @author YumJens
 */
public class ResultSetHandler {

    public <T> List<T> handle(
            ResultSet resultSet,
            Class<?> rawResultType,
            ResultMap resultMap) throws SQLException {
        @SuppressWarnings("unchecked")
        Class<T> resultType = (Class<T>) rawResultType;
        if (isSimpleType(resultType)) {
            return mapSimpleValues(resultSet, resultType);
        }
        return mapBeans(resultSet, resultType, resultMap);
    }

    private <T> List<T> mapSimpleValues(ResultSet resultSet, Class<T> resultType)
            throws SQLException {
        List<T> results = new ArrayList<>();
        while (resultSet.next()) {
            results.add(convertValue(resultSet.getObject(1), resultType));
        }
        return results;
    }

    private <T> List<T> mapBeans(
            ResultSet resultSet,
            Class<T> resultType,
            ResultMap resultMap) throws SQLException {
        Constructor<T> constructor = getConstructor(resultType);
        Map<String, Field> fields = resultMap == null
                ? getAutoMappingFields(resultType)
                : getExplicitMappingFields(resultType, resultMap);
        ResultSetMetaData metadata = resultSet.getMetaData();
        List<T> results = new ArrayList<>();

        while (resultSet.next()) {
            T bean = newInstance(constructor);
            for (int column = 1; column <= metadata.getColumnCount(); column++) {
                String columnLabel = metadata.getColumnLabel(column);
                Field field = fields.get(normalizeName(columnLabel));
                if (field == null) {
                    continue;
                }
                setField(bean, field, resultSet.getObject(column));
            }
            results.add(bean);
        }
        return results;
    }

    private <T> Constructor<T> getConstructor(Class<T> resultType) {
        try {
            Constructor<T> constructor = resultType.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor;
        } catch (NoSuchMethodException e) {
            throw new PersistenceException(
                    "Result type requires a no-argument constructor: " + resultType.getName(),
                    e
            );
        }
    }

    private Map<String, Field> getAutoMappingFields(Class<?> resultType) {
        Map<String, Field> fields = new HashMap<>();
        Class<?> currentType = resultType;
        while (currentType != null && currentType != Object.class) {
            for (Field field : currentType.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())) {
                    continue;
                }
                field.setAccessible(true);
                fields.putIfAbsent(normalizeName(field.getName()), field);
            }
            currentType = currentType.getSuperclass();
        }
        return fields;
    }

    private Map<String, Field> getExplicitMappingFields(
            Class<?> resultType,
            ResultMap resultMap) {
        Map<String, Field> fields = new HashMap<>();
        for (ResultMapping mapping : resultMap.resultMappings()) {
            Field field = findField(resultType, mapping.property());
            field.setAccessible(true);
            fields.put(normalizeName(mapping.column()), field);
        }
        return fields;
    }

    private Field findField(Class<?> resultType, String property) {
        Class<?> currentType = resultType;
        while (currentType != null && currentType != Object.class) {
            try {
                Field field = currentType.getDeclaredField(property);
                if (Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())) {
                    throw new PersistenceException("Result property is not writable: " + property);
                }
                return field;
            } catch (NoSuchFieldException ignored) {
                currentType = currentType.getSuperclass();
            }
        }
        throw new PersistenceException(
                "Result property not found on " + resultType.getName() + ": " + property
        );
    }

    private <T> T newInstance(Constructor<T> constructor) {
        try {
            return constructor.newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new PersistenceException(
                    "Cannot create result type: " + constructor.getDeclaringClass().getName(),
                    e
            );
        }
    }

    private void setField(Object bean, Field field, Object value) {
        if (value == null && field.getType().isPrimitive()) {
            return;
        }
        try {
            field.set(bean, convertValue(value, field.getType()));
        } catch (IllegalAccessException e) {
            throw new PersistenceException("Cannot set result field: " + field.getName(), e);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T> T convertValue(Object value, Class<T> targetType) {
        if (value == null || targetType.isInstance(value)) {
            return (T) value;
        }
        if (value instanceof Number number) {
            Object converted = switch (targetType.getName()) {
                case "byte", "java.lang.Byte" -> number.byteValue();
                case "short", "java.lang.Short" -> number.shortValue();
                case "int", "java.lang.Integer" -> number.intValue();
                case "long", "java.lang.Long" -> number.longValue();
                case "float", "java.lang.Float" -> number.floatValue();
                case "double", "java.lang.Double" -> number.doubleValue();
                case "java.math.BigDecimal" -> new BigDecimal(number.toString());
                default -> value;
            };
            return (T) converted;
        }
        if (targetType.isEnum()) {
            return (T) Enum.valueOf((Class<? extends Enum>) targetType, value.toString());
        }
        if (String.class.equals(targetType)) {
            return (T) value.toString();
        }
        return (T) value;
    }

    private boolean isSimpleType(Class<?> type) {
        return type.isPrimitive()
                || Number.class.isAssignableFrom(type)
                || CharSequence.class.isAssignableFrom(type)
                || Boolean.class.equals(type)
                || Character.class.equals(type)
                || Enum.class.isAssignableFrom(type);
    }

    private String normalizeName(String name) {
        return name.replace("_", "").toLowerCase(Locale.ROOT);
    }
}
