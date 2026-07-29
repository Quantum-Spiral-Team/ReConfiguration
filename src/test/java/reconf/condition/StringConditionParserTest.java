package reconf.condition;

import com.qsteam.reconf.util.condition.StringConditionParser;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for {@link StringConditionParser}.
 *
 * <p>Covers single/multi-value sets, case sensitivity, logical operators,
 * escape sequences, quoted entries with embedded whitespace/commas, quoting
 * rules, whitespace handling, and extensive error cases including malformed
 * input, unquoted entries, mismatched quotes, and truncated escape sequences.
 */
class StringConditionParserTest {

    // ---------- Basic set matching (case-insensitive, the default) ----------

    @Nested
    class BasicSetMatchingTests {

        @Test
        void singleValueSet() {
            Predicate<String> p = StringConditionParser.parse("{\"forest\"}");
            assertTrue(p.test("forest"));
            assertTrue(p.test("FOREST"));
            assertTrue(p.test("FoReSt"));
            assertFalse(p.test("plains"));
        }

        @ParameterizedTest
        @CsvSource({
                "forest,  true",
                "FOREST,  true",
                "plains,  true",
                "PLAINS,  true",
                "desert,  false",
                "DESERT,  false"
        })
        void multiValueSetCaseInsensitive(String value, boolean expected) {
            Predicate<String> p = StringConditionParser.parse("{\"forest\", \"plains\", \"mountain\"}");
            assertEquals(expected, p.test(value));
        }

        @Test
        void setValuesWithSpaces() {
            Predicate<String> p = StringConditionParser.parse("{\"old growth forest\", \"mushroom island\"}");
            assertTrue(p.test("old growth forest"));
            assertTrue(p.test("OLD GROWTH FOREST"));
            assertTrue(p.test("mushroom island"));
            assertFalse(p.test("old growth"));
            assertFalse(p.test("forest"));
        }

        @Test
        void setValuesWithCommas() {
            // Commas inside quoted strings are preserved, not treated as separators
            Predicate<String> p = StringConditionParser.parse("{\"apple, orange\", \"banana\"}");
            assertTrue(p.test("apple, orange"));
            assertTrue(p.test("APPLE, ORANGE"));
            assertTrue(p.test("banana"));
            assertFalse(p.test("apple"));
            assertFalse(p.test("orange"));
        }

        @Test
        void duplicateValuesInSet() {
            Predicate<String> p = StringConditionParser.parse("{\"a\", \"a\", \"b\"}");
            assertTrue(p.test("a"));
            assertTrue(p.test("b"));
            assertFalse(p.test("c"));
        }
    }

    @Nested
    class CaseSensitivityTests {

        @Test
        void caseSensitiveMatchingExactOnly() {
            Predicate<String> p = StringConditionParser.parse("{\"forest\", \"Plains\"}", true);
            assertTrue(p.test("forest"));
            assertFalse(p.test("FOREST"));
            assertTrue(p.test("Plains"));
            assertFalse(p.test("plains"));
            assertFalse(p.test("PLAINS"));
        }

        @Test
        void caseSensitiveIgnoresUnrelatedCasing() {
            Predicate<String> p = StringConditionParser.parse("{\"abc\"}", true);
            assertTrue(p.test("abc"));
            assertFalse(p.test("Abc"));
            assertFalse(p.test("ABC"));
        }

        @Test
        void defaultIsCaseInsensitive() {
            Predicate<String> p1 = StringConditionParser.parse("{\"test\"}");
            Predicate<String> p2 = StringConditionParser.parse("{\"test\"}", false);
            for (String val : new String[]{"test", "TEST", "Test", "tEsT"}) {
                assertEquals(p1.test(val), p2.test(val));
            }
        }

        @Test
        void caseSensitiveParamWorks() {
            Predicate<String> sensitive = StringConditionParser.parse("{\"Test\"}", true);
            assertTrue(sensitive.test("Test"));
            assertFalse(sensitive.test("test"));
            assertFalse(sensitive.test("TEST"));
        }
    }

    @Nested
    class EscapeSequenceTests {

        @Test
        void escapedDoubleQuote() {
            Predicate<String> p = StringConditionParser.parse("{\"\\\"hello\\\"\"}");
            assertTrue(p.test("\"hello\""));
            assertFalse(p.test("hello"));
        }

