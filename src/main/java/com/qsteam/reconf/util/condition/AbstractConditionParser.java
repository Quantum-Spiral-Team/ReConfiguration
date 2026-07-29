package com.qsteam.reconf.util.condition;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Base class for condition-expression parsers. Contains all shared lexer
 * infrastructure (token types, token record, navigation helpers) and the
 * full recursive-descent grammar, leaving only the type-specific parts —
 * tokenization, atomic-predicate construction, and predicate combination —
 * to concrete subclasses.
 *
 * <h2>Grammar (shared by all subclasses)</h2>
 * <pre>{@code
 * expr   = term { '|' term }
 * term   = factor { '&' factor }
 * factor = '!' factor | '(' expr ')' | atom
 * atom   = <defined by subclass>
 * }</pre>
 *
 * <h2>Threading</h2>
 * Parser instances are short-lived and stateful. The static {@code parse()}
 * method in each subclass constructs a fresh instance per call, so concurrent
 * parsing is safe. The predicates returned by those methods are stateless and
 * safe to evaluate concurrently.
 *
 * @param <P> the predicate type produced by this parser
 *            (e.g. {@code DoublePredicate} or {@code Predicate<String>})
 */
public abstract class AbstractConditionParser<P extends Predicate<?>> {

    /**
     * Matches a set literal: {@code {a, b, c}} (values may contain spaces).
     *
     * <p>Quote-aware: a single- or double-quoted run (with {@code \}-escapes)
     * is matched as one unit, so a {@code }}, {@code ,}, or the other quote
     * character inside a quoted entry does not prematurely end the set or
     * split an entry. Used by every subclass to locate the full
     * {@code {...}} span before delegating to {@link #parseStringSetValues}
     * (or a type-specific variant) for splitting.
     */
    protected static final Pattern SET_PATTERN = Pattern.compile("\\{(?:[^}\"'\\\\]|\\\\.|'(?:[^'\\\\]|\\\\.)*'|\"(?:[^\"\\\\]|\\\\.)*\")*}");

    // ---------- Token types ----------

    /**
     * Token kinds recognized by the lexer.
     *
     * <p>{@link #INTERVAL} is produced only by the numeric parsers
     * ({@link DoubleConditionParser}, {@link LongConditionParser}); it is
     * declared here so that the shared {@link Token} record can reference
     * it without casting.
     */
    protected enum TokenType {
        /**
         * A numeric interval literal: {@code [a..b]}, {@code (..b]}, {@code [a..)}, etc.
         * Produced exclusively by {@link DoubleConditionParser} and {@link LongConditionParser}.
         */
        INTERVAL,

        /**
         * A set/list literal enclosed in curly braces: {@code {a, b, c}}.
         * The element type depends on the concrete parser subclass.
         */
        SET,

        /**
         * The logical AND operator {@code &}. Binds tighter than {@link #OR}.
         */
        AND,

        /**
         * The logical OR operator {@code |}.
         */
        OR,

        /**
         * The logical NOT (negation) operator {@code !}. Binds tightest of all.
         */
        NOT,

        /**
         * A left parenthesis {@code (} used for explicit grouping.
         */
        LPAREN,

        /**
         * A right parenthesis {@code )} closing a grouped expression.
         */
        RPAREN,

        /**
         * End-of-input sentinel appended by the lexer after the last real token.
         */
        EOF
    }

    // ---------- Token record ----------

