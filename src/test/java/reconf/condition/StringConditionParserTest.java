package reconf.condition;

import com.qsteam.reconf.util.condition.StringConditionParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

class StringConditionParserTest {

    // ---------- Basic set matching (case-insensitive, the default) ----------

    @Test
    void singleValueSet() {
        Predicate<String> p = StringConditionParser.parse("{forest}");
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
        Predicate<String> p = StringConditionParser.parse("{forest, plains, mountain}");
        assertEquals(expected, p.test(value));
    }

    @Test
    void setValuesWithSpaces() {
        Predicate<String> p = StringConditionParser.parse("{old growth forest, mushroom island}");
        assertTrue(p.test("old growth forest"));
        assertTrue(p.test("OLD GROWTH FOREST"));
        assertTrue(p.test("mushroom island"));
        assertFalse(p.test("old growth"));
        assertFalse(p.test("forest"));
    }

    // ---------- Case sensitivity ----------

    @Test
    void caseSensitiveMatchingExactOnly() {
        Predicate<String> p = StringConditionParser.parse("{forest, Plains}", true);
        assertTrue(p.test("forest"));
        assertFalse(p.test("FOREST"));
        assertTrue(p.test("Plains"));
        assertFalse(p.test("plains"));
        assertFalse(p.test("PLAINS"));
    }

    @Test
    void caseSensitiveIgnoresUnrelatedCasing() {
        Predicate<String> p = StringConditionParser.parse("{abc}", true);
        assertTrue(p.test("abc"));
        assertFalse(p.test("Abc"));
        assertFalse(p.test("ABC"));
    }

    // ---------- Negation ----------

    @Test
    void negationInvertsSet() {
        Predicate<String> p = StringConditionParser.parse("!{nether, the_end}");
        assertTrue(p.test("overworld"));
        assertTrue(p.test("forest"));
        assertFalse(p.test("nether"));
        assertFalse(p.test("the_end"));
    }

    @Test
    void doubleNegationCancelsOut() {
        Predicate<String> p = StringConditionParser.parse("!!{forest}");
        assertTrue(p.test("forest"));
        assertFalse(p.test("plains"));
    }

    // ---------- AND / OR / precedence ----------

    @Test
    void andRequiresBothSides() {
        Predicate<String> p = StringConditionParser.parse("{forest, plains} & !{dense_forest}");
        assertTrue(p.test("forest"));
        assertTrue(p.test("plains"));
        assertFalse(p.test("dense_forest"));
        assertFalse(p.test("desert"));
    }

    @Test
    void orMatchesEitherSide() {
        Predicate<String> p = StringConditionParser.parse("{desert} | {ocean}");
        assertTrue(p.test("desert"));
        assertTrue(p.test("ocean"));
        assertFalse(p.test("forest"));
    }

    @Test
    void andHasHigherPrecedenceThanOr() {
        // {a} | {b} & {c} should parse as {a} | ({b} & {c})
        // Since no value can be both b and c simultaneously, the AND side is always false.
        // So the result is equivalent to {a}.
        Predicate<String> p = StringConditionParser.parse("{apple} | {banana} & {cherry}");
        assertTrue(p.test("apple"));
        assertFalse(p.test("banana"));
        assertFalse(p.test("cherry"));
    }

    @Test
    void parenthesesOverridePrecedence() {
        // ({a} | {b}) & {a} should match only 'a' (both sides true)
        Predicate<String> p = StringConditionParser.parse("({apple} | {banana}) & {apple}");
        assertTrue(p.test("apple"));
        assertFalse(p.test("banana"));
    }

    @Test
    void complexCombination() {
        // ({forest, plains} | {mushroom}) & !{old_growth}
        // Matches any of forest/plains/mushroom, but not old_growth
        Predicate<String> p = StringConditionParser.parse(
                "({forest, plains} | {mushroom}) & !{old_growth}");
        assertTrue(p.test("forest"));
        assertTrue(p.test("plains"));
        assertTrue(p.test("mushroom"));
        assertFalse(p.test("old_growth"));
        assertFalse(p.test("desert"));
    }

    // ---------- Whitelist/blacklist patterns ----------

    @Test
    void whitelistPattern() {
        // Only allow specific dimensions
        Predicate<String> whitelist = StringConditionParser.parse(
                "{minecraft:overworld, minecraft:the_nether, minecraft:the_end}");
        assertTrue(whitelist.test("minecraft:overworld"));
        assertTrue(whitelist.test("MINECRAFT:OVERWORLD"));
        assertFalse(whitelist.test("custom:dimension"));
    }

    @Test
    void blacklistPattern() {
        // Reject specific values
        Predicate<String> blacklist = StringConditionParser.parse(
                "!{minecraft:the_nether, minecraft:the_end}");
        assertTrue(blacklist.test("minecraft:overworld"));
        assertTrue(blacklist.test("custom:dimension"));
        assertFalse(blacklist.test("minecraft:the_nether"));
        assertFalse(blacklist.test("minecraft:the_end"));
    }

