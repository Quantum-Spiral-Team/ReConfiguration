package com.qsteam.reconf.util.condition;

import java.util.function.Predicate;
import java.util.regex.Matcher;

/**
 * Parses a compact textual mini-language describing string set-membership
 * checks combined with boolean operators, into a single
 * {@link Predicate}{@code <String>} that can be evaluated against a string value.
 *
 * <p>Unlike {@link DoubleConditionParser}, no interval syntax exists here —
 * only set literals are supported as atomic predicates. This makes the parser
 * suitable for whitelist/blacklist config values such as biome names, item IDs,
 * dimension keys, etc.
 *
 * <h2>Supported syntax</h2>
 * <table border="1" cellpadding="3" summary="syntax overview">
 *   <tr><th>Pattern</th><th>Meaning</th></tr>
 *   <tr><td>{@code {"a", "b", "c"}}</td><td>set membership: matches if the value
 *       equals any of the listed strings</td></tr>
 *   <tr><td>{@code !X}</td><td>negation of condition {@code X}</td></tr>
 *   <tr><td>{@code X & Y}</td><td>logical AND (binds tighter than {@code |})</td></tr>
 *   <tr><td>{@code X | Y}</td><td>logical OR</td></tr>
 *   <tr><td>{@code (X)}</td><td>explicit grouping</td></tr>
 * </table>
 *
 * <p>Whitespace around operators and between set entries is insignificant.
 * Each set entry <strong>must</strong> be enclosed in double quotes; an
 * unquoted entry (e.g. bare {@code {forest}}) is rejected. Whitespace
 * <em>within</em> a quoted entry is preserved: {@code {"hello world"}}
 * matches the literal string {@code "hello world"}. Standard {@code \}-escapes
 * are supported inside the quotes via {@link AbstractConditionParser#unescapeJava}
 * (e.g. {@code "\""}, {@code "\n"}, {@code "\uFFFF"}).
 *
 * <h2>Case sensitivity</h2>
 * Matching is <strong>case-insensitive by default</strong>, which suits most
 * config use-cases (e.g. biome IDs). Pass {@code caseSensitive = true} to
 * {@link #parse(String, boolean)} when exact casing matters.
 *
 * <h2>Grammar</h2>
 * <pre>{@code
 * expr   = term { '|' term }
 * term   = factor { '&' factor }
 * factor = '!' factor | '(' expr ')' | atom
 * atom   = set
 * }</pre>
 *
 * <h2>Examples</h2>
 * <pre>{@code
 * // Whitelist: only forest and plains pass
 * Predicate<String> wl = StringConditionParser.parse("{\"forest\", \"plains\"}");
 * wl.test("forest");  // true
 * wl.test("desert");  // false
 *
 * // Blacklist: everything except nether and the_end
 * Predicate<String> bl = StringConditionParser.parse("!{\"nether\", \"the_end\"}");
 * bl.test("overworld"); // true
 * bl.test("nether");    // false
 *
 * // Combination: forest or plains, but never old_growth_birch_forest
 * Predicate<String> cond = StringConditionParser.parse(
 *     "{\"forest\", \"plains\"} & !{\"old_growth_birch_forest\"}");
 * }</pre>
 */
public class StringConditionParser extends AbstractConditionParser<Predicate<String>> {

    private final boolean caseSensitive;

    private StringConditionParser(boolean caseSensitive) {
        this.caseSensitive = caseSensitive;
    }

    /**
     * Parses the given condition expression using <strong>case-insensitive</strong>
     * string matching and compiles it into a {@link Predicate}{@code <String>}.
     *
     * @param expr the condition expression, e.g. {@code "{\"forest\", \"plains\"} & !{\"mushroom_fields\"}"}
     * @return a predicate that evaluates the parsed condition against a given string
     * @throws IllegalArgumentException if {@code expr} is malformed
     * @see #parse(String, boolean)
     */
    public static Predicate<String> parse(String expr) {
        return parse(expr, false);
    }