    /**
     * A single lexical token. Only the fields relevant to each {@link TokenType}
     * are populated; all others are {@code null}.
     *
     * <ul>
     *   <li>{@link TokenType#INTERVAL} in {@link DoubleConditionParser}:
     *       {@code value1} = lower bound, {@code value2} = upper bound
     *       ({@code null} = open/unbounded side).</li>
     *   <li>{@link TokenType#INTERVAL} in {@link LongConditionParser}:
     *       {@code longValue1} = lower bound, {@code longValue2} = upper bound
     *       ({@code null} = open/unbounded side). Kept as a separate boxed
     *       {@link Long} pair (rather than reusing {@code value1}/{@code value2})
     *       so integer bounds are never routed through a {@code double} and
     *       risk losing precision.</li>
     *   <li>{@link TokenType#SET} in {@link DoubleConditionParser}:
     *       {@code numericValues} = parsed {@code double} entries.</li>
     *   <li>{@link TokenType#SET} in {@link LongConditionParser}:
     *       {@code longValues} = parsed {@code long} entries.</li>
     *   <li>{@link TokenType#SET} in {@link StringConditionParser}:
     *       {@code stringValues} = raw (possibly case-normalized) string entries,
     *       each still enclosed in its original double quotes (see
     *       {@link #parseStringSetValues}) until the subclass strips and
     *       unescapes them.</li>
     *   <li>{@link TokenType#SET} in {@link CharConditionParser}:
     *       {@code charValues} = parsed, unquoted, unescaped, single-character
     *       entries (case-folded already if the parser is case-insensitive).</li>
     *   <li>All other types: only {@code type} and {@code text} are meaningful.</li>
     * </ul>
     *
     * <p>{@code value1} and {@code value2} are boxed {@link Double} (not primitive
     * {@code double}) intentionally: {@code null} represents an absent interval
     * bound without requiring a sentinel value. The same reasoning applies to
     * the boxed {@link Long} pair {@code longValue1}/{@code longValue2}.
     */
    protected record Token(
            TokenType type,
            String text,
            Double value1,
            Double value2,
            double[] numericValues,
            String[] stringValues,
            Long longValue1,
            Long longValue2,
            long[] longValues,
            char[] charValues
    ) {
        /**
         * For operator / punctuation tokens ({@code AND}, {@code OR}, etc.).
         */
        Token(TokenType type, String text) {
            this(type, text, null, null, null, null, null, null, null, null);
        }

        /**
         * For {@link TokenType#INTERVAL} tokens in {@link DoubleConditionParser}.
         */
        Token(TokenType type, String text, Double value1, Double value2) {
            this(type, text, value1, value2, null, null, null, null, null, null);
        }

        /**
         * For {@link TokenType#SET} tokens in {@link DoubleConditionParser}.
         */
        Token(TokenType type, String text, double[] numericValues) {
            this(type, text, null, null, numericValues, null, null, null, null, null);
        }

        /**
         * For {@link TokenType#SET} tokens in {@link StringConditionParser}.
         */
        Token(TokenType type, String text, String[] stringValues) {
            this(type, text, null, null, null, stringValues, null, null, null, null);
        }

        /**
         * For {@link TokenType#INTERVAL} tokens in {@link LongConditionParser}.
         */
        Token(TokenType type, String text, Long longValue1, Long longValue2) {
            this(type, text, null, null, null, null, longValue1, longValue2, null, null);
        }

        /**
         * For {@link TokenType#SET} tokens in {@link LongConditionParser}.
         */
        Token(TokenType type, String text, long[] longValues) {
            this(type, text, null, null, null, null, null, null, longValues, null);
        }

        /**
         * For {@link TokenType#SET} tokens in {@link CharConditionParser}
         */
        Token(TokenType type, String text, char[] charValues) {
            this(type, text, null, null, null, null, null, null, null, charValues);
        }
    }

    // ---------- State ----------

    /**
     * Token stream produced by {@link #tokenize(String)}.
     */
    protected final List<Token> tokens = new ArrayList<>();

    /**
     * Current position in {@link #tokens}.
     */
    protected int pos = 0;

    protected AbstractConditionParser() {
    }

    // ---------- Abstract contract ----------

    /**
     * Scans {@code input} into {@link #tokens} and appends a terminal
     * {@link TokenType#EOF} token. Called once from the constructor.
     *
     * @throws IllegalArgumentException if the input contains an unrecognized character
     */
    protected abstract void tokenize(String input);

    /**
     * Parses an atomic predicate (interval, set, etc.) from the current token.
     * Called by {@link #parseFactor()} after ruling out {@code !} and grouping.
     *
     * @throws IllegalArgumentException if the current token is not a valid atom
     */
    protected abstract P parseAtom();

    /**
     * Returns a predicate that accepts a value only when both {@code a} and {@code b} accept it.
     */
    protected abstract P and(P a, P b);