    // ---------- Whitespace tolerance ----------

    @Test
    void whitespaceAroundOperatorsIsIgnored() {
        Predicate<String> compact = StringConditionParser.parse("{a,b}|!{c}");
        Predicate<String> spaced = StringConditionParser.parse(
                "  {a , b}  |  ! {c}  ");
        for (String val : new String[]{"a", "b", "c", "d"}) {
            assertEquals(compact.test(val), spaced.test(val),
                    "Mismatch at value=" + val);
        }
    }

    @Test
    void whitespaceWithinSetValuesIsPreserved() {
        Predicate<String> p = StringConditionParser.parse("{hello world, foo  bar}");
        assertTrue(p.test("hello world"));
        assertTrue(p.test("HELLO WORLD"));
        assertTrue(p.test("foo  bar"));
        assertFalse(p.test("hello")); // Space is part of the value
        assertFalse(p.test("world"));
    }

    @Test
    void leadingAndTrailingWhitespaceInValuesIsTrimmed() {
        Predicate<String> p = StringConditionParser.parse("{ forest , plains }");
        assertTrue(p.test("forest"));
        assertTrue(p.test("plains"));
        assertFalse(p.test(" forest")); // Leading space not in the set
        assertFalse(p.test("forest "));  // Trailing space not in the set
    }

    // ---------- Empty and malformed input ----------

    @Test
    void emptySetThrows() {
        assertThrows(IllegalArgumentException.class, () -> StringConditionParser.parse("{}"));
    }

    @Test
    void trailingCommaInSetThrows() {
        assertThrows(IllegalArgumentException.class, () -> StringConditionParser.parse("{a, b,}"));
    }

    @Test
    void unclosedParenthesisThrows() {
        assertThrows(IllegalArgumentException.class, () -> StringConditionParser.parse("({a}"));
    }

    @Test
    void unclosedSetBraceThrows() {
        assertThrows(IllegalArgumentException.class, () -> StringConditionParser.parse("{a, b"));
    }

    @Test
    void unknownCharacterThrows() {
        assertThrows(IllegalArgumentException.class, () -> StringConditionParser.parse("{a} ^ {b}"));
    }

    @Test
    void trailingGarbageThrows() {
        assertThrows(IllegalArgumentException.class, () -> StringConditionParser.parse("{a} foo"));
    }

    @Test
    void danglingOperatorThrows() {
        assertThrows(IllegalArgumentException.class, () -> StringConditionParser.parse("{a} &"));
    }

    @Test
    void danglingOperatorAtStartThrows() {
        assertThrows(IllegalArgumentException.class, () -> StringConditionParser.parse("& {a}"));
    }

    // ---------- Case sensitivity parameter ----------

    @Test
    void defaultIsCaseInsensitive() {
        Predicate<String> p1 = StringConditionParser.parse("{test}");
        Predicate<String> p2 = StringConditionParser.parse("{test}", false);
        for (String val : new String[]{"test", "TEST", "Test"}) {
            assertEquals(p1.test(val), p2.test(val));
        }
    }

    @Test
    void caseSensitiveParamWorks() {
        Predicate<String> sensitive = StringConditionParser.parse("{Test}", true);
        assertTrue(sensitive.test("Test"));
        assertFalse(sensitive.test("test"));
        assertFalse(sensitive.test("TEST"));
    }

    // ---------- Complex real-world examples ----------

    @Test
    void biomeFilterExample() {
        // Allow forest biomes except dense variants
        Predicate<String> p = StringConditionParser.parse(
                "{forest, birch_forest, dark_forest} & !{dark_forest}");
        assertTrue(p.test("forest"));
        assertTrue(p.test("birch_forest"));
        assertFalse(p.test("dark_forest"));
        assertFalse(p.test("plains"));
    }

    @Test
    void modIDFilterExample() {
        // Accept mods except certain ones
        Predicate<String> p = StringConditionParser.parse(
                "!{minecraft, modular, problematic_mod}");
        assertTrue(p.test("forestry"));
        assertTrue(p.test("buildercraft"));
        assertFalse(p.test("minecraft"));
        assertFalse(p.test("problematic_mod"));
    }

    @Test
    void dimensionWhitelistExample() {
        // Only specific dimensions are allowed
        Predicate<String> p = StringConditionParser.parse(
                "{minecraft:overworld, minecraft:the_nether} | {custom:custom_dimension}");
        assertTrue(p.test("minecraft:overworld"));
        assertTrue(p.test("MINECRAFT:OVERWORLD"));
        assertTrue(p.test("custom:custom_dimension"));
        assertFalse(p.test("minecraft:the_end"));
    }
}
