package cn.jens.mybatis.scripting.xmltags;

import cn.jens.mybatis.exception.PersistenceException;
import cn.jens.mybatis.scripting.PropertyAccessor;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;

/** 支持常用 OGNL 子集的轻量表达式求值器。 */
public class ExpressionEvaluator {

    public boolean evaluateBoolean(String expression, DynamicContext context) {
        if (expression == null || expression.isBlank()) {
            throw new PersistenceException("Dynamic SQL test expression must not be blank");
        }
        return isTruthy(evaluate(expression.strip(), context));
    }

    public Object evaluateValue(String expression, DynamicContext context) {
        if (expression == null || expression.isBlank()) {
            throw new PersistenceException("Dynamic SQL expression must not be blank");
        }
        return resolveOperand(stripOuterParentheses(expression.strip()), context);
    }

    private Object evaluate(String expression, DynamicContext context) {
        String candidate = stripOuterParentheses(expression.strip());
        int operatorIndex = findLogicalOperator(candidate, "or", "||");
        if (operatorIndex >= 0) {
            String operator = logicalOperatorAt(candidate, operatorIndex, "or", "||");
            return isTruthy(evaluate(candidate.substring(0, operatorIndex), context))
                    || isTruthy(evaluate(
                            candidate.substring(operatorIndex + operator.length()),
                            context
                    ));
        }

        operatorIndex = findLogicalOperator(candidate, "and", "&&");
        if (operatorIndex >= 0) {
            String operator = logicalOperatorAt(candidate, operatorIndex, "and", "&&");
            return isTruthy(evaluate(candidate.substring(0, operatorIndex), context))
                    && isTruthy(evaluate(
                            candidate.substring(operatorIndex + operator.length()),
                            context
                    ));
        }

        Comparison comparison = findComparison(candidate);
        if (comparison != null) {
            Object left = resolveOperand(candidate.substring(0, comparison.index()), context);
            Object right = resolveOperand(
                    candidate.substring(comparison.index() + comparison.operator().length()),
                    context
            );
            return compare(left, right, comparison.operator());
        }

        if (candidate.startsWith("!") && !candidate.startsWith("!=")) {
            return !isTruthy(evaluate(candidate.substring(1), context));
        }
        if (startsWithWord(candidate, "not")) {
            return !isTruthy(evaluate(candidate.substring(3), context));
        }
        return resolveOperand(candidate, context);
    }