    /**
     * Returns a predicate that accepts a value when either {@code a} or {@code b} accepts it.
     */
    protected abstract P or(P a, P b);

    /**
     * Returns a predicate that accepts exactly the values rejected by {@code p}, and vice versa.
     */
    protected abstract P negate(P p);

    // ---------- Token navigation ----------

    /**
     * Returns the token at the current position without consuming it.
     */
    protected Token peek() {
        return tokens.get(pos);
    }

    /**
     * Returns the token at the current position and advances the position.
     */
    protected Token advance() {
        return tokens.get(pos++);
    }

    /**
     * Returns {@code true} if the current token has type {@code t}.
     */
    protected boolean check(TokenType t) {
        return peek().type() == t;
    }

    // ---------- Recursive descent (shared grammar) ----------

    /**
     * {@code expr = term { '|' term }}
     *
     * <p>Lowest-precedence rule. Builds a left-associative chain of OR-predicates.
     */
    protected P parseExpr() {
        P left = parseTerm();
        while (check(TokenType.OR)) {
            advance();
            left = or(left, parseTerm());
        }
        return left;
    }

    /**
     * {@code term = factor { '&' factor }}
     *
     * <p>Mid-precedence rule. Builds a left-associative chain of AND-predicates.
     */
    protected P parseTerm() {
        P left = parseFactor();
        while (check(TokenType.AND)) {
            advance();
            left = and(left, parseFactor());
        }
        return left;
    }

    /**
     * {@code factor = '!' factor | '(' expr ')' | atom}
     *
     * <p>Handles right-associative negation and explicit grouping with parentheses,
     * then delegates to {@link #parseAtom()} for concrete atomic predicates.
     *
     * @throws IllegalArgumentException if a {@code (} is not matched by a closing {@code )}
     */
    protected P parseFactor() {
        if (check(TokenType.NOT)) {
            advance();
            return negate(parseFactor());
        }
        if (check(TokenType.LPAREN)) {
            advance();
            P inner = parseExpr();
            if (!check(TokenType.RPAREN)) {
                throw new IllegalArgumentException("Expected closing ')'");
            }
            advance();
            return inner;
        }
        return parseAtom();
    }

    // ---------- Shared set-parsing helpers ----------

    /**
     * Splits the comma-separated contents of a {@code {...}} set literal into
     * raw entries, respecting single- and double-quoted runs.
     *
     * <p>Used by {@link StringConditionParser} and {@link CharConditionParser}.
     * Each returned entry is stripped of leading/trailing whitespace but keeps
     * its surrounding quote characters (if any) and any {@code \}-escape
     * sequences verbatim; the caller is responsible for stripping the quotes,
     * unescaping (via {@link #unescapeJava}), and any further normalization
     * such as case folding.
     *
     * <p>A comma or the opposite quote character found <em>inside</em> a
     * quoted run does not split or close the entry — only a comma outside any
     * quotes acts as a separator, and only a matching, unescaped quote closes
     * a quoted run. This is what lets entries like {@code {"a, b", 'c'}}
     * split into exactly {@code ["a, b"]} and {@code ['c']} instead of three
     * pieces.
     *
     * @param inner the text between the braces, without leading/trailing whitespace
     * @return the parsed entries in encounter order; duplicates are preserved
     *         (the predicate's container type may collapse them)
     * @throws IllegalArgumentException if {@code inner} is empty, contains an
     *         empty entry (e.g. a trailing comma like {@code {a,}}), or
     *         contains an unterminated quote
     */
    protected static String[] parseStringSetValues(String inner) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingleQuotes = false;
        boolean inDoubleQuotes = false;
        boolean escaped = false;

