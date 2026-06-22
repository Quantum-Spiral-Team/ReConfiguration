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

    /** Matches a set literal: {@code {a, b, c}} (values may contain spaces). */
    protected static final Pattern SET_PATTERN = Pattern.compile("\\{[^}]*}");

    // ---------- Token types ----------

    /**
     * Token kinds recognized by the lexer.
     *
     * <p>{@link #INTERVAL} is produced only by {@link NumericConditionParser};
     * it is declared here so that the shared {@link Token} record can reference
     * it without casting.
     */
    protected enum TokenType {
        /**
         * A numeric interval literal: {@code [a..b]}, {@code (..b]}, {@code [a..)}, etc.
         * Produced exclusively by {@link NumericConditionParser}.
         */
        INTERVAL,

        /**
         * A set/list literal enclosed in curly braces: {@code {a, b, c}}.
         * The element type depends on the concrete parser subclass.
         */
        SET,

        /** The logical AND operator {@code &}. Binds tighter than {@link #OR}. */
        AND,

        /** The logical OR operator {@code |}. */
        OR,

        /** The logical NOT (negation) operator {@code !}. Binds tightest of all. */
        NOT,

        /** A left parenthesis {@code (} used for explicit grouping. */
        LPAREN,

        /** A right parenthesis {@code )} closing a grouped expression. */
        RPAREN,

        /** End-of-input sentinel appended by the lexer after the last real token. */
        EOF
    }

    // ---------- Token record ----------

    /**
     * A single lexical token. Only the fields relevant to each {@link TokenType}
     * are populated; all others are {@code null}.
     *
     * <ul>
     *   <li>{@link TokenType#INTERVAL}: {@code value1} = lower bound,
     *       {@code value2} = upper bound ({@code null} = open/unbounded side).</li>
     *   <li>{@link TokenType#SET} in {@link NumericConditionParser}:
     *       {@code numericValues} = parsed {@code double} entries.</li>
     *   <li>{@link TokenType#SET} in {@link StringConditionParser}:
     *       {@code stringValues} = raw (possibly case-normalized) string entries.</li>
     *   <li>All other types: only {@code type} and {@code text} are meaningful.</li>
     * </ul>
     *
     * <p>{@code value1} and {@code value2} are boxed {@link Double} (not primitive
     * {@code double}) intentionally: {@code null} represents an absent interval
     * bound without requiring a sentinel value.
     */
    protected record Token(
            TokenType type,
            String text,
            Double value1,
            Double value2,
            double[] numericValues,
            String[] stringValues
    ) {
        /** For operator / punctuation tokens ({@code AND}, {@code OR}, etc.). */
        Token(TokenType type, String text) {
            this(type, text, null, null, null, null);
        }

        /** For {@link TokenType#INTERVAL} tokens. */
        Token(TokenType type, String text, Double value1, Double value2) {
            this(type, text, value1, value2, null, null);
        }

        /** For {@link TokenType#SET} tokens in {@link NumericConditionParser}. */
        Token(TokenType type, String text, double[] numericValues) {
            this(type, text, null, null, numericValues, null);
        }

        /** For {@link TokenType#SET} tokens in {@link StringConditionParser}. */
        Token(TokenType type, String text, String[] stringValues) {
            this(type, text, null, null, null, stringValues);
        }
    }

    // ---------- State ----------

    /** Token stream produced by {@link #tokenize(String)}. */
    protected final List<Token> tokens = new ArrayList<>();

    /** Current position in {@link #tokens}. */
    protected int pos = 0;

    protected AbstractConditionParser() {}

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

    /** Returns a predicate that accepts a value only when both {@code a} and {@code b} accept it. */
    protected abstract P and(P a, P b);

    /** Returns a predicate that accepts a value when either {@code a} or {@code b} accepts it. */
    protected abstract P or(P a, P b);

    /** Returns a predicate that accepts exactly the values rejected by {@code p}, and vice versa. */
    protected abstract P negate(P p);

    // ---------- Token navigation ----------

    /** Returns the token at the current position without consuming it. */
    protected Token peek() {
        return tokens.get(pos);
    }

    /** Returns the token at the current position and advances the position. */
    protected Token advance() {
        return tokens.get(pos++);
    }

    /** Returns {@code true} if the current token has type {@code t}. */
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
     * Splits and trims the comma-separated contents of a {@code {...}} set
     * literal and returns the raw string entries.
     *
     * <p>Used by {@link StringConditionParser}. The caller is responsible for
     * any further normalization (e.g. case folding).
     *
     * @param inner the text between the braces, without leading/trailing whitespace
     * @return the parsed entries in encounter order; duplicates are preserved
     *         (the predicate's container type may collapse them)
     * @throws IllegalArgumentException if {@code inner} is empty or contains
     *         an empty entry (e.g. a trailing comma like {@code {a,}})
     */
    protected static String[] parseStringSetValues(String inner) {
        String[] parts = inner.split(",", -1);
        String[] values = new String[parts.length];
        for (int i = 0; i < parts.length; i++) {
            String trimmed = parts[i].trim();
            if (trimmed.isEmpty()) {
                throw new IllegalArgumentException(
                        "Empty value in set literal: {" + inner + "}");
            }
            values[i] = trimmed;
        }
        return values;
    }

    /**
     * Splits and trims the comma-separated contents of a {@code {...}} set
     * literal and parses each entry as a {@code double}.
     *
     * <p>Used by {@link NumericConditionParser}.
     *
     * @param inner the text between the braces, without leading/trailing whitespace
     * @return the parsed values in encounter order
     * @throws IllegalArgumentException if {@code inner} is empty, contains an
     *         empty entry, or contains a value that is not a valid {@code double}
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
}