        @Test
        void escapedBackslash() {
            Predicate<String> p = StringConditionParser.parse("{\"back\\\\slash\"}");
            assertTrue(p.test("back\\slash"));
            assertFalse(p.test("backslash"));
        }

        @Test
        void newlineEscape() {
            Predicate<String> p = StringConditionParser.parse("{\"hello\\nworld\"}");
            assertTrue(p.test("hello\nworld"));
            assertFalse(p.test("hello world"));
            assertFalse(p.test("helloworld"));
        }

        @Test
        void tabEscape() {
            Predicate<String> p = StringConditionParser.parse("{\"hello\\tworld\"}");
            assertTrue(p.test("hello\tworld"));
            assertFalse(p.test("hello world"));
        }

        @Test
        void carriageReturnEscape() {
            Predicate<String> p = StringConditionParser.parse("{\"hello\\rworld\"}");
            assertTrue(p.test("hello\rworld"));
            assertFalse(p.test("helloworld"));
        }

        @Test
        void unicodeEscape() {
            Predicate<String> p = StringConditionParser.parse("{\"\u0041BC\"}");  // ABC
            assertTrue(p.test("ABC"));
            assertTrue(p.test("abc"));  // case-insensitive by default
            assertFalse(p.test("XYZ"));
        }

        @Test
        void unicodeEscapeCyrillic() {
            Predicate<String> p = StringConditionParser.parse("{\"\u0410\u0411\u0412\"}");  // АБВ
            assertTrue(p.test("АБВ"));
            assertTrue(p.test("абв"));  // case-insensitive
            assertFalse(p.test("АБ"));
        }

        @Test
        void multipleEscapesInSingleEntry() {
            Predicate<String> p = StringConditionParser.parse("{\"\\\"line1\\nline2\\t\\u0041\\\"\"}");
            assertTrue(p.test("\"line1\nline2\tA\""));
            assertFalse(p.test("line1 line2 A"));
        }

        @Test
        void multipleEscapedEntriesInSet() {
            Predicate<String> p = StringConditionParser.parse("{\"\\\"quoted\\\"\", \"path\\\\to\\\\file\"}");
            assertTrue(p.test("\"quoted\""));
            assertTrue(p.test("path\\to\\file"));
            assertFalse(p.test("quoted"));
        }
    }

    @Nested
    class NegationTests {

        @Test
        void negationInvertsSet() {
            Predicate<String> p = StringConditionParser.parse("!{\"nether\", \"the_end\"}");
            assertTrue(p.test("overworld"));
            assertTrue(p.test("forest"));
            assertFalse(p.test("nether"));
            assertFalse(p.test("the_end"));
        }

        @Test
        void doubleNegationCancelsOut() {
            Predicate<String> p = StringConditionParser.parse("!!{\"forest\"}");
            assertTrue(p.test("forest"));
            assertFalse(p.test("plains"));
        }

        @Test
        void tripleNegation() {
            Predicate<String> p = StringConditionParser.parse("!!!{\"forest\"}");
            assertFalse(p.test("forest"));
            assertTrue(p.test("plains"));
        }
    }

    @Nested
    class LogicalOperatorsPrecedenceTests {

        @Test
        void andRequiresBothSides() {
            Predicate<String> p = StringConditionParser.parse("{\"forest\", \"plains\"} & !{\"dense_forest\"}");
            assertTrue(p.test("forest"));
            assertTrue(p.test("plains"));
            assertFalse(p.test("dense_forest"));
            assertFalse(p.test("desert"));
        }

        @Test
        void orMatchesEitherSide() {
            Predicate<String> p = StringConditionParser.parse("{\"desert\"} | {\"ocean\"}");
            assertTrue(p.test("desert"));
            assertTrue(p.test("ocean"));
            assertFalse(p.test("forest"));
        }

        @Test
        void andHasHigherPrecedenceThanOr() {
            // {a} | {b} & {c} should parse as {a} | ({b} & {c})
            // Since no value can be both b and c simultaneously, the AND side is always false.
            // So the result is equivalent to {a}.
            Predicate<String> p = StringConditionParser.parse("{\"apple\"} | {\"banana\"} & {\"cherry\"}");
            assertTrue(p.test("apple"));
            assertFalse(p.test("banana"));
            assertFalse(p.test("cherry"));
        }

