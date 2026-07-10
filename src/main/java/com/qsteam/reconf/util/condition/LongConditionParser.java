package com.qsteam.reconf.util.condition;

import it.unimi.dsi.fastutil.longs.LongPredicate;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses a compact textual mini-language describing integer ranges and
 * set-membership checks, combined with boolean operators, into a single
 * {@link LongPredicate} that can be evaluated against a {@code long} value.
 *
 * <p>The syntax mirrors {@link DoubleConditionParser}, but interval bounds
 * and set entries must be plain integers (optionally signed, no decimal
 * point or exponent). Every bound and set entry is parsed with
 * {@link Long#parseLong(String)} directly from the source text — never
 * routed through {@code double} — so values are represented exactly across
 * the full {@code long} range, including magnitudes beyond 2^53 where a
 * {@code double} would start losing precision.
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
 *       {@code x} equals any of the listed integers</td></tr>
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
 * LongPredicate condition = LongConditionParser.parse("[0..1000) & !{13} | [1000000..)");
 * condition.test(13L);        // false -> excluded via !{13}
 * condition.test(500L);       // true  -> falls inside [0..1000)
 * condition.test(2_000_000L); // true  -> falls inside [1000000..)
 * }</pre>
 */
public class LongConditionParser extends AbstractConditionParser<LongPredicate> {

    /**
     * Matches an integer interval literal, e.g. {@code (1..10]}, {@code [..5)},
     * {@code (-3..)}. Capture group 1 is the lower bound (may be absent),
     * capture group 2 is the upper bound (may be absent). Unlike
     * {@link DoubleConditionParser}'s pattern, no decimal point is accepted,
     * so every captured bound is a plain integer literal.
     */
    private static final Pattern INTERVAL_PATTERN = Pattern.compile(
            "[(\\[]\\s*([+-]?\\d+)?\\s*\\.\\.\\s*([+-]?\\d+)?\\s*[)\\]]"
    );

    private LongConditionParser() {}

    /**
     * Parses the given condition expression and compiles it into a
     * {@link LongPredicate}. The expression is scanned exactly once; the
     * returned predicate performs no further string parsing and can be
     * evaluated repeatedly at native comparison speed.
     *
     * @param expr the condition expression, e.g. {@code "[0..1000) & !{13} | [1000000..)"}
     * @return a predicate that evaluates the parsed condition against a given value
     * @throws IllegalArgumentException if {@code expr} contains an unexpected
     *         character, is malformed (e.g. unbalanced parentheses, a dangling
     *         operator, or trailing garbage), or contains a number that is not
     *         a valid {@code long} literal ({@link NumberFormatException} is a
     *         subclass of {@code IllegalArgumentException})
     */
    public static LongPredicate parse(String expr) {
        LongConditionParser parser = new LongConditionParser();
        parser.tokenize(expr);
        LongPredicate result = parser.parseExpr();
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
     * <p>Bounds and set entries are parsed to {@code long} right here, once,
     * via {@link Long#parseLong} directly from the matched digits, and stored
     * in the {@link Token}'s dedicated {@code longValue1}/{@code longValue2}
     * and {@code longValues} fields (mirroring how {@link DoubleConditionParser}
     * uses {@code value1}/{@code value2} and {@code numericValues}). No integer
     * value is ever routed through a {@code double} intermediate, so precision
     * is exact across the full {@code long} range.
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
                Long low = parseOrNull(mi.group(1));
                Long high = parseOrNull(mi.group(2));
                tokens.add(new Token(TokenType.INTERVAL, mi.group(), low, high));
                i = mi.end();
                continue;
            }

            Matcher ms = SET_PATTERN.matcher(input);
            ms.region(i, input.length());
            if (ms.lookingAt()) {
                String inner = ms.group().substring(1, ms.group().length() - 1).trim();
                long[] values = parseLongSetValues(inner);
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
     * @return the parsed integer, or {@code null} representing an open/unbounded side
     */
    private static Long parseOrNull(String s) {
        return (s == null || s.isEmpty()) ? null : Long.parseLong(s);
    }

    // ---------- Predicate combinators ----------

    @Override
    protected LongPredicate and(LongPredicate a, LongPredicate b) {
        return a.and(b);
    }

    @Override
    protected LongPredicate or(LongPredicate a, LongPredicate b) {
        return a.or(b);
    }

    @Override
    protected LongPredicate negate(LongPredicate p) {
        return p.negate();
    }

    // ---------- Atom parsing ----------

    /**
     * {@code atom = interval | set}
     *
     * @throws IllegalArgumentException if the current token is neither
     */
    @Override
    protected LongPredicate parseAtom() {
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
     * bounds based on the bracket characters. A missing bound ({@code null}) means
     * that side is unconstrained; unlike {@link DoubleConditionParser}, this is not
     * modeled by substituting {@link Long#MIN_VALUE}/{@link Long#MAX_VALUE} (which
     * would wrongly interact with an exclusive bound at the extreme of the range),
     * so bound presence is checked explicitly instead.
     */
    private LongPredicate parseInterval(Token t) {
        boolean lowerInclusive = t.text().startsWith("[");
        boolean upperInclusive = t.text().endsWith("]");
        Long low = t.longValue1();
        Long high = t.longValue2();

        return v -> (low == null || (lowerInclusive ? v >= low : v > low))
                && (high == null || (upperInclusive ? v <= high : v < high));
    }

    /**
     * Builds the predicate for a {@code {n1, n2, ...}} set-membership token.
     */
    private LongPredicate parseSet(Token t) {
        long[] values = t.longValues();
        return val -> {
            for (long v : values) {
                if (v == val) return true;
            }
            return false;
        };
    }
}