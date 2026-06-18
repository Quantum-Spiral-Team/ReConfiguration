package com.qsteam.reconf.util;

import it.unimi.dsi.fastutil.doubles.DoubleOpenHashSet;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses a compact textual mini-language describing numeric ranges and
 * set-membership checks, combined with boolean operators, into a single
 * {@link Predicate}{@code <Double>} that can be evaluated against a value.
 *
 * <p>This is effectively a tiny "compiler": the expression is parsed once
 * into a tree of composed {@link Predicate} lambdas, and the resulting
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
 *   <tr><td>{@code (..b]}</td><td>no lower bound: {@code x <= b}
 *       (either side of {@code ..} may be omitted)</td></tr>
 *   <tr><td>{@code [a..)}</td><td>no upper bound: {@code x >= a}</td></tr>
 *   <tr><td>{@code (..)}</td><td>unbounded on both sides: always {@code true}</td></tr>
 *   <tr><td>{@code {n}} or {@code {n1, n2, ...}}</td><td>set membership: matches if the
 *       value equals any of the listed numbers, e.g. {@code {2, 4, 5, 6}} means
 *       {@code x == 2 || x == 4 || x == 5 || x == 6}</td></tr>
 *   <tr><td>{@code !X}</td><td>negation of condition {@code X}</td></tr>
 *   <tr><td>{@code X & Y}</td><td>logical AND (binds tighter than {@code |})</td></tr>
 *   <tr><td>{@code X | Y}</td><td>logical OR</td></tr>
 *   <tr><td>{@code (X)}</td><td>grouping with parentheses (not to be confused with
 *       interval parentheses &mdash; the lexer distinguishes them by the presence
 *       of {@code ..} inside)</td></tr>
 * </table>
 *
 * <p>Whitespace is insignificant and may appear freely between tokens,
 * e.g. {@code "( .. 85 ]  &  ! { 5 }"} is equivalent to {@code "(..85]&!{5}"}.
 *
 * <h2>Grammar</h2>
 * The expression is parsed using recursive descent with the following
 * grammar (operator precedence, from tightest to loosest binding:
 * {@code !} &gt; {@code &} &gt; {@code |}):
 * <pre>{@code
 * expr   = term { '|' term }
 * term   = factor { '&' factor }
 * factor = '!' factor | '(' expr ')' | atom
 * atom   = interval | set
 * }</pre>
 *
 * <p>Instances of this class are short-lived and stateful (they hold the
 * token stream and current parsing position), but {@link #parse(String)}
 * creates a fresh instance per call and exposes no shared mutable state,
 * so it is safe to call concurrently from multiple threads. The
 * {@link Predicate} returned by {@link #parse(String)} is itself
 * stateless and thread-safe to evaluate concurrently.
 */
public class ConditionParser {

    /**
     *  Token kinds produced by the lexer.
     */
    private enum TokenType {
        /**
         * A numeric interval block, matching patterns like {@code [a..b]}, {@code (..b]}, or {@code [a..)}.
         */
        INTERVAL,

        /**
         * An exact value or set of values enclosed in curly braces, matching patterns like {@code {a, b, c}}.
         */
        SET,

        /**
         * The logical AND operator, represented by {@code &}.
         */
        AND,

        /**
         * The logical OR operator, represented by {@code |}.
         */
        OR,

        /**
         * The logical NOT operator, represented by {@code !}.
         */
        NOT,

        /**
         * A left parenthesis {@code (} used exclusively for grouping expressions.
         */
        LPAREN,

        /**
         * A right parenthesis {@code )} used exclusively for closing a grouped expression.
         */
        RPAREN,

        /**
         * End-of-file marker indicating that the entire input string has been successfully tokenized.
         */
        EOF
    }

    /**
     * A single lexical token.
     *
     * <p>{@code value1}/{@code value2} are populated once, directly during
     * tokenization, from the regex capture groups, so that numbers are
     * parsed exactly once rather than being re-parsed later while building
     * predicates:
     * <ul>
     *   <li>for {@link TokenType#INTERVAL}: {@code value1} is the lower bound
     *       and {@code value2} is the upper bound ({@code null} means that
     *       side of the interval is open/unbounded);</li>
     *   <li>for {@link TokenType#SET}: {@code values} holds every comma-separated
     *       number listed inside {@code { ... }}; {@code value1}/{@code value2}
     *       are unused.</li>
     * </ul>
     * {@code value1}/{@code value2} are deliberately typed as the boxed
     * {@link Double} (not the primitive {@code double}) so that {@code null}
     * can represent an absent bound without resorting to sentinel values.
     */
    private record Token(TokenType type, String text, Double value1, Double value2, DoubleOpenHashSet values) {
        Token(TokenType type, String text) {
            this(type, text, null, null, null);
        }
        Token(TokenType type, String text, Double value1, Double value2) {
            this(type, text, value1, value2, null);
        }
        Token(TokenType type, String text, DoubleOpenHashSet values) {
            this(type, text, null, null, values);
        }
    }


    /**
     * Matches an interval literal, e.g. {@code (1..10]}, {@code [..5)},
     * {@code (-3.5..)}. Capture group 1 is the lower bound (may be absent),
     * capture group 2 is the upper bound (may be absent).
     */
    private static final Pattern INTERVAL_PATTERN = Pattern.compile(
            "[(\\[]\\s*([+-]?\\d+(?:\\.\\d+)?)?\\s*\\.\\.\\s*([+-]?\\d+(?:\\.\\d+)?)?\\s*[)\\]]"
    );

    /**
     *  Matches a set/exact-value literal, e.g. {@code {5}} or {@code {-3.5}}.
     */
    private static final Pattern SET_PATTERN = Pattern.compile("\\{[^}]*}");

    private final List<Token> tokens = new ArrayList<>();
    private int pos = 0;

    /**
     * Private constructor to initialize the parser with the input string.
     * * @param input the condition string to be tokenized and parsed
     */
    private ConditionParser(String input) {
        tokenize(input);
    }

    /**
     * Parses the given condition expression and compiles it into a
     * {@link Predicate}. The expression is scanned exactly once; the
     * returned predicate performs no further string parsing and can be
     * evaluated repeatedly at native comparison speed.
     *
     * @param expr the condition expression, e.g. {@code "(..85] & !{5} | [100..)"}
     * @return a predicate that evaluates the parsed condition against a given value
     * @throws IllegalArgumentException if {@code expr} contains an unexpected
     *         character, is malformed (e.g. unbalanced parentheses, a dangling
     *         operator, trailing garbage after a valid expression), or contains
     *         a number that cannot be parsed (note that {@link NumberFormatException}
     *         is itself a subclass of {@code IllegalArgumentException})
     */
    public static Predicate<Double> parse(String expr) {
        ConditionParser parser = new ConditionParser(expr);
        Predicate<Double> result = parser.parseExpr();
        if (!parser.check(TokenType.EOF)) {
            throw new IllegalArgumentException("Unexpected trailing characters after: " + parser.peek());
        }
        return result;
    }

    // ---------- Lexer ----------

    /**
     * Scans the input string into {@link #tokens}.
     *
     * <p>Intervals are attempted before generic parenthesis tokens because
     * an interval token also starts with {@code (} or {@code [}; the regex
     * requires {@code ..} inside, which a plain grouping parenthesis never
     * contains, so the two cannot be confused.
     *
     * @throws IllegalArgumentException if an unrecognized character is encountered
     */
    private void tokenize(String input) {
        int i = 0;
        while (i < input.length()) {
            char c = input.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }

            // First, attempt to match an interval. It contains "..",
            // which prevents conflicts with standard grouping parenthesis "(".
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

                DoubleOpenHashSet values = parseSetValues(inner);
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
     * Parses the comma-separated contents of a {@code { ... }} set literal,
     * e.g. {@code "2, 4, 5, 6"} -> {@code {2.0, 4.0, 5.0, 6.0}}.
     *
     * @param inner the text between the braces, not yet trimmed or split
     * @return the parsed values as a set (duplicates are silently collapsed)
     * @throws IllegalArgumentException if the literal is empty (e.g. {@code {}})
     *         or contains an empty entry (e.g. a trailing comma like {@code {2,}})
     */
    private static DoubleOpenHashSet parseSetValues(String inner) {
        DoubleOpenHashSet values = new DoubleOpenHashSet();
        for (String part : inner.split(",", -1)) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                throw new IllegalArgumentException("Empty value in set literal: {" + inner + "}");
            }
            values.add(Double.parseDouble(trimmed));
        }
        return values;
    }


    /**
     * @param s a regex capture group value, possibly {@code null} or empty
     *          when the group did not participate in the match
     * @return the parsed number, or {@code null} if {@code s} is {@code null}
     *         or empty (representing an open/unbounded interval side)
     */
    private static Double parseOrNull(String s) {
        return (s == null || s.isEmpty()) ? null : Double.parseDouble(s);
    }


    // ---------- Parser (Recursive Descent) ----------

    private Token peek() {
        return tokens.get(pos);
    }

    private Token advance() {
        return tokens.get(pos++);
    }

    private boolean check(TokenType t) {
        return peek().type() == t;
    }

    /**
     * Parses an expression.
     * Grammar: {@code expr = term { '|' term }}
     */
    private Predicate<Double> parseExpr() {
        Predicate<Double> left = parseTerm();
        while (check(TokenType.OR)) {
            advance();
            left = left.or(parseTerm());
        }
        return left;
    }

    /**
     * Parses a term.
     * Grammar: {@code term = factor { '&' factor }}
     */
    private Predicate<Double> parseTerm() {
        Predicate<Double> left = parseFactor();
        while (check(TokenType.AND)) {
            advance();
            left = left.and(parseFactor());
        }
        return left;
    }


    /**
     * Parses a factor.
     * Grammar: {@code factor = '!' factor | '(' expr ')' | atom}
     *
     * @throws IllegalArgumentException if a {@code '('} is not matched by a closing {@code ')'}
     */
    private Predicate<Double> parseFactor() {
        if (check(TokenType.NOT)) {
            advance();
            return parseFactor().negate();
        }
        if (check(TokenType.LPAREN)) {
            advance();
            Predicate<Double> inner = parseExpr();
            if (!check(TokenType.RPAREN)) {
                throw new IllegalArgumentException("Expected closing ')'");
            }
            advance();
            return inner;
        }
        return parseAtom();
    }


    /**
     * Parses an atomic element (either an interval or a set).
     * Grammar: {@code atom = interval | set}
     *
     * @throws IllegalArgumentException if the current token is neither an interval nor a set
     */
    private Predicate<Double> parseAtom() {
        Token t = advance();
        return switch (t.type()) {
            case INTERVAL -> parseInterval(t);
            case SET -> parseSet(t);
            default -> throw new IllegalArgumentException(
                    "Ожидался интервал или множество, получено: " + t);
        };
    }


    /**
     * Builds the predicate for an interval token, honoring inclusive/exclusive
     * bounds based on the bracket characters and treating a missing bound
     * ({@code null}) as {@link Double#NEGATIVE_INFINITY} /
     * {@link Double#POSITIVE_INFINITY} respectively.
     */
    private Predicate<Double> parseInterval(Token t) {
        boolean lowerInclusive = t.text().startsWith("[");
        boolean upperInclusive = t.text().endsWith("]");
        double low = t.value1() == null ? Double.NEGATIVE_INFINITY : t.value1();
        double high = t.value2() == null ? Double.POSITIVE_INFINITY : t.value2();

        return v -> (lowerInclusive ? v >= low : v > low)
                && (upperInclusive ? v <= high : v < high);
    }

    /**
     * Builds the predicate for a {@code {n1, n2, ...}} set-membership token:
     * matches if the value equals any of the listed numbers.
     *
     * <p>Note: matching is done via {@link DoubleOpenHashSet#contains}, i.e. {@link Double#equals},
     * not {@code ==}. This differs from {@code ==} in two edge cases that rarely
     * matter in practice: {@code NaN} is considered equal to itself, and
     * {@code -0.0} is considered distinct from {@code 0.0}.
     */
    @SuppressWarnings("deprecation")
    private Predicate<Double> parseSet(Token t) {
        DoubleOpenHashSet values = t.values();
        return values::contains;
    }

}