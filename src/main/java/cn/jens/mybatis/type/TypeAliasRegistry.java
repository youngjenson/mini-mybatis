package cn.jens.mybatis.type;

import cn.jens.mybatis.exception.PersistenceException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** 保存 XML 中常用 Java 类型别名。 */
public class TypeAliasRegistry {

    private final Map<String, Class<?>> typeAliases = new HashMap<>();

    public TypeAliasRegistry() {
        registerAlias("byte", Byte.class);
        registerAlias("short", Short.class);
        registerAlias("int", Integer.class);
        registerAlias("integer", Integer.class);
        registerAlias("long", Long.class);
        registerAlias("float", Float.class);
        registerAlias("double", Double.class);
        registerAlias("boolean", Boolean.class);
        registerAlias("char", Character.class);
        registerAlias("character", Character.class);
        registerAlias("string", String.class);
        registerAlias("decimal", BigDecimal.class);
        registerAlias("bigdecimal", BigDecimal.class);
        registerAlias("date", LocalDate.class);
        registerAlias("localdate", LocalDate.class);
        registerAlias("localdatetime", LocalDateTime.class);
        registerAlias("object", Object.class);
    }

    public void registerAlias(String alias, Class<?> type) {
        if (alias == null || alias.isBlank()) {
            throw new PersistenceException("Type alias must not be blank");
        }
        Class<?> previous = typeAliases.putIfAbsent(
                alias.toLowerCase(Locale.ROOT),
                type
        );
        if (previous != null && !previous.equals(type)) {
            throw new PersistenceException("Duplicate type alias: " + alias);
        }
    }

    public Class<?> resolveAlias(String alias) {
        if (alias == null || alias.isBlank()) {
            throw new PersistenceException("Type name must not be blank");
        }
        Class<?> type = typeAliases.get(alias.toLowerCase(Locale.ROOT));
        if (type != null) {
            return type;
        }
        try {
            return Class.forName(alias);
        } catch (ClassNotFoundException e) {
            throw new PersistenceException("Type class not found: " + alias, e);
        }
    }
}
