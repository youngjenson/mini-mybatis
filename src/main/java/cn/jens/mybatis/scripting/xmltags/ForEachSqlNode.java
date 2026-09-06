package cn.jens.mybatis.scripting.xmltags;

import cn.jens.mybatis.exception.PersistenceException;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 遍历集合并安全展开重复 SQL 片段。 */
public class ForEachSqlNode implements SqlNode {

    private final SqlNode contents;

    private final String collectionExpression;

    private final String item;

    private final String index;

    private final String open;

    private final String close;

    private final String separator;

    private final boolean nullable;

    private final ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator();

    public ForEachSqlNode(
            SqlNode contents,
            String collectionExpression,
            String item,
            String index,
            String open,
            String close,
            String separator,
            boolean nullable) {
        this.contents = contents;
        this.collectionExpression = collectionExpression;
        this.item = item;
        this.index = index;
        this.open = open;
        this.close = close;
        this.separator = separator;
        this.nullable = nullable;
    }

    @Override
    public void apply(DynamicContext context) {
        Object collection = expressionEvaluator.evaluateValue(
                collectionExpression,
                context
        );
        if (collection == null) {
            if (nullable) {
                return;
            }
            throw new PersistenceException(
                    "foreach collection evaluated to null: " + collectionExpression
            );
        }

        List<Iteration> iterations = toIterations(collection);
        StringJoiner fragments = new StringJoiner(separator == null ? "" : separator);
        for (Iteration iteration : iterations) {
            String fragment = renderIteration(context, iteration);
            if (!fragment.isBlank()) {
                fragments.add(fragment);
            }
        }
        if (fragments.length() == 0) {
            return;
        }
        context.appendSql(valueOrEmpty(open) + fragments + valueOrEmpty(close));
    }

    private String renderIteration(DynamicContext context, Iteration iteration) {
        int uniqueNumber = context.nextUniqueNumber();
        String itemAlias = "__frch_" + item + "_" + uniqueNumber;
        String indexAlias = index == null
                ? null
                : "__frch_" + index + "_" + uniqueNumber;
        BindingSnapshot oldItem = captureBinding(context, item);
        BindingSnapshot oldIndex = captureBinding(context, index);

        context.bind(item, iteration.value());
        context.bind(itemAlias, iteration.value());
        if (index != null) {
            context.bind(index, iteration.index());
            context.bind(indexAlias, iteration.index());
        }

        try {
            DynamicContext childContext = context.createChild();
            contents.apply(childContext);
            String sql = rewriteParameter(childContext.getSql(), item, itemAlias);
            if (index != null) {
                sql = rewriteParameter(sql, index, indexAlias);
            }
            return sql;
        } finally {
            restoreBinding(context, item, oldItem);
            restoreBinding(context, index, oldIndex);
        }
    }

    private List<Iteration> toIterations(Object collection) {
        List<Iteration> iterations = new ArrayList<>();
        if (collection instanceof Map<?, ?> map) {
            map.forEach((key, value) -> iterations.add(new Iteration(key, value)));
            return iterations;
        }
        if (collection instanceof Iterable<?> iterable) {
            int position = 0;
            for (Object value : iterable) {
                iterations.add(new Iteration(position++, value));
            }
            return iterations;
        }
        if (collection.getClass().isArray()) {
            for (int position = 0; position < Array.getLength(collection); position++) {
                iterations.add(new Iteration(position, Array.get(collection, position)));
            }
            return iterations;
        }
        throw new PersistenceException(
                "foreach collection must be Iterable, Map, or array: "
                        + collectionExpression
        );
    }

    private String rewriteParameter(String sql, String variable, String alias) {
        Pattern pattern = Pattern.compile(
                "(#\\{\\s*)" + Pattern.quote(variable) + "(?=\\.|\\s*(?:,|}))"
        );
        Matcher matcher = pattern.matcher(sql);
        StringBuilder rewritten = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(
                    rewritten,
                    Matcher.quoteReplacement(matcher.group(1) + alias)
            );
        }
        matcher.appendTail(rewritten);
        return rewritten.toString();
    }

    private BindingSnapshot captureBinding(DynamicContext context, String name) {
        if (name == null) {
            return BindingSnapshot.MISSING;
        }
        return new BindingSnapshot(
                context.hasBinding(name),
                context.getBindings().get(name)
        );
    }

    private void restoreBinding(
            DynamicContext context,
            String name,
            BindingSnapshot snapshot) {
        if (name == null) {
            return;
        }
        if (snapshot.present()) {
            context.bind(name, snapshot.value());
        } else {
            context.removeBinding(name);
        }
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private record Iteration(Object index, Object value) {
    }

    private record BindingSnapshot(boolean present, Object value) {

        private static final BindingSnapshot MISSING = new BindingSnapshot(false, null);
    }
}