        @Test
        void parenthesesOverridePrecedence() {
            // ({a} | {b}) & {a} should match only 'a' (both sides true)
            Predicate<String> p = StringConditionParser.parse("({\"apple\"} | {\"banana\"}) & {\"apple\"}");
            assertTrue(p.test("apple"));
            assertFalse(p.test("banana"));
        }

        @Test
        void complexCombination() {
            // ({forest, plains} | {mushroom}) & !{old_growth}
            // Matches any of forest/plains/mushroom, but not old_growth
            Predicate<String> p = StringConditionParser.parse(
                    "({\"forest\", \"plains\"} | {\"mushroom\"}) & !{\"old_growth\"}");
            assertTrue(p.test("forest"));
            assertTrue(p.test("plains"));
            assertTrue(p.test("mushroom"));
            assertFalse(p.test("old_growth"));
            assertFalse(p.test("desert"));
        }

        @Test
        void multipleOrChain() {
            Predicate<String> p = StringConditionParser.parse(
                    "{\"a\"} | {\"b\"} | {\"c\"} | {\"d\"}");
            assertTrue(p.test("a"));
            assertTrue(p.test("b"));
            assertTrue(p.test("c"));
            assertTrue(p.test("d"));
            assertFalse(p.test("e"));
        }

        @Test
        void multipleAndChain() {
            Predicate<String> p = StringConditionParser.parse(
                    "!{\"x\"} & !{\"y\"} & !{\"z\"}");
            assertTrue(p.test("a"));
            assertFalse(p.test("x"));
            assertFalse(p.test("y"));
            assertFalse(p.test("z"));
        }
    }

    @Nested
    class Whitelist_BlacklistPatterns {

        @Test
        void whitelistPattern() {
            // Only allow specific dimensions
            Predicate<String> whitelist = StringConditionParser.parse(
                    "{\"minecraft:overworld\", \"minecraft:the_nether\", \"minecraft:the_end\"}");
            assertTrue(whitelist.test("minecraft:overworld"));
            assertTrue(whitelist.test("MINECRAFT:OVERWORLD"));
            assertFalse(whitelist.test("custom:dimension"));
        }

        @Test
        void blacklistPattern() {
            // Reject specific values
            Predicate<String> blacklist = StringConditionParser.parse(
                    "!{\"minecraft:the_nether\", \"minecraft:the_end\"}");
            assertTrue(blacklist.test("minecraft:overworld"));
            assertTrue(blacklist.test("custom:dimension"));
            assertFalse(blacklist.test("minecraft:the_nether"));
            assertFalse(blacklist.test("minecraft:the_end"));
        }

        @Test
        void biomeFilterExample() {
            // Allow forest biomes except dense variants
            Predicate<String> p = StringConditionParser.parse(
                    "{\"forest\", \"birch_forest\", \"dark_forest\"} & !{\"dark_forest\"}");
            assertTrue(p.test("forest"));
            assertTrue(p.test("birch_forest"));
            assertFalse(p.test("dark_forest"));
            assertFalse(p.test("plains"));
        }

        @Test
        void modIDFilterExample() {
            // Accept mods except certain ones
            Predicate<String> p = StringConditionParser.parse(
                    "!{\"minecraft\", \"modular\", \"problematic_mod\"}");
            assertTrue(p.test("forestry"));
            assertTrue(p.test("buildercraft"));
            assertFalse(p.test("minecraft"));
            assertFalse(p.test("problematic_mod"));
        }

        @Test
        void dimensionWhitelistExample() {
            // Only specific dimensions are allowed
            Predicate<String> p = StringConditionParser.parse(
                    "{\"minecraft:overworld\", \"minecraft:the_nether\"} | {\"custom:custom_dimension\"}");
            assertTrue(p.test("minecraft:overworld"));
            assertTrue(p.test("MINECRAFT:OVERWORLD"));
            assertTrue(p.test("custom:custom_dimension"));
            assertFalse(p.test("minecraft:the_end"));
        }
    }

    @Nested
    class WhitespaceHandlingTests {

