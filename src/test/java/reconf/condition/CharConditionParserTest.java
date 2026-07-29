package reconf.condition;

import com.qsteam.reconf.util.condition.CharConditionParser;
import it.unimi.dsi.fastutil.chars.CharPredicate;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Comprehensive unit tests for {@link CharConditionParser}.
 *
 * <p>Covers single/multi-char sets, case sensitivity, logical operators,
 * escape sequences, Unicode, quoting rules, whitespace handling, and
 * extensive error cases including malformed input, unquoted entries,
 * multi-character entries, and truncated/mismatched quotes.
 */
class CharConditionParserTest {

    @Nested
    class BasicAndCaseSensitivityTests {

        @Test
        void singleValueSet() {
            CharPredicate p = CharConditionParser.parse("{'f'}");
            assertTrue(p.test('f'));
            assertTrue(p.test('F'));  // case-insensitive by default
            assertFalse(p.test('m'));
            assertFalse(p.test('M'));
        }

        @ParameterizedTest
        @CsvSource({
                "c, true",
                "o, true",
                "a, true",
                "C, true",
                "O, true",
                "A, true",
                "B, false",
                "5, false",
                "?, false",
        })
        void multiValueSetCaseInsensitive(char value, boolean expected) {
            CharPredicate p = CharConditionParser.parse("{'c', 'o', 'a'}");
            assertEquals(expected, p.test(value));
        }

        @Test
        void caseSensitiveMatchingExactOnly() {
            CharPredicate p = CharConditionParser.parse("{'a', 'z'}", true);
            assertTrue(p.test('a'));
            assertFalse(p.test('A'));
            assertTrue(p.test('z'));
            assertFalse(p.test('Z'));
            assertFalse(p.test('?'));
        }

        @Test
        void explicitCaseInsensitiveParam() {
            CharPredicate p1 = CharConditionParser.parse("{'t'}");
            CharPredicate p2 = CharConditionParser.parse("{'t'}", false);
            for (char val : new char[]{'t', 'T', 'x'}) {
                assertEquals(p1.test(val), p2.test(val));
            }
        }

        @Test
        void duplicateValuesInSet() {
            CharPredicate p = CharConditionParser.parse("{'a', 'a', 'b'}");
            assertTrue(p.test('a'));
            assertTrue(p.test('b'));
            assertFalse(p.test('c'));
        }
    }

    @Nested
    class EscapeSequenceTests {

        @Test
        void escapedSingleQuote() {
            CharPredicate p = CharConditionParser.parse("{'\\''}");
            assertTrue(p.test('\''));
            assertFalse(p.test('a'));
        }

        @Test
        void escapedBackslash() {
            CharPredicate p = CharConditionParser.parse("{'\\\\'}");
            assertTrue(p.test('\\'));
            assertFalse(p.test('a'));
        }

        @Test
        void newlineEscape() {
            CharPredicate p = CharConditionParser.parse("{'\\n'}");
            assertTrue(p.test('\n'));
            assertFalse(p.test('n'));
        }

        @Test
        void tabEscape() {
            CharPredicate p = CharConditionParser.parse("{'\\t'}");
            assertTrue(p.test('\t'));
            assertFalse(p.test('t'));
        }

        @Test
        void carriageReturnEscape() {
            CharPredicate p = CharConditionParser.parse("{'\\r'}");
            assertTrue(p.test('\r'));
            assertFalse(p.test('r'));
        }

        @Test
        void unicodeEscape() {
            CharPredicate p = CharConditionParser.parse("{'\u0041'}", true);  // 'A'
            assertTrue(p.test('A'));
            assertFalse(p.test('a'));  // case-sensitive here because it's a literal
        }

        @Test
        void unicodeEscapeCyrillic() {
            CharPredicate p = CharConditionParser.parse("{'\\u0410'}");  // 'А' (Cyrillic)
            assertTrue(p.test('А'));
            assertFalse(p.test('a'));
        }

        @Test
        void multipleEscapedCharsInSet() {
            CharPredicate p = CharConditionParser.parse("{'\\n', '\\t', '\\'', '\\\\'}");
            assertTrue(p.test('\n'));
            assertTrue(p.test('\t'));
            assertTrue(p.test('\''));
            assertTrue(p.test('\\'));
            assertFalse(p.test('a'));
        }
    }

    @Nested
    class UnicodeAndSpecialCharsTests {

        @Test
        void cyrillicSupport() {
            CharPredicate ci = CharConditionParser.parse("{'а', 'б', 'ё'}");
            assertTrue(ci.test('а'));
            assertTrue(ci.test('А'));  // case-insensitive
            assertTrue(ci.test('Ё'));
            assertFalse(ci.test('в'));

            CharPredicate cs = CharConditionParser.parse("{'а', 'б', 'ё'}", true);
            assertTrue(cs.test('а'));
            assertFalse(cs.test('А'));
        }

        @Test
        void greekLetters() {
            CharPredicate p = CharConditionParser.parse("{'α', 'β', 'γ'}");
            assertTrue(p.test('α'));
            assertTrue(p.test('β'));
            assertTrue(p.test('γ'));
            assertFalse(p.test('δ'));
        }

