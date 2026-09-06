package cn.jens.mybatis.scripting;

import cn.jens.mybatis.exception.PersistenceException;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;

/** 读取 Map、JavaBean、record 和集合上的嵌套属性。 */
public final class PropertyAccessor {

    private PropertyAccessor() {
    }

    public static Object getValue(
            Object parameterObject,
            Map<String, Object> bindings,
            String propertyPath) {
        if (propertyPath == null || propertyPath.isBlank()) {
            throw new PersistenceException("Property path must not be blank");
        }

        String[] properties = propertyPath.split("\\.");
        Object currentValue;
        int propertyIndex;
        if (bindings.containsKey(properties[0])) {
            currentValue = bindings.get(properties[0]);
            propertyIndex = 1;
        } else {
            if (parameterObject == null) {
                throw new PersistenceException(
                        "Cannot resolve SQL parameter '" + propertyPath + "' from null"
                );
            }
            if (properties.length == 1 && isSimpleType(parameterObject.getClass())) {
                return parameterObject;
            }
            currentValue = parameterObject;
            propertyIndex = 0;
        }

        for (int index = propertyIndex; index < properties.length; index++) {
            currentValue = readProperty(currentValue, properties[index], propertyPath);
        }
        return currentValue;
    }

    private static Object readProperty(Object target, String property, String propertyPath) {
        if (target == null) {
            return null;
        }
        if (target instanceof Map<?, ?> map) {
            if (map.containsKey(property)) {
                return map.get(property);
            }
            return readContainerProperty(target, property, propertyPath);
        }

        Object containerValue = readContainerProperty(target, property, null);
        if (containerValue != null) {
            return containerValue;
        }

        Method getter = findGetter(target.getClass(), property);
        if (getter != null) {
            return invokeGetter(target, getter, propertyPath);
        }
        return readField(target, property, propertyPath);
    }

    private static Object readContainerProperty(
            Object target,
            String property,
            String propertyPath) {
        if ("size".equals(property)) {
            if (target instanceof Collection<?> collection) {
                return collection.size();
            }
            if (target instanceof Map<?, ?> map) {
                return map.size();
            }
        }
        if ("length".equals(property)) {
            if (target.getClass().isArray()) {
                return Array.getLength(target);
            }
            if (target instanceof CharSequence sequence) {
                return sequence.length();
            }
        }
        if ("isEmpty".equals(property)) {
            if (target instanceof Collection<?> collection) {
                return collection.isEmpty();
            }
            if (target instanceof Map<?, ?> map) {
                return map.isEmpty();
            }
            if (target instanceof CharSequence sequence) {
                return sequence.isEmpty();
            }
        }
        if (propertyPath == null) {
            return null;
        }
        throw new PersistenceException("SQL parameter not found: " + propertyPath);
    }

    private static Method findGetter(Class<?> type, String property) {
        String suffix = Character.toUpperCase(property.charAt(0)) + property.substring(1);
        for (String methodName : new String[]{"get" + suffix, "is" + suffix, property}) {
            try {
                return type.getMethod(methodName);
            } catch (NoSuchMethodException ignored) {
                // 尝试下一种 JavaBean 或 record 访问方式。
            }
        }
        return null;
    }

    private static Object invokeGetter(Object target, Method getter, String propertyPath) {
        try {
            return getter.invoke(target);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new PersistenceException("Cannot read SQL parameter: " + propertyPath, e);
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