        @Test
        void whitespaceAroundOperatorsIsIgnored() {
            Predicate<String> compact = StringConditionParser.parse("{\"a\",\"b\"}|!{\"c\"}");
            Predicate<String> spaced = StringConditionParser.parse(
                    "  {\"a\" , \"b\"}  |  ! {\"c\"}  ");
            for (String val : new String[]{"a", "b", "c", "d"}) {
                assertEquals(compact.test(val), spaced.test(val),
                        "Mismatch at value=" + val);
            }
        }

        @Test
        void whitespaceWithinSetValuesIsPreserved() {
            Predicate<String> p = StringConditionParser.parse("{\"hello world\", \"foo  bar\"}");
            assertTrue(p.test("hello world"));
            assertTrue(p.test("HELLO WORLD"));
            assertTrue(p.test("foo  bar"));
            assertFalse(p.test("hello"));  // Space is part of the value
            assertFalse(p.test("world"));
        }

        @Test
        void leadingAndTrailingWhitespaceInValuesIsTrimmed() {
            Predicate<String> p = StringConditionParser.parse("{ \"forest\" , \"plains\" }");
            assertTrue(p.test("forest"));
            assertTrue(p.test("plains"));
            assertFalse(p.test(" forest"));  // Leading space not in the set
            assertFalse(p.test("forest "));   // Trailing space not in the set
        }

        @Test
        void whitespaceAroundSetBracesAndOperatorsVariations() {
            Predicate<String> p = StringConditionParser.parse(
                    "  {  \"a\"  }  &  !  {  \"b\"  }  |  {  \"c\"  }  ");
            assertTrue(p.test("a"));
            assertTrue(p.test("c"));
            assertFalse(p.test("b"));
        }

        @Test
        void noWhitespaceCompact() {
            Predicate<String> p = StringConditionParser.parse("{\"a\",\"b\"}&!{\"c\"}|{\"d\"}");
            assertTrue(p.test("a"));
            assertTrue(p.test("b"));
            assertTrue(p.test("d"));
            assertFalse(p.test("c"));
        }
    }

    @Nested
    class QuotingAndFormatTests {

        @Test
        void entryMustBeDoubleQuoted() {
            assertThrows(IllegalArgumentException.class,
                    () -> StringConditionParser.parse("{forest}"));
        }

        @Test
        void entryMustNotBeSingleQuoted() {
            assertThrows(IllegalArgumentException.class,
                    () -> StringConditionParser.parse("{'forest'}"));
        }

        @Test
        void unmatchedDoubleQuoteAtEnd() {
            assertThrows(IllegalArgumentException.class,
                    () -> StringConditionParser.parse("{\"forest"));
        }

        @Test
        void unmatchedDoubleQuoteInMiddle() {
            assertThrows(IllegalArgumentException.class,
                    () -> StringConditionParser.parse("{\"forest\", plains}"));
        }

        @Test
        void trailingBackslash() {
            // Trailing backslash at end of quoted entry (incomplete escape)
            assertThrows(IllegalArgumentException.class,
                    () -> StringConditionParser.parse("{\"forest\\\"}"));
        }
    }

    @Nested
    class ErrorHandlingTests {

        @ParameterizedTest
        @ValueSource(strings = {
                "",                                 // empty expression
                "{}",                               // empty set
                "{,}",                              // only comma
                "{\"a\",\"b\",}",                   // trailing comma
                "{\"a\", , \"b\"}",                 // double comma
                "({\"a\"}",                         // unclosed paren
                "{\"a\"",                           // unclosed quote
                "{\"a\"} ^ {\"b\"}",                // unknown operator
                "{\"a\"} foo",                      // trailing garbage
                "{\"a\"} &",                        // dangling AND
                "& {\"a\"}",                        // dangling operator at start
                "{\"a\"} | | {\"b\"}",              // double pipe
                "()",                               // empty parens
                "{forest}",                         // unquoted
                "{'forest'}",                       // single quotes instead of double
                "| {\"a\"}",                        // leading OR
                "{\"a\"} | ",                       // trailing OR
        })
        void invalidExpressionsThrowException(String expression) {
            assertThrows(IllegalArgumentException.class,
                    () -> StringConditionParser.parse(expression),
                    "Should have thrown for: " + expression);
        }
    }
}