        @Test
        void grammarOperatorsAsValues() {
            CharPredicate p = CharConditionParser.parse("{'!', '|', '&', '(', ')', '{', '}'}");
            assertTrue(p.test('!'));
            assertTrue(p.test('|'));
            assertTrue(p.test('&'));
            assertTrue(p.test('('));
            assertTrue(p.test(')'));
            assertTrue(p.test('{'));
            assertTrue(p.test('}'));
            assertFalse(p.test('a'));
        }

        @Test
        void digitsAndPunctuation() {
            CharPredicate p = CharConditionParser.parse("{'0', '9', '?', '.', '#', ','}");
            assertTrue(p.test('0'));
            assertTrue(p.test('9'));
            assertTrue(p.test('?'));
            assertTrue(p.test('.'));
            assertTrue(p.test('#'));
            assertTrue(p.test(','));
            assertFalse(p.test('1'));
        }

        @Test
        void boundaryCharValues() {
            CharPredicate p = CharConditionParser.parse("{'\u0000', '\uFFFF'}");
            assertTrue(p.test('\u0000'));
            assertTrue(p.test('\uFFFF'));
            assertFalse(p.test('a'));
        }

        @Test
        void whitespaceCharacters() {
            CharPredicate p = CharConditionParser.parse("{' ', '\\t', '\\n'}");
            assertTrue(p.test(' '));
            assertTrue(p.test('\t'));
            assertTrue(p.test('\n'));
            assertFalse(p.test('a'));
        }
    }

    @Nested
    class LogicalOperatorsTests {

        @Test
        void negationInvertsSet() {
            CharPredicate p = CharConditionParser.parse("!{'n', 'e'}");
            assertTrue(p.test('o'));
            assertTrue(p.test('f'));
            assertFalse(p.test('n'));
            assertFalse(p.test('e'));
        }

        @Test
        void doubleNegationCancelsOut() {
            CharPredicate p = CharConditionParser.parse("!!{'f'}");
            assertTrue(p.test('f'));
            assertFalse(p.test('p'));
        }

        @Test
        void tripleNegation() {
            CharPredicate p = CharConditionParser.parse("!!!{'f'}");
            assertFalse(p.test('f'));
            assertTrue(p.test('p'));
        }

        @Test
        void andRequiresBothSides() {
            CharPredicate p = CharConditionParser.parse("{'f', 'p'} & !{'d'}");
            assertTrue(p.test('f'));
            assertTrue(p.test('p'));
            assertFalse(p.test('d'));
            assertFalse(p.test('x'));
        }

        @Test
        void orMatchesEitherSide() {
            CharPredicate p = CharConditionParser.parse("{'d'} | {'o'}");
            assertTrue(p.test('d'));
            assertTrue(p.test('o'));
            assertFalse(p.test('f'));
        }

        @Test
        void andHasHigherPrecedenceThanOr() {
            // {a} | {b} & {c} -> {a} | ({b} & {c})
            CharPredicate p = CharConditionParser.parse("{'a'} | {'b'} & {'c'}");
            assertTrue(p.test('a'));
            assertFalse(p.test('b'));
            assertFalse(p.test('c'));
        }

        @Test
        void parenthesesOverridePrecedence() {
            CharPredicate p = CharConditionParser.parse("({'a'} | {'b'}) & {'a'}");
            assertTrue(p.test('a'));
            assertFalse(p.test('b'));
        }

        @Test
        void complexCombination() {
            CharPredicate p = CharConditionParser.parse("({'f', 'p'} | {'m'}) & !{'g'}");
            assertTrue(p.test('f'));
            assertTrue(p.test('p'));
            assertTrue(p.test('m'));
            assertFalse(p.test('g'));
            assertFalse(p.test('x'));
        }

        @Test
        void deeplyNestedParentheses() {
            CharPredicate p = CharConditionParser.parse("((({'a'} | {'b'}) & !{'c'}) | {'d'})");
            assertTrue(p.test('a'));
            assertTrue(p.test('b'));
            assertFalse(p.test('c'));
            assertTrue(p.test('d'));
        }

        @Test
        void multipleOrChain() {
            CharPredicate p = CharConditionParser.parse("{'a'} | {'b'} | {'c'} | {'d'}");
            assertTrue(p.test('a'));
            assertTrue(p.test('b'));
            assertTrue(p.test('c'));
            assertTrue(p.test('d'));
            assertFalse(p.test('e'));
        }

        @Test
        void multipleAndChain() {
            CharPredicate p = CharConditionParser.parse("!{'x'} & !{'y'} & !{'z'}");
            assertTrue(p.test('a'));
            assertFalse(p.test('x'));
            assertFalse(p.test('y'));
            assertFalse(p.test('z'));
        }
    }

    @Nested
    class WhitespaceAndFormattingTests {

