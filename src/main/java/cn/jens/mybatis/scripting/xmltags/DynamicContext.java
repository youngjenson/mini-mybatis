package cn.jens.mybatis.scripting.xmltags;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 一次动态 SQL 渲染过程中的参数绑定和 SQL 缓冲区。 */
public class DynamicContext {

    private final Object parameterObject;

    private final Map<String, Object> bindings;

    private final UniqueNumber uniqueNumber;

    private final StringBuilder sqlBuilder = new StringBuilder();

    public DynamicContext(Object parameterObject) {
        this(parameterObject, new HashMap<>(), new UniqueNumber());
        initializeBindings(parameterObject);
    }

    private DynamicContext(
            Object parameterObject,
            Map<String, Object> bindings,
            UniqueNumber uniqueNumber) {
        this.parameterObject = parameterObject;
        this.bindings = bindings;
        this.uniqueNumber = uniqueNumber;
    }

    public Object getParameterObject() {
        return parameterObject;
    }

    public Map<String, Object> getBindings() {
        return bindings;
    }

    public void bind(String name, Object value) {
        bindings.put(name, value);
    }

    public boolean hasBinding(String name) {
        return bindings.containsKey(name);
    }

    public Object removeBinding(String name) {
        return bindings.remove(name);
    }

    public int nextUniqueNumber() {
        return uniqueNumber.next();
    }

    public DynamicContext createChild() {
        return new DynamicContext(parameterObject, bindings, uniqueNumber);
    }

    public void appendSql(String sql) {
        if (sql == null || sql.isBlank()) {
            return;
        }
        String fragment = sql.strip();
        if (!sqlBuilder.isEmpty()
                && !Character.isWhitespace(sqlBuilder.charAt(sqlBuilder.length() - 1))
                && !Character.isWhitespace(fragment.charAt(0))) {
            sqlBuilder.append(' ');
        }
        sqlBuilder.append(fragment);
    }

    public String getSql() {
        return sqlBuilder.toString().strip();
    }

    private void initializeBindings(Object parameter) {
        bindings.put("_parameter", parameter);
        if (parameter instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() instanceof String key) {
                    bindings.put(key, entry.getValue());
                }
            }
        }
        if (parameter instanceof Collection<?> collection) {
            bindings.put("collection", collection);
            if (parameter instanceof List<?>) {
                bindings.put("list", parameter);
            }
        }
        if (parameter != null && parameter.getClass().isArray()) {
            bindings.put("array", parameter);
            bindings.put("length", Array.getLength(parameter));
        }
    }

    private static class UniqueNumber {

        private int value;

        private int next() {
            return value++;
        }
    }
}