        for (int i = 0; i < inner.length(); i++) {
            char c = inner.charAt(i);

            if (escaped) {
                current.append(c);
                escaped = false;
            } else if (c == '\\') {
                current.append(c);
                escaped = true;
            } else if (c == '\'' && !inDoubleQuotes) {
                inSingleQuotes = !inSingleQuotes;
                current.append(c);
            } else if (c == '"' && !inSingleQuotes) {
                inDoubleQuotes = !inDoubleQuotes;
                current.append(c);
            } else if (c == ',' && !inSingleQuotes && !inDoubleQuotes) {
                result.add(current.toString().strip());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }

        if (inSingleQuotes || inDoubleQuotes) {
            throw new IllegalArgumentException("Unclosed quote in set literal: {" + inner + "}");
        }

        result.add(current.toString().strip());

        String[] values = new String[result.size()];
        for (int i = 0; i < result.size(); i++) {
            String val = result.get(i);
            if (val.isEmpty()) {
                throw new IllegalArgumentException("Empty value in set literal: {" + inner + "}");
            }
            values[i] = val;
        }
        return values;
    }

    /**
     * Interprets Java-style {@code \}-escape sequences in an already
     * quote-stripped entry, returning the literal text they represent.
     *
     * <p>Used by {@link StringConditionParser} and {@link CharConditionParser}
     * after they strip the surrounding quotes that {@link #parseStringSetValues}
     * left in place. Recognizes {@code \n}, {@code \r}, {@code \t}, and
     * {@code \\uXXXX} (a 4-hex-digit Unicode escape); any other
     * {@code \}-prefixed character (including {@code \'}, {@code \"}, and
     * {@code \\} itself) is unescaped to just that character. A trailing,
     * unpaired {@code \} at the end of the input is kept as-is.
     *
     * <p>This does <em>not</em> touch quote characters or commas by itself —
     * it only interprets backslash sequences — so it is safe to call on text
     * that has already had its quotes and comma-splitting handled by
     * {@link #parseStringSetValues}.
     *
     * @param s the quote-stripped entry text, still containing raw {@code \}-escapes
     * @return the entry with all recognized escape sequences resolved
     */
    protected static String unescapeJava(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char next = s.charAt(i + 1);
                if (next == 'n') sb.append('\n');
                else if (next == 'r') sb.append('\r');
                else if (next == 't') sb.append('\t');
                else if (next == 'u' && i + 5 < s.length()) {
                    sb.append((char) Integer.parseInt(s.substring(i + 2, i + 6), 16));
                    i += 5;
                } else {
                    sb.append(next);
                }
                i++;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Splits and trims the comma-separated contents of a {@code {...}} set
     * literal and parses each entry as a {@code double}.
     *
     * <p>Used by {@link DoubleConditionParser}.
     *
     * @param inner the text between the braces, without leading/trailing whitespace
     * @return the parsed values in encounter order
     * @throws IllegalArgumentException if {@code inner} is empty, contains an
     *                                  empty entry, or contains a value that is not a valid {@code double}
     */
    protected static double[] parseNumericSetValues(String inner) {
        String[] parts = inner.split(",", -1);
        double[] values = new double[parts.length];
        for (int i = 0; i < parts.length; i++) {
            String trimmed = parts[i].trim();
            if (trimmed.isEmpty()) {
                throw new IllegalArgumentException(
                        "Empty value in set literal: {" + inner + "}");
            }
            values[i] = Double.parseDouble(trimmed);
        }
        return values;
    }

    /**
     * Splits and trims the comma-separated contents of a {@code {...}} set
     * literal and parses each entry as a {@code long}.
     *
     * <p>Used by {@link LongConditionParser}. Unlike {@link #parseNumericSetValues},
     * entries are parsed directly with {@link Long#parseLong(String)} rather than
     * via {@code double}, so values are exact across the full {@code long} range.
     *
     * @param inner the text between the braces, without leading/trailing whitespace
     * @return the parsed values in encounter order
     * @throws IllegalArgumentException if {@code inner} is empty, contains an
     *                                  empty entry, or contains a value that is not a valid {@code long}
     *                                  ({@link NumberFormatException} is a subclass of
     *                                  {@code IllegalArgumentException})
     */
    protected static long[] parseLongSetValues(String inner) {
        String[] parts = inner.split(",", -1);
        long[] values = new long[parts.length];
        for (int i = 0; i < parts.length; i++) {
            String trimmed = parts[i].trim();
            if (trimmed.isEmpty()) {
                throw new IllegalArgumentException(
                        "Empty value in set literal: {" + inner + "}");
            }
            values[i] = Long.parseLong(trimmed);
        }
        return values;
    }
}