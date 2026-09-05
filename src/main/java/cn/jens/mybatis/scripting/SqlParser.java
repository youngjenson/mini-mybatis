package cn.jens.scripting;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SQL解析器
 *
 * @author YumJens
 * @date 2026-09-05 00:05
 */
public class SqlParser {

    /**
     * 参数占位符正则表达式
     */
    private static final Pattern PARAM_PATTERN =
            Pattern.compile("#\\{([^}]+)}");

    /**
     * 解析SQL语句，将参数占位符替换为实际参数
     *
     * @param sql             SQL语句
     * @param parameterObject 参数对象
     * @return 解析后的SQL语句和参数列表
     */
    public static BoundSql parse(
            String sql,
            Object parameterObject) {

        Matcher matcher =
                PARAM_PATTERN.matcher(sql);

        StringBuffer newSql =
                new StringBuffer();

        List<Object> parameters =
                new ArrayList<>();

        while (matcher.find()) {

            String propertyName =
                    matcher.group(1);

            Object value =
                    getValue(
                            parameterObject,
                            propertyName
                    );

            parameters.add(value);

            matcher.appendReplacement(
                    newSql,
                    "?"
            );
        }

        matcher.appendTail(newSql);

        return new BoundSql(
                newSql.toString(),
                parameters
        );
    }

    /**
     * 根据属性名获取属性值
     *
     * @param parameterObject 参数对象
     * @param propertyName  属性名
     * @return 属性值
     */
    private static Object getValue(
            Object parameterObject,
            String propertyName) {

        if (parameterObject == null) {
            return null;
        }

        // 基础类型直接返回
        if (parameterObject instanceof Number
                || parameterObject instanceof String
                || parameterObject instanceof Boolean) {

            return parameterObject;
        }

        try {

            Field field =
                    parameterObject
                            .getClass()
                            .getDeclaredField(propertyName);

            field.setAccessible(true);

            return field.get(parameterObject);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}