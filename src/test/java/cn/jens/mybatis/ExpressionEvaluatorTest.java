package cn.jens.mybatis;

import cn.jens.mybatis.scripting.xmltags.DynamicContext;
import cn.jens.mybatis.scripting.xmltags.ExpressionEvaluator;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExpressionEvaluatorTest {

    private final ExpressionEvaluator evaluator = new ExpressionEvaluator();

    @Test
    void shouldEvaluateLogicalComparisonAndParentheses() {
        DynamicContext context = new DynamicContext(Map.of(
                "age", 20,
                "active", true,
                "name", "Alice"
        ));

        assertTrue(evaluator.evaluateBoolean(
                "age >= 18 and (age < 30 or active == false)",
                context
        ));
        assertTrue(evaluator.evaluateBoolean("name != null and name != ''", context));
        assertFalse(evaluator.evaluateBoolean("age < 18 or active == false", context));
    }

    @Test
    void shouldShortCircuitNullChecks() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("author", null);
        DynamicContext context = new DynamicContext(parameters);

        assertFalse(evaluator.evaluateBoolean(
                "author != null and missing.value != null",
                context
        ));
        assertTrue(evaluator.evaluateBoolean(
                "author == null or missing.value != null",
                context
        ));
        assertFalse(evaluator.evaluateBoolean("null < 1", context));
    }
}
