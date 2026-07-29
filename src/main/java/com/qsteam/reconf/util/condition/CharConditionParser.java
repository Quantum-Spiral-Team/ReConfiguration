package com.qsteam.reconf.util.condition;

import it.unimi.dsi.fastutil.chars.CharPredicate;

import java.util.regex.Matcher;

/**
 * Parses a compact textual mini-language describing character set-membership
 * checks combined with boolean operators, into a single {@link CharPredicate}
 * (fastutil's primitive {@code char} predicate) that can be evaluated against
 * a {@code char} value.
 *
 * <p>The grammar and case-sensitivity semantics are <strong>identical to
 * {@link StringConditionParser}</strong> — this class exists purely to avoid
 * the boxing/allocation overhead of {@code Predicate<String>} (a single-character
 * {@code String} per comparison) when the domain is known to be single
 * characters. The one grammar difference is quoting: entries are wrapped in
 * <em>single</em> quotes ({@code 'a'}) here, matching {@code char} literal
 * syntax, versus <em>double</em> quotes ({@code "a"}) for whole strings in
 * {@link StringConditionParser}. No interval syntax exists here (same as the
 * string parser); only set literals are supported as atomic predicates.
 *
 * <h2>Why fastutil's {@code CharPredicate}</h2>
 * {@code it.unimi.dsi.fastutil.chars.CharPredicate} implements
 * {@code Predicate<Character>} (satisfying this hierarchy's
 * {@code P extends Predicate<?>} bound) while exposing a primitive
 * {@code boolean test(char)} plus {@code and}/{@code or}/{@code negate}
 * combinators — the same shape {@link DoubleConditionParser} and
 * {@link LongConditionParser} already rely on for {@code DoublePredicate}/
 * {@code LongPredicate}. Using it here avoids boxing every tested character
 * into a {@code Character} or a length-1 {@code String}:
 * <pre>{@code
 * CharPredicate p = CharConditionParser.parse("{'a', 'b', 'c'}");
 * p.test('b'); // true, no boxing
 * }</pre>
 * The parsed set values are stored in the shared {@link Token}'s dedicated
 * {@code charValues} field (a {@code char[]}, added to {@link Token}
 * alongside the {@code double}/{@code long}/{@code String} variants already
 * used by the other subclasses), so no further change to
 * {@link AbstractConditionParser} was needed to add this parser.
 *
 * <h2>Supported syntax</h2>
 * <table border="1" cellpadding="3" summary="syntax overview">
 *   <tr><th>Pattern</th><th>Meaning</th></tr>
 *   <tr><td>{@code {'a', 'b', 'c'}}</td><td>set membership: matches if the value
 *       equals any of the listed characters</td></tr>
 *   <tr><td>{@code !X}</td><td>negation of condition {@code X}</td></tr>
 *   <tr><td>{@code X & Y}</td><td>logical AND (binds tighter than {@code |})</td></tr>
 *   <tr><td>{@code X | Y}</td><td>logical OR</td></tr>
 *   <tr><td>{@code (X)}</td><td>explicit grouping</td></tr>
 * </table>
 *
 * <p>Whitespace around operators, braces, and set entries is insignificant.
 * Each set entry <strong>must</strong> be enclosed in single quotes and must
 * resolve to <strong>exactly one character</strong> after unescaping; an
 * unquoted entry (e.g. bare {@code {a}}), an empty {@code ''}, or a quoted
 * entry longer than one character (e.g. {@code {'ab'}}) is rejected. Standard
 * {@code \}-escapes are supported inside the quotes via
 * {@link AbstractConditionParser#unescapeJava}, so a literal quote, comma, or
 * brace can be matched with {@code '\''}, or a control/Unicode character with
 * {@code '\n'} / {@code '\uFFFF'}.
 *
 * <h2>Case sensitivity</h2>
 * Matching is <strong>case-insensitive by default</strong>, exactly as in
 * {@link StringConditionParser}. Pass {@code caseSensitive = true} to
 * {@link #parse(String, boolean)} when exact casing matters. Case folding is
 * done once per set entry at parse time (via {@link Character#toLowerCase(char)})
 * and once per evaluation on the tested value — never repeatedly per entry.
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
 * // Whitelist: only 'a' and 'b' pass (case-insensitive by default)
 * CharPredicate wl = CharConditionParser.parse("{'a', 'b'}");
 * wl.test('a');  // true
 * wl.test('A');  // true  -> case-insensitive
 * wl.test('c');  // false
 *
 * // Blacklist: everything except vowels
 * CharPredicate bl = CharConditionParser.parse("!{'a', 'e', 'i', 'o', 'u'}");
 * bl.test('x'); // true
 * bl.test('e'); // false
 * }</pre>
 */
public class CharConditionParser extends AbstractConditionParser<CharPredicate> {

    private final boolean caseSensitive;

    private CharConditionParser(boolean caseSensitive) {
        this.caseSensitive = caseSensitive;
    }

    /**
     * Parses the given condition expression using <strong>case-insensitive</strong>
     * character matching and compiles it into a {@link CharPredicate}.
     *
     * @param expr the condition expression, e.g. {@code "{'a', 'b', 'c'} & !{'b'}"}
     * @return a predicate that evaluates the parsed condition against a given
     * {@code char}
     * @throws IllegalArgumentException if {@code expr} is malformed
     * @see #parse(String, boolean)
     */
    public static CharPredicate parse(String expr) {
        return parse(expr, false);
    }

