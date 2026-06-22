package com.qsteam.reconf.util.condition;

import it.unimi.dsi.fastutil.doubles.DoublePredicate;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses a compact textual mini-language describing numeric ranges and
 * set-membership checks, combined with boolean operators, into a single
 * {@link DoublePredicate} that can be evaluated against a {@code double} value.
 *
 * <p>This is effectively a tiny "compiler": the expression is parsed once
 * into a tree of composed {@link DoublePredicate} lambdas, and the resulting
 * predicate can then be evaluated repeatedly with no further string
 * processing, regex matching, or tokenizing involved.
 *
 * <h2>Supported syntax</h2>
 * <table border="1" cellpadding="3" summary="syntax overview">
 *   <tr><th>Pattern</th><th>Meaning</th></tr>
 *   <tr><td>{@code (a..b)}</td><td>open interval: {@code a < x < b}</td></tr>
 *   <tr><td>{@code [a..b]}</td><td>closed interval: {@code a <= x <= b}</td></tr>
 *   <tr><td>{@code (a..b]}</td><td>half-open: {@code a < x <= b}</td></tr>
 *   <tr><td>{@code [a..b)}</td><td>half-open: {@code a <= x < b}</td></tr>
 *   <tr><td>{@code (..b]}</td><td>no lower bound: {@code x <= b}</td></tr>
 *   <tr><td>{@code [a..)}</td><td>no upper bound: {@code x >= a}</td></tr>
 *   <tr><td>{@code (..)}</td><td>unbounded on both sides: always {@code true}</td></tr>
 *   <tr><td>{@code {n}} or {@code {n1, n2, ...}}</td><td>set membership: matches if
 *       {@code x} equals any of the listed numbers</td></tr>
 *   <tr><td>{@code !X}</td><td>negation of condition {@code X}</td></tr>
 *   <tr><td>{@code X & Y}</td><td>logical AND (binds tighter than {@code |})</td></tr>
 *   <tr><td>{@code X | Y}</td><td>logical OR</td></tr>
 *   <tr><td>{@code (X)}</td><td>explicit grouping (distinguished from interval
 *       parentheses by the absence of {@code ..} inside)</td></tr>
 * </table>
 *
 * <p>Whitespace is insignificant and may appear freely between tokens.
 *
 * <h2>Grammar</h2>
 * <pre>{@code
 * expr   = term { '|' term }
 * term   = factor { '&' factor }
 * factor = '!' factor | '(' expr ')' | atom
 * atom   = interval | set
 * }</pre>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * DoublePredicate condition = NumericConditionParser.parse("(..85] & !{5} | [100..)");
 * condition.test(50.0);  // true  -> falls inside (..85]
 * condition.test(5.0);   // false -> excluded via !{5}
 * condition.test(150.0); // true  -> falls inside [100..)
 * }</pre>
 */
public class NumericConditionParser extends AbstractConditionParser<DoublePredicate> {

    /**
     * Matches an interval literal, e.g. {@code (1..10]}, {@code [..5)},
     * {@code (-3.5..)}. Capture group 1 is the lower bound (may be absent),
     * capture group 2 is the upper bound (may be absent).
     */
    private static final Pattern INTERVAL_PATTERN = Pattern.compile(
            "[(\\[]\\s*([+-]?\\d+(?:\\.\\d+)?)?\\s*\\.\\.\\s*([+-]?\\d+(?:\\.\\d+)?)?\\s*[)\\]]"
    );

    private NumericConditionParser() {}

    /**
     * Parses the given condition expression and compiles it into a
     * {@link DoublePredicate}. The expression is scanned exactly once; the
     * returned predicate performs no further string parsing and can be
     * evaluated repeatedly at native comparison speed.
     *
     * @param expr the condition expression, e.g. {@code "(..85] & !{5} | [100..)"}
     * @return a predicate that evaluates the parsed condition against a given value
     * @throws IllegalArgumentException if {@code expr} contains an unexpected
     *         character, is malformed (e.g. unbalanced parentheses, a dangling
     *         operator, or trailing garbage), or contains a number that cannot
     *         be parsed ({@link NumberFormatException} is a subclass of
     *         {@code IllegalArgumentException})
     */
    public static DoublePredicate parse(String expr) {
        NumericConditionParser parser = new NumericConditionParser();
        parser.tokenize(expr);
        DoublePredicate result = parser.parseExpr();
        if (!parser.check(TokenType.EOF)) {
            throw new IllegalArgumentException("Unexpected trailing characters after: " + parser.peek());
        }
        return result;
    }