    private Object resolveOperand(String operand, DynamicContext context) {
        String value = stripOuterParentheses(operand.strip());
        if (value.isBlank()) {
            throw new PersistenceException("Invalid empty dynamic SQL operand");
        }
        if ("null".equalsIgnoreCase(value)) {
            return null;
        }
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            return Boolean.parseBoolean(value);
        }
        if (isQuoted(value)) {
            return unquote(value);
        }
        if (value.matches("[-+]?\\d+(?:\\.\\d+)?")) {
            return new BigDecimal(value);
        }
        return PropertyAccessor.getValue(
                context.getParameterObject(),
                context.getBindings(),
                value
        );
    }

    private boolean compare(Object left, Object right, String operator) {
        if ((left == null || right == null)
                && !"==".equals(operator)
                && !"!=".equals(operator)) {
            return false;
        }
        return switch (operator) {
            case "==" -> valuesEqual(left, right);
            case "!=" -> !valuesEqual(left, right);
            case ">" -> compareOrder(left, right) > 0;
            case ">=" -> compareOrder(left, right) >= 0;
            case "<" -> compareOrder(left, right) < 0;
            case "<=" -> compareOrder(left, right) <= 0;
            default -> throw new PersistenceException(
                    "Unsupported dynamic SQL operator: " + operator
            );
        };
    }

    private boolean valuesEqual(Object left, Object right) {
        if (left instanceof Number leftNumber && right instanceof Number rightNumber) {
            return toDecimal(leftNumber).compareTo(toDecimal(rightNumber)) == 0;
        }
        return Objects.equals(left, right);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private int compareOrder(Object left, Object right) {
        if (left == null || right == null) {
            return -1;
        }
        if (left instanceof Number leftNumber && right instanceof Number rightNumber) {
            return toDecimal(leftNumber).compareTo(toDecimal(rightNumber));
        }
        if (left instanceof Comparable comparable
                && left.getClass().isAssignableFrom(right.getClass())) {
            return comparable.compareTo(right);
        }
        throw new PersistenceException(
                "Dynamic SQL values are not comparable: " + left + " and " + right
        );
    }

    private BigDecimal toDecimal(Number value) {
        return new BigDecimal(value.toString());
    }

    private boolean isTruthy(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof Number number) {
            return toDecimal(number).compareTo(BigDecimal.ZERO) != 0;
        }
        if (value instanceof CharSequence sequence) {
            return !sequence.isEmpty();
        }
        if (value instanceof Collection<?> collection) {
            return !collection.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return !map.isEmpty();
        }
        if (value.getClass().isArray()) {
            return Array.getLength(value) > 0;
        }
        return true;
    }

    private int findLogicalOperator(String expression, String word, String symbol) {
        int symbolIndex = findTopLevelToken(expression, symbol, false);
        int wordIndex = findTopLevelToken(expression, word, true);
        if (symbolIndex < 0) {
            return wordIndex;
        }
        if (wordIndex < 0) {
            return symbolIndex;
        }
        return Math.min(symbolIndex, wordIndex);
    }

    private String logicalOperatorAt(
            String expression,
            int index,
            String word,
            String symbol) {
        return expression.startsWith(symbol, index) ? symbol : word;
    }

    private Comparison findComparison(String expression) {
        for (String operator : new String[]{"==", "!=", ">=", "<=", ">", "<"}) {
            int index = findTopLevelToken(expression, operator, false);
            if (index >= 0) {
                return new Comparison(index, operator);
            }
        }
        return null;
    }

    private int findTopLevelToken(String expression, String token, boolean wordToken) {
        int parenthesesDepth = 0;
        char quote = 0;
        boolean escaped = false;
        for (int index = 0; index <= expression.length() - token.length(); index++) {
            char current = expression.charAt(index);
            if (quote != 0) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == quote) {
                    quote = 0;
                }
                continue;
            }
            if (current == '\'' || current == '"') {
                quote = current;
                continue;
            }
            if (current == '(') {
                parenthesesDepth++;
                continue;
            }
            if (current == ')') {
                parenthesesDepth--;
                continue;
            }
            if (parenthesesDepth == 0
                    && expression.regionMatches(true, index, token, 0, token.length())
                    && (!wordToken || hasWordBoundaries(expression, index, token.length()))) {
                return index;
            }
        }
        return -1;
    }

    private boolean hasWordBoundaries(String expression, int index, int length) {
        return (index == 0 || !isIdentifierCharacter(expression.charAt(index - 1)))
                && (index + length == expression.length()
                || !isIdentifierCharacter(expression.charAt(index + length)));
    }

    private boolean startsWithWord(String expression, String word) {
        return expression.regionMatches(true, 0, word, 0, word.length())
                && hasWordBoundaries(expression, 0, word.length());
    }

    private boolean isIdentifierCharacter(char character) {
        return Character.isLetterOrDigit(character)
                || character == '_'
                || character == '$';
    }

    private String stripOuterParentheses(String expression) {
        String candidate = expression;
        while (candidate.startsWith("(")
                && candidate.endsWith(")")
                && outerParenthesesWrapWholeExpression(candidate)) {
            candidate = candidate.substring(1, candidate.length() - 1).strip();
        }
        return candidate;
    }

    private boolean outerParenthesesWrapWholeExpression(String expression) {
        int depth = 0;
        char quote = 0;
        for (int index = 0; index < expression.length(); index++) {
            char current = expression.charAt(index);
            if (quote != 0) {
                if (current == quote && expression.charAt(index - 1) != '\\') {
                    quote = 0;
                }
                continue;
            }
            if (current == '\'' || current == '"') {
                quote = current;
            } else if (current == '(') {
                depth++;
            } else if (current == ')' && --depth == 0) {
                return index == expression.length() - 1;
            }
        }
        return false;
    }

    private boolean isQuoted(String value) {
        return value.length() >= 2
                && ((value.startsWith("'") && value.endsWith("'"))
                || (value.startsWith("\"") && value.endsWith("\"")));
    }

    private String unquote(String value) {
        return value.substring(1, value.length() - 1)
                .replace("\\'", "'")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    private record Comparison(int index, String operator) {
    }
}
