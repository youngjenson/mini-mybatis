package cn.jens.mybatis.executor.resultset;

import cn.jens.mybatis.exception.PersistenceException;
import cn.jens.mybatis.mapping.ResultMap;
import cn.jens.mybatis.mapping.ResultMapping;
import cn.jens.mybatis.type.JdbcType;
import cn.jens.mybatis.type.TypeHandler;
import cn.jens.mybatis.type.TypeHandlerRegistry;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 默认结果集映射实现。 */
public class DefaultResultSetHandler implements ResultSetHandler {

    private final TypeHandlerRegistry typeHandlerRegistry;

    public DefaultResultSetHandler() {
        this(new TypeHandlerRegistry());
    }

    public DefaultResultSetHandler(TypeHandlerRegistry typeHandlerRegistry) {
        this.typeHandlerRegistry = typeHandlerRegistry;
    }

    @Override
    public <T> List<T> handle(
            ResultSet resultSet,
            Class<?> rawResultType,
            ResultMap resultMap) throws SQLException {
        @SuppressWarnings("unchecked")
        Class<T> resultType = (Class<T>) rawResultType;
        if (typeHandlerRegistry.hasTypeHandler(resultType)) {
            return mapSimpleValues(resultSet, resultType);
        }
        return mapBeans(resultSet, resultType, resultMap);
    }

    private <T> List<T> mapSimpleValues(ResultSet resultSet, Class<T> resultType)
            throws SQLException {
        JdbcType jdbcType = JdbcType.fromCode(resultSet.getMetaData().getColumnType(1));
        TypeHandler<Object> typeHandler = typeHandlerRegistry.getTypeHandler(
                resultType,
                jdbcType
        );
        List<T> results = new ArrayList<>();
        while (resultSet.next()) {
            results.add(castResult(typeHandler.getResult(resultSet, 1)));
        }
        return results;
    }

    private <T> List<T> mapBeans(
            ResultSet resultSet,
            Class<T> resultType,
            ResultMap resultMap) throws SQLException {
        Constructor<T> constructor = getConstructor(resultType);
        Map<String, FieldMapping> fields = resultMap == null
                ? getAutoMappingFields(resultType)
                : getExplicitMappingFields(resultType, resultMap);
        List<ColumnMapping> columns = resolveColumns(resultSet.getMetaData(), fields);
        List<T> results = new ArrayList<>();

        while (resultSet.next()) {
            T bean = newInstance(constructor);
            for (ColumnMapping column : columns) {
                Object value = column.typeHandler().getResult(
                        resultSet,
                        column.columnIndex()
                );
                setField(bean, column.field(), value);
            }
            results.add(bean);
        }
        return results;
    }

    private List<ColumnMapping> resolveColumns(
            ResultSetMetaData metadata,
            Map<String, FieldMapping> fields) throws SQLException {
        List<ColumnMapping> columns = new ArrayList<>();
        for (int columnIndex = 1; columnIndex <= metadata.getColumnCount(); columnIndex++) {
            String columnLabel = metadata.getColumnLabel(columnIndex);
            FieldMapping fieldMapping = fields.get(normalizeName(columnLabel));
            if (fieldMapping == null) {
                continue;
            }
            columns.add(new ColumnMapping(
                    columnIndex,
                    fieldMapping.field(),
                    resolveTypeHandler(metadata, columnIndex, fieldMapping)
            ));
        }
        return columns;
    }

    private TypeHandler<Object> resolveTypeHandler(
            ResultSetMetaData metadata,
            int columnIndex,
            FieldMapping fieldMapping) throws SQLException {
        ResultMapping mapping = fieldMapping.resultMapping();
        if (mapping != null && mapping.typeHandler() != null) {
            return mapping.typeHandler();
        }
        Class<?> javaType = mapping != null && mapping.javaType() != null
                ? mapping.javaType()
                : fieldMapping.field().getType();
        JdbcType jdbcType = mapping != null && mapping.jdbcType() != null
                ? mapping.jdbcType()
                : JdbcType.fromCode(metadata.getColumnType(columnIndex));
        return typeHandlerRegistry.getTypeHandler(javaType, jdbcType);
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

    private Map<String, FieldMapping> getAutoMappingFields(Class<?> resultType) {
        Map<String, FieldMapping> fields = new HashMap<>();
        Class<?> currentType = resultType;
        while (currentType != null && currentType != Object.class) {
            for (Field field : currentType.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())
                        || Modifier.isFinal(field.getModifiers())) {
                    continue;
                }
                field.setAccessible(true);
                fields.putIfAbsent(
                        normalizeName(field.getName()),
                        new FieldMapping(field, null)
                );
            }
            currentType = currentType.getSuperclass();
        }
        return fields;
    }

    private Map<String, FieldMapping> getExplicitMappingFields(
            Class<?> resultType,
            ResultMap resultMap) {
        Map<String, FieldMapping> fields = new HashMap<>();
        for (ResultMapping mapping : resultMap.resultMappings()) {
            Field field = findField(resultType, mapping.property());
            field.setAccessible(true);
            fields.put(
                    normalizeName(mapping.column()),
                    new FieldMapping(field, mapping)
            );
        }
        return fields;
    }

    private Field findField(Class<?> resultType, String property) {
        Class<?> currentType = resultType;
        while (currentType != null && currentType != Object.class) {
            try {
                Field field = currentType.getDeclaredField(property);
                if (Modifier.isStatic(field.getModifiers())
                        || Modifier.isFinal(field.getModifiers())) {
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
            field.set(bean, value);
        } catch (IllegalAccessException | IllegalArgumentException e) {
            throw new PersistenceException("Cannot set result field: " + field.getName(), e);
        }
    }

    private String normalizeName(String name) {
        return name.replace("_", "").toLowerCase(Locale.ROOT);
    }

    @SuppressWarnings("unchecked")
    private <T> T castResult(Object value) {
        return (T) value;
    }

    private record FieldMapping(Field field, ResultMapping resultMapping) {
    }

    private record ColumnMapping(
            int columnIndex,
            Field field,
            TypeHandler<Object> typeHandler) {
    }
}