    // ---------- Lexer ----------

    /**
     * Scans the input string into the token list.
     *
     * <p>Interval matching is attempted before plain parenthesis tokens because
     * intervals also start with {@code (} or {@code [}; the regex requires
     * {@code ..} inside, which grouping parentheses never contain.
     *
     * @throws IllegalArgumentException if an unrecognized character is encountered
     */
    @Override
    protected void tokenize(String input) {
        int i = 0;
        while (i < input.length()) {
            char c = input.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }

            // Intervals must be tried before '(' to avoid consuming the opening
            // bracket of a grouping expression as part of an incomplete interval.
            Matcher mi = INTERVAL_PATTERN.matcher(input);
            mi.region(i, input.length());
            if (mi.lookingAt()) {
                Double low = parseOrNull(mi.group(1));
                Double high = parseOrNull(mi.group(2));
                tokens.add(new Token(TokenType.INTERVAL, mi.group(), low, high));
                i = mi.end();
                continue;
            }

            Matcher ms = SET_PATTERN.matcher(input);
            ms.region(i, input.length());
            if (ms.lookingAt()) {
                String inner = ms.group().substring(1, ms.group().length() - 1).trim();
                double[] values = parseNumericSetValues(inner);
                tokens.add(new Token(TokenType.SET, ms.group(), values));
                i = ms.end();
                continue;
            }

            switch (c) {
                case '&' -> tokens.add(new Token(TokenType.AND, "&"));
                case '|' -> tokens.add(new Token(TokenType.OR, "|"));
                case '!' -> tokens.add(new Token(TokenType.NOT, "!"));
                case '(' -> tokens.add(new Token(TokenType.LPAREN, "("));
                case ')' -> tokens.add(new Token(TokenType.RPAREN, ")"));
                default -> throw new IllegalArgumentException(
                        "Unexpected character '" + c + "' at position " + i);
            }
            i++;
        }
        tokens.add(new Token(TokenType.EOF, ""));
    }

    /**
     * @param s a regex capture group value; {@code null} or empty when the
     *          group did not participate in the match (i.e. the bound is absent)
     * @return the parsed number, or {@code null} representing an open/unbounded side
     */
    private static Double parseOrNull(String s) {
        return (s == null || s.isEmpty()) ? null : Double.parseDouble(s);
    }

    // ---------- Predicate combinators ----------

    @Override
    protected DoublePredicate and(DoublePredicate a, DoublePredicate b) {
        return a.and(b);
    }

    @Override
    protected DoublePredicate or(DoublePredicate a, DoublePredicate b) {
        return a.or(b);
    }

    @Override
    protected DoublePredicate negate(DoublePredicate p) {
        return p.negate();
    }

    // ---------- Atom parsing ----------

    /**
     * {@code atom = interval | set}
     *
     * @throws IllegalArgumentException if the current token is neither
     */
    @Override
    protected DoublePredicate parseAtom() {
        Token t = advance();
        return switch (t.type()) {
            case INTERVAL -> parseInterval(t);
            case SET -> parseSet(t);
            default -> throw new IllegalArgumentException(
                    "Expected an interval or a set literal, got: " + t);
        };
    }

    /**
     * Builds the predicate for an interval token, honoring inclusive/exclusive
     * bounds based on the bracket characters. A missing bound ({@code null}) is
     * treated as {@link Double#NEGATIVE_INFINITY} or {@link Double#POSITIVE_INFINITY}.
     */
    private DoublePredicate parseInterval(Token t) {
        boolean lowerInclusive = t.text().startsWith("[");
        boolean upperInclusive = t.text().endsWith("]");
        double low = t.value1() == null ? Double.NEGATIVE_INFINITY : t.value1();
        double high = t.value2() == null ? Double.POSITIVE_INFINITY : t.value2();

        return v -> (lowerInclusive ? v >= low : v > low)
                && (upperInclusive ? v <= high : v < high);
    }

    /**
     * Builds the predicate for a {@code {n1, n2, ...}} set-membership token.
     * Matching uses {@link Double#compare} rather than {@code ==}: {@code NaN}
     * is considered equal to itself, and {@code -0.0} is distinct from {@code 0.0}.
     */
    private DoublePredicate parseSet(Token t) {
        double[] values = t.numericValues();
        return val -> {
            for (double v : values) {
                if (Double.compare(v, val) == 0) return true;
            }
            return false;
        };
    }
}