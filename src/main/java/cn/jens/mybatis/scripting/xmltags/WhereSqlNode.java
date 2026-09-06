package cn.jens.mybatis.scripting.xmltags;

import java.util.regex.Pattern;

/** 有条件内容时添加 WHERE，并移除开头的 AND 或 OR。 */
public class WhereSqlNode implements SqlNode {

    private static final Pattern LEADING_CONNECTOR = Pattern.compile(
            "^(?i:AND|OR)\\b\\s*"
    );

    private final SqlNode contents;

    public WhereSqlNode(SqlNode contents) {
        this.contents = contents;
    }

    @Override
    public void apply(DynamicContext context) {
        DynamicContext childContext = context.createChild();
        contents.apply(childContext);
        String sql = childContext.getSql();
        if (sql.isBlank()) {
            return;
        }
        String condition = LEADING_CONNECTOR.matcher(sql).replaceFirst("");
        context.appendSql("WHERE " + condition);
    }
}
