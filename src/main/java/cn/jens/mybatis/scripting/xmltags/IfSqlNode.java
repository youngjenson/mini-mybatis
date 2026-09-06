package cn.jens.mybatis.scripting.xmltags;

/** test 表达式成立时输出子节点。 */
public class IfSqlNode implements SqlNode {

    private final SqlNode contents;

    private final String test;

    private final ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator();

    public IfSqlNode(SqlNode contents, String test) {
        this.contents = contents;
        this.test = test;
    }

    @Override
    public void apply(DynamicContext context) {
        if (expressionEvaluator.evaluateBoolean(test, context)) {
            contents.apply(context);
        }
    }
}