        @Test
        void whitespaceAroundOperatorsIsIgnored() {
            CharPredicate compact = CharConditionParser.parse("{'a','b'}|!{'c'}");
            CharPredicate spaced = CharConditionParser.parse("  {'a' , 'b'}  |  ! {'c'}  ");
            for (char val : new char[]{'a', 'b', 'c', 'd'}) {
                assertEquals(compact.test(val), spaced.test(val), "Mismatch at char=" + val);
            }
        }

        @Test
        void whitespaceAroundElementsIsTrimmed() {
            CharPredicate p = CharConditionParser.parse("{ 'f' , 'p' }");
            assertTrue(p.test('f'));
            assertTrue(p.test('p'));
            assertFalse(p.test(' '));
        }

        @Test
        void whitespaceAroundSetBracesAndOperatorsVariations() {
            CharPredicate p = CharConditionParser.parse(
                    "  {  'a'  }  &  !  {  'b'  }  |  {  'c'  }  ");
            assertTrue(p.test('a'));
            assertTrue(p.test('c'));
            assertFalse(p.test('b'));
        }

        @Test
        void noWhitespaceCompact() {
            CharPredicate p = CharConditionParser.parse("{'a','b'}&!{'c'}|{'d'}");
            assertTrue(p.test('a'));
            assertTrue(p.test('b'));
            assertTrue(p.test('d'));
            assertFalse(p.test('c'));
        }
    }

    @Nested
    class QuotingAndFormatTests {

        @Test
        void entryMustBeSingleQuoted() {
            assertThrows(IllegalArgumentException.class, () -> CharConditionParser.parse("{a}"));
        }

        @Test
        void entryMustNotBeDoubleQuoted() {
            assertThrows(IllegalArgumentException.class, () -> CharConditionParser.parse("{\"a\"}"));
        }

        @Test
        void entryCannotBeUnquoted() {
            assertThrows(IllegalArgumentException.class, () -> CharConditionParser.parse("{abc}"));
        }

        @Test
        void emptyQuotesIsInvalid() {
            assertThrows(IllegalArgumentException.class, () -> CharConditionParser.parse("{''}"));
        }

        @Test
        void singleCharAfterEscapeIsValid() {
            CharPredicate p = CharConditionParser.parse("{'\\n'}");  // Newline
            assertTrue(p.test('\n'));
        }

        @Test
        void multiCharAfterUnescapingIsInvalid() {
            // 'ab' is two characters, should throw
            assertThrows(IllegalArgumentException.class, () -> CharConditionParser.parse("{'ab'}"));
        }

        @Test
        void escapedMultiCharIsInvalid() {
            // Even with escaping, only one character allowed
            assertThrows(IllegalArgumentException.class, () -> CharConditionParser.parse("{'a\\nb'}"));
        }

        @Test
        void unmatchedSingleQuoteAtEnd() {
            assertThrows(IllegalArgumentException.class, () -> CharConditionParser.parse("{'a"));
        }

        @Test
        void unmatchedSingleQuoteInMiddle() {
            assertThrows(IllegalArgumentException.class, () -> CharConditionParser.parse("{'a', b}"));
        }

        @Test
        void trailingBackslash() {
            // Trailing backslash at end of quoted entry
            assertThrows(IllegalArgumentException.class, () -> CharConditionParser.parse("{'a\"}"));
        }
    }

    @Nested
    class ErrorHandlingTests {

        @ParameterizedTest
        @ValueSource(strings = {
                "",                          // empty expression
                "{}",                        // empty set
                "{,}",                       // only comma
                "{a, b,}",                   // unquoted with trailing comma
                "{a, ,b}",                   // unquoted with double comma
                "({'a'}",                    // unclosed paren
                "{'a'",                      // unclosed quote
                "{'a'} ^ {'b'}",             // unknown operator
                "{'a'} foo",                 // trailing garbage
                "{'a'} &",                   // dangling AND
                "& {'a'}",                   // dangling operator at start
                "{'a'} | | {'b'}",           // double pipe
                "()",                        // empty parens
                "{\"a\"}",                   // double quotes instead of single
                "{a}",                       // unquoted letter
                "{'ab'}",                    // multi-char
                "{''}",                      // empty quoted
                "| {'a'}",                   // leading OR
                "{'a'} | ",                  // trailing OR
        })
        void invalidExpressionsThrowException(String expression) {
            assertThrows(IllegalArgumentException.class,
                    () -> CharConditionParser.parse(expression),
                    "Should have thrown for: " + expression);
        }
    }

    @Nested
    class RealWorldScenarios {

        @Test
        void vowelFilter() {
            CharPredicate p = CharConditionParser.parse("{'a', 'e', 'i', 'o', 'u', 'y'} & !{'y'}");
            assertTrue(p.test('a'));
            assertTrue(p.test('E'));
            assertFalse(p.test('y'));
            assertFalse(p.test('b'));
        }

        @Test
        void hexDigitFilter() {
            CharPredicate p = CharConditionParser.parse(
                    "{'a', 'b', 'c', 'd', 'e', 'f', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9'}");
            assertTrue(p.test('a'));
            assertTrue(p.test('F'));
            assertTrue(p.test('5'));
            assertFalse(p.test('g'));
            assertFalse(p.test('Z'));
        }
    }
}