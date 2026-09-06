package cn.jens.mybatis.scripting.xmltags;

/** 始终输出的 SQL 文本。 */
public class StaticTextSqlNode implements SqlNode {

    private final String text;

    public StaticTextSqlNode(String text) {
        this.text = text;
    }

    @Override
    public void apply(DynamicContext context) {
        context.appendSql(text);
    }
}