    /**
     * Parses the given condition expression and compiles it into a
     * {@link CharPredicate}.
     *
     * <p>The expression is scanned exactly once; the returned predicate
     * performs no further string processing at evaluation time — only a
     * single {@link Character#toLowerCase(char)} call when case-insensitive.
     *
     * @param expr          the condition expression, e.g. {@code "{'a', 'b', 'c'} & !{'b'}"}
     * @param caseSensitive {@code true} for exact-case matching;
     *                      {@code false} (the default) for case-insensitive matching
     * @return a predicate that evaluates the parsed condition against a given
     * {@code char}
     * @throws IllegalArgumentException if {@code expr} contains an unexpected
     *                                  character, is malformed (e.g. unbalanced parentheses, a dangling
     *                                  operator, trailing garbage), contains an empty set literal, contains
     *                                  a set entry that isn't enclosed in single quotes, or contains a
     *                                  quoted entry that doesn't resolve to exactly one character
     */
    public static CharPredicate parse(String expr, boolean caseSensitive) {
        CharConditionParser parser = new CharConditionParser(caseSensitive);
        parser.tokenize(expr);
        CharPredicate result = parser.parseExpr();
        if (!parser.check(TokenType.EOF)) {
            throw new IllegalArgumentException(
                    "Unexpected trailing characters after: " + parser.peek());
        }
        return result;
    }

    // ---------- Lexer ----------

    /**
     * Scans the input string into the token list.
     * Recognizes: {@code {}}, {@code &}, {@code |}, {@code !}, {@code (},
     * {@code )} and whitespace — same as {@link StringConditionParser}. No
     * interval syntax is present.
     *
     * <p>{@link AbstractConditionParser#SET_PATTERN} locates the full quote-aware
     * {@code {...}} span, {@link #parseStringSetValues} splits it into raw,
     * still-quoted entries, and each entry is then unquoted, unescaped (via
     * {@link AbstractConditionParser#unescapeJava}), validated to be exactly one
     * character, and case-folded here if {@code caseSensitive} is {@code false}
     * — see {@link #toCharCodes}. The resulting {@code char[]} is stored in the
     * token's dedicated {@code charValues} field, mirroring how
     * {@link LongConditionParser} populates its own {@code longValues} field.
     *
     * @throws IllegalArgumentException if an unrecognized character is
     *                                  encountered, a set entry isn't enclosed in single quotes, or a
     *                                  quoted entry doesn't resolve to exactly one character
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
                char[] values = toCharCodes(ms.group(), raw);
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
     * Strips the required single-quote delimiters from each raw set entry,
     * resolves {@code \}-escape sequences via {@link AbstractConditionParser#unescapeJava},
     * and validates that exactly one character remains — then case-folds it
     * (if {@link #caseSensitive} is {@code false}) into the returned
     * {@code char[]}.
     *
     * @throws IllegalArgumentException if an entry is not enclosed in single
     *         quotes, or if the unescaped, unquoted content isn't exactly one character
     */
    private char[] toCharCodes(String setLiteralText, String[] raw) {
        char[] values = new char[raw.length];
        for (int i = 0; i < raw.length; i++) {
            String entry = raw[i];

            if (entry.startsWith("'") && entry.endsWith("'") && entry.length() >= 2) {
                String unquoted = unescapeJava(entry.substring(1, entry.length() - 1));

                if (unquoted.length() != 1) {
                    throw new IllegalArgumentException(
                            "Char entry must resolve to exactly one character, got \""
                                    + unquoted + "\" in: " + setLiteralText);
                }
                char ch = caseSensitive ? unquoted.charAt(0) : Character.toLowerCase(unquoted.charAt(0));
                values[i] = ch;
            } else {
                throw new IllegalArgumentException(
                        "Char set entries must be strictly enclosed in single quotes, e.g. {'a'}. Got: " + entry);
            }
        }
        return values;
    }

    // ---------- Predicate combinators ----------

    @Override
    protected CharPredicate and(CharPredicate a, CharPredicate b) {
        return a.and(b);
    }

    @Override
    protected CharPredicate or(CharPredicate a, CharPredicate b) {
        return a.or(b);
    }

    @Override
    protected CharPredicate negate(CharPredicate p) {
        return p.negate();
    }

    // ---------- Atom parsing ----------

    /**
     * {@code atom = set}
     *
     * <p>The only atomic construct in the char parser is a set literal
     * (same restriction as {@link StringConditionParser}).
     *
     * @throws IllegalArgumentException if the current token is not a {@link TokenType#SET}
     */
    @Override
    protected CharPredicate parseAtom() {
        Token t = advance();
        if (t.type() != TokenType.SET) {
            throw new IllegalArgumentException(
                    "Expected a set literal {'a', 'b', ...}, got: " + t);
        }
        return parseSet(t);
    }

    /**
     * Builds the predicate for a {@code {'a', 'b', ...}} set-membership token.
     *
     * <p>When the parser was created with {@code caseSensitive = false} (the
     * default), the set values were already case-folded once in {@link #tokenize}
     * at parse time; the returned predicate only needs to case-fold the
     * incoming value at evaluation time, exactly mirroring
     * {@link StringConditionParser#parseSet}.
     */
    private CharPredicate parseSet(Token t) {
        char[] values = t.charValues();
        if (caseSensitive) {
            return val -> {
                for (char v : values) {
                    if (v == val) return true;
                }
                return false;
            };
        } else {
            return val -> {
                char folded = foldCase(val);
                for (char v : values) {
                    if (v == folded) return true;
                }
                return false;
            };
        }
    }

    /**
     * Case-folds a single tested {@code char} the same way each set entry was
     * folded once at parse time in {@link #toCharCodes}, so the two sides of
     * the comparison in {@link #parseSet} use the same normalization.
     */
    private static char foldCase(char val) {
        return Character.toLowerCase(val);
    }
}