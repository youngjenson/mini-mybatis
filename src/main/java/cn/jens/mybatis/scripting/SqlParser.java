package cn.jens.mybatis.scripting;

import java.util.ArrayList;
import java.util.Collections;
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
        return parse(sql, parameterObject, Collections.emptyMap());
    }

    public static BoundSql parse(
            String sql,
            Object parameterObject,
            Map<String, Object> additionalParameters) {
        Matcher matcher = PARAM_PATTERN.matcher(sql);
        StringBuilder jdbcSql = new StringBuilder();
        var parameterMappings = new ArrayList<ParameterMapping>();

        while (matcher.find()) {
            String property = matcher.group(1);
            parameterMappings.add(
                    new ParameterMapping(
                            property,
                            PropertyAccessor.getValue(
                                    parameterObject,
                                    additionalParameters,
                                    property
                            )
                    )
            );
            matcher.appendReplacement(jdbcSql, "?");
        }
        matcher.appendTail(jdbcSql);
        return new BoundSql(jdbcSql.toString(), parameterMappings);
    }
}