    /**
     * Parses the given condition expression and compiles it into a
     * {@link Predicate}{@code <String>}.
     *
     * <p>The expression is scanned exactly once; the returned predicate
     * performs no further string processing at evaluation time.
     *
     * @param expr          the condition expression, e.g.
     *                      {@code "{\"forest\", \"plains\"} & !{\"mushroom_fields\"}"}
     * @param caseSensitive {@code true} for exact-case matching;
     *                      {@code false} (the default) for case-insensitive matching
     * @return a predicate that evaluates the parsed condition against a given string
     * @throws IllegalArgumentException if {@code expr} contains an unexpected
     *         character, is malformed (e.g. unbalanced parentheses, a dangling
     *         operator, trailing garbage), contains an empty set literal, or
     *         contains a set entry that isn't enclosed in double quotes
     */
    public static Predicate<String> parse(String expr, boolean caseSensitive) {
        StringConditionParser parser = new StringConditionParser(caseSensitive);
        parser.tokenize(expr);
        Predicate<String> result = parser.parseExpr();
        if (!parser.check(TokenType.EOF)) {
            throw new IllegalArgumentException(
                    "Unexpected trailing characters after: " + parser.peek());
        }
        return result;
    }

    // ---------- Lexer ----------

    /**
     * Scans the input string into the token list.
     * String parsers recognize: {@code {}}, {@code &}, {@code |}, {@code !},
     * {@code (}, {@code )} and whitespace. No interval syntax is present.
     *
     * <p>{@link AbstractConditionParser#SET_PATTERN} locates the full quote-aware
     * {@code {...}} span, {@link #parseStringSetValues} splits it into raw,
     * still-quoted entries, and each entry here is validated to be enclosed
     * in double quotes, unescaped via {@link AbstractConditionParser#unescapeJava},
     * and — unless {@link #caseSensitive} — lower-cased before being stored in
     * the token's {@code stringValues} field.
     *
     * @throws IllegalArgumentException if an unrecognized character is
     *         encountered, or a set entry isn't enclosed in double quotes
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

            Matcher ms = SET_PATTERN.matcher(input);
            ms.region(i, input.length());
            if (ms.lookingAt()) {
                String inner = ms.group().substring(1, ms.group().length() - 1);
                String[] raw = parseStringSetValues(inner);

                String[] clean = new String[raw.length];
                for (int j = 0; j < raw.length; j++) {
                    String s = raw[j];
                    if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) {
                        clean[j] = unescapeJava(s.substring(1, s.length() - 1));
                    } else {
                        throw new IllegalArgumentException(
                                "String set entries must be strictly enclosed in double quotes, e.g. {\"forest\"}. Got: " + s);
                    }
                }

                String[] values = caseSensitive ? clean : toLowerCase(clean);
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

    // ---------- Predicate combinators ----------

    @Override
    protected Predicate<String> and(Predicate<String> a, Predicate<String> b) {
        return a.and(b);
    }

    @Override
    protected Predicate<String> or(Predicate<String> a, Predicate<String> b) {
        return a.or(b);
    }

    @Override
    protected Predicate<String> negate(Predicate<String> p) {
        return p.negate();
    }

    // ---------- Atom parsing ----------

    /**
     * {@code atom = set}
     *
     * <p>The only atomic construct in the string parser is a set literal.
     *
     * @throws IllegalArgumentException if the current token is not a {@link TokenType#SET}
     */
    @Override
    protected Predicate<String> parseAtom() {
        Token t = advance();
        if (t.type() != TokenType.SET) {
            throw new IllegalArgumentException(
                    "Expected a set literal {\"a\", \"b\", ...}, got: " + t);
        }
        return parseSet(t);
    }

    /**
     * Builds the predicate for a {@code {"a", "b", ...}} set-membership token.
     *
     * <p>When the parser was created with {@code caseSensitive = false} (the
     * default), both the set values and the tested string are compared in
     * lower-case, so the predicate itself always receives an already-normalized
     * array and only needs to lower-case the incoming value at evaluation time.
     */
    private Predicate<String> parseSet(Token t) {
        String[] values = t.stringValues();
        if (caseSensitive) {
            return val -> {
                for (String v : values) {
                    if (v.equals(val)) return true;
                }
                return false;
            };
        } else {
            return val -> {
                String lower = val.toLowerCase();
                for (String v : values) {
                    if (v.equals(lower)) return true;
                }
                return false;
            };
        }
    }

    // ---------- Utility ----------

    /**
     * Returns a new array containing each element of {@code values} converted to lower-case.
     */
    private static String[] toLowerCase(String[] values) {
        String[] result = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = values[i].toLowerCase();
        }
        return result;
    }
}