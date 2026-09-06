package cn.jens.mybatis.type;

import cn.jens.mybatis.exception.PersistenceException;
import cn.jens.mybatis.type.handler.BooleanTypeHandler;
import cn.jens.mybatis.type.handler.CharacterTypeHandler;
import cn.jens.mybatis.type.handler.EnumTypeHandler;
import cn.jens.mybatis.type.handler.LocalDateTimeTypeHandler;
import cn.jens.mybatis.type.handler.LocalDateTypeHandler;
import cn.jens.mybatis.type.handler.NumberTypeHandler;
import cn.jens.mybatis.type.handler.ObjectTypeHandler;
import cn.jens.mybatis.type.handler.StringTypeHandler;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** 按 Java 类型和可选 JDBC 类型注册、选择 TypeHandler。 */
public class TypeHandlerRegistry {

    private final Map<Class<?>, Map<JdbcType, TypeHandler<?>>> typeHandlerMap =
            new HashMap<>();

    private final ConcurrentMap<Class<? extends TypeHandler<?>>, TypeHandler<?>> handlerInstances =
            new ConcurrentHashMap<>();

    private final ConcurrentMap<Class<?>, TypeHandler<?>> enumTypeHandlers =
            new ConcurrentHashMap<>();

    private final TypeHandler<Object> unknownTypeHandler = new ObjectTypeHandler();

    public TypeHandlerRegistry() {
        register(Object.class, unknownTypeHandler);
        register(String.class, new StringTypeHandler());
        registerNumber(Byte.class);
        registerNumber(Short.class);
        registerNumber(Integer.class);
        registerNumber(Long.class);
        registerNumber(Float.class);
        registerNumber(Double.class);
        registerNumber(BigDecimal.class);
        register(Boolean.class, new BooleanTypeHandler());
        register(Character.class, new CharacterTypeHandler());
        register(LocalDate.class, JdbcType.DATE, new LocalDateTypeHandler());
        register(LocalDate.class, new LocalDateTypeHandler());
        register(LocalDateTime.class, JdbcType.TIMESTAMP, new LocalDateTimeTypeHandler());
        register(LocalDateTime.class, new LocalDateTimeTypeHandler());
    }

    public void register(Class<?> javaType, TypeHandler<?> typeHandler) {
        register(javaType, null, typeHandler);
    }

    public void register(
            Class<?> javaType,
            JdbcType jdbcType,
            TypeHandler<?> typeHandler) {
        Class<?> normalizedType = wrapPrimitive(javaType);
        typeHandlerMap.computeIfAbsent(normalizedType, ignored -> new HashMap<>())
                .put(jdbcType, typeHandler);
        handlerInstances.putIfAbsent(handlerClass(typeHandler), typeHandler);
    }

    public void register(Class<? extends TypeHandler<?>> handlerType) {
        register(createTypeHandler(handlerType));
    }

    public void register(TypeHandler<?> typeHandler) {
        Class<?> handlerType = typeHandler.getClass();
        Set<Class<?>> javaTypes = resolveMappedJavaTypes(handlerType);
        MappedJdbcTypes mappedJdbcTypes = handlerType.getAnnotation(MappedJdbcTypes.class);
        for (Class<?> javaType : javaTypes) {
            if (mappedJdbcTypes == null) {
                register(javaType, typeHandler);
                continue;
            }
            for (JdbcType jdbcType : mappedJdbcTypes.value()) {
                register(javaType, jdbcType, typeHandler);
            }
            if (mappedJdbcTypes.includeNullJdbcType()) {
                register(javaType, typeHandler);
            }
        }
    }

    public boolean hasTypeHandler(Class<?> javaType) {
        if (javaType == null) {
            return false;
        }
        Class<?> normalizedType = wrapPrimitive(javaType);
        return normalizedType.isEnum() || typeHandlerMap.containsKey(normalizedType);
    }

    public TypeHandler<Object> getTypeHandler(Class<?> javaType, JdbcType jdbcType) {
        Class<?> normalizedType = wrapPrimitive(javaType == null ? Object.class : javaType);
        Map<JdbcType, TypeHandler<?>> jdbcHandlers = typeHandlerMap.get(normalizedType);
        TypeHandler<?> typeHandler = selectTypeHandler(jdbcHandlers, jdbcType);
        if (typeHandler == null && normalizedType.isEnum()) {
            typeHandler = enumTypeHandlers.computeIfAbsent(
                    normalizedType,
                    this::createEnumTypeHandler
            );
        }
        if (typeHandler == null) {
            typeHandler = unknownTypeHandler;
        }
        return cast(typeHandler);
    }

    public TypeHandler<Object> resolveTypeHandler(String handlerClassName) {
        try {
            Class<?> rawType = Class.forName(handlerClassName);
            if (!TypeHandler.class.isAssignableFrom(rawType)) {
                throw new PersistenceException(
                        "Type handler must implement TypeHandler: " + handlerClassName
                );
            }
            @SuppressWarnings("unchecked")
            Class<? extends TypeHandler<?>> handlerType =
                    (Class<? extends TypeHandler<?>>) rawType;
            return cast(createTypeHandler(handlerType));
        } catch (ClassNotFoundException e) {
            throw new PersistenceException("Type handler class not found: " + handlerClassName, e);
        }
    }

    private TypeHandler<?> selectTypeHandler(
            Map<JdbcType, TypeHandler<?>> jdbcHandlers,
            JdbcType jdbcType) {
        if (jdbcHandlers == null || jdbcHandlers.isEmpty()) {
            return null;
        }
        TypeHandler<?> typeHandler = jdbcHandlers.get(jdbcType);
        if (typeHandler == null) {
            typeHandler = jdbcHandlers.get(null);
        }
        if (typeHandler != null) {
            return typeHandler;
        }
        Set<TypeHandler<?>> distinctHandlers = new HashSet<>(jdbcHandlers.values());
        return distinctHandlers.size() == 1 ? distinctHandlers.iterator().next() : null;
    }

    private Set<Class<?>> resolveMappedJavaTypes(Class<?> handlerType) {
        MappedTypes mappedTypes = handlerType.getAnnotation(MappedTypes.class);
        if (mappedTypes != null && mappedTypes.value().length > 0) {
            return Set.of(mappedTypes.value());
        }
        Class<?> genericType = resolveGenericType(handlerType);
        if (genericType != null) {
            return Set.of(genericType);
        }
        throw new PersistenceException(
                "Cannot infer Java type for type handler: " + handlerType.getName()
        );
    }

    private Class<?> resolveGenericType(Class<?> handlerType) {
        Class<?> currentType = handlerType;
        while (currentType != null && currentType != Object.class) {
            Class<?> resolved = resolveTypeArgument(currentType.getGenericSuperclass());
            if (resolved != null) {
                return resolved;
            }
            for (Type genericInterface : currentType.getGenericInterfaces()) {
                resolved = resolveTypeArgument(genericInterface);
                if (resolved != null) {
                    return resolved;
                }
            }
            currentType = currentType.getSuperclass();
        }
        return null;
    }

    private Class<?> resolveTypeArgument(Type genericType) {
        if (!(genericType instanceof ParameterizedType parameterizedType)) {
            return null;
        }
        Type rawType = parameterizedType.getRawType();
        if (!(rawType instanceof Class<?> rawClass)
                || !TypeHandler.class.isAssignableFrom(rawClass)) {
            return null;
        }
        Type typeArgument = parameterizedType.getActualTypeArguments()[0];
        return typeArgument instanceof Class<?> typeClass ? typeClass : null;
    }

    private TypeHandler<?> createTypeHandler(
            Class<? extends TypeHandler<?>> handlerType) {
        return handlerInstances.computeIfAbsent(handlerType, this::newTypeHandler);
    }

    private TypeHandler<?> newTypeHandler(Class<? extends TypeHandler<?>> handlerType) {
        try {
            var constructor = handlerType.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new PersistenceException(
                    "Cannot create type handler: " + handlerType.getName(),
                    e
            );
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private TypeHandler<?> createEnumTypeHandler(Class<?> enumType) {
        return new EnumTypeHandler(enumType.asSubclass(Enum.class));
    }

    private <T extends Number> void registerNumber(Class<T> javaType) {
        register(javaType, new NumberTypeHandler<>(javaType));
    }

    @SuppressWarnings("unchecked")
    private Class<? extends TypeHandler<?>> handlerClass(TypeHandler<?> typeHandler) {
        return (Class<? extends TypeHandler<?>>) typeHandler.getClass();
    }

    @SuppressWarnings("unchecked")
    private TypeHandler<Object> cast(TypeHandler<?> typeHandler) {
        return (TypeHandler<Object>) typeHandler;
    }

    private Class<?> wrapPrimitive(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        return switch (type.getName()) {
            case "byte" -> Byte.class;
            case "short" -> Short.class;
            case "int" -> Integer.class;
            case "long" -> Long.class;
            case "float" -> Float.class;
            case "double" -> Double.class;
            case "boolean" -> Boolean.class;
            case "char" -> Character.class;
            default -> type;
        };
    }
}
