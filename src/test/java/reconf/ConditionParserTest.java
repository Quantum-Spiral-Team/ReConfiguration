package reconf;

import com.qsteam.reconf.util.ConditionParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

class ConditionParserTest {

    @ParameterizedTest
    @CsvSource({
            "1.0,  false",
            "1.1,  true",
            "5.0,  true",
            "9.9,  true",
            "10.0, false"
    })
    void openInterval(double value, boolean expected) {
        Predicate<Double> p = ConditionParser.parse("(1..10)");
        assertEquals(expected, p.test(value));
    }

    @ParameterizedTest
    @CsvSource({
            "0.9,  false",
            "1.0,  true",
            "10.0, true",
            "10.1, false"
    })
    void closedInterval(double value, boolean expected) {
        Predicate<Double> p = ConditionParser.parse("[1..10]");
        assertEquals(expected, p.test(value));
    }

    @ParameterizedTest
    @CsvSource({
            "1.0,  false",
            "1.1,  true",
            "10.0, true"
    })
    void mixedIntervalExclusiveLowInclusiveHigh(double value, boolean expected) {
        Predicate<Double> p = ConditionParser.parse("(1..10]");
        assertEquals(expected, p.test(value));
    }

    @ParameterizedTest
    @CsvSource({
            "1.0,  true",
            "10.0, false"
    })
    void mixedIntervalInclusiveLowExclusiveHigh(double value, boolean expected) {
        Predicate<Double> p = ConditionParser.parse("[1..10)");
        assertEquals(expected, p.test(value));
    }



    @ParameterizedTest
    @CsvSource({
            "-1000.0, true",
            "85.0,    true",
            "85.1,    false"
    })
    void unboundedBelow(double value, boolean expected) {
        Predicate<Double> p = ConditionParser.parse("(..85]");
        assertEquals(expected, p.test(value));
    }

    @ParameterizedTest
    @CsvSource({
            "99.9,   false",
            "100.0,  true",
            "100000, true"
    })
    void unboundedAbove(double value, boolean expected) {
        Predicate<Double> p = ConditionParser.parse("[100..)");
        assertEquals(expected, p.test(value));
    }

    @Test
    void fullyUnboundedMatchesEverything() {
        Predicate<Double> p = ConditionParser.parse("(..)");
        assertTrue(p.test(-1_000_000.0));
        assertTrue(p.test(0.0));
        assertTrue(p.test(1_000_000.0));
    }



    @Test
    void setMatchesExactValueOnly() {
        Predicate<Double> p = ConditionParser.parse("{5}");
        assertTrue(p.test(5.0));
        assertFalse(p.test(5.0001));
        assertFalse(p.test(4.9999));
    }

    @Test
    void setSupportsNegativeAndDecimalValues() {
        assertTrue(ConditionParser.parse("{-3.5}").test(-3.5));
        assertFalse(ConditionParser.parse("{-3.5}").test(3.5));
    }

    @ParameterizedTest
    @CsvSource({
            "2,  true",
            "4,  true",
            "5,  true",
            "6,  true",
            "3,  false",
            "7,  false"
    })
    void setMatchesAnyOfMultipleValues(double value, boolean expected) {
        Predicate<Double> p = ConditionParser.parse("{2, 4, 5, 6}");
        assertEquals(expected, p.test(value));
    }

    @Test
    void setWithMultipleValuesSupportsNegativesAndDecimals() {
        Predicate<Double> p = ConditionParser.parse("{-1.5, 0, 2.25}");
        assertTrue(p.test(-1.5));
        assertTrue(p.test(0.0));
        assertTrue(p.test(2.25));
        assertFalse(p.test(1.0));
    }

    @Test
    void setIgnoresWhitespaceAroundCommas() {
        Predicate<Double> compact = ConditionParser.parse("{2,4,5,6}");
        Predicate<Double> spaced = ConditionParser.parse("{ 2 ,  4,5 ,   6 }");
        for (double v : new double[]{2, 3, 4, 5, 6, 7}) {
            assertEquals(compact.test(v), spaced.test(v), "Mismatch at value=" + v);
        }
    }

    @Test
    void setWithDuplicateValuesStillWorks() {
        Predicate<Double> p = ConditionParser.parse("{1, 1, 2}");
        assertTrue(p.test(1.0));
        assertTrue(p.test(2.0));
        assertFalse(p.test(3.0));
    }

    @Test
    void multiValueSetCombinesWithOtherOperators() {
        Predicate<Double> p = ConditionParser.parse("{2, 4, 6} & !{4}");
        assertTrue(p.test(2.0));
        assertFalse(p.test(4.0));
        assertTrue(p.test(6.0));
        assertFalse(p.test(8.0));
    }

    @Test
    void emptySetThrows() {
        assertThrows(IllegalArgumentException.class, () -> ConditionParser.parse("{}"));
    }

    @Test
    void trailingCommaInSetThrows() {
        assertThrows(IllegalArgumentException.class, () -> ConditionParser.parse("{1, 2,}"));
    }




    @ParameterizedTest
    @CsvSource({
            "5,   false",
            "50,  true",
            "85,  true",
            "86,  false",
            "99,  false",
            "100, true",
            "150, true"
    })
    void combinedExample(double value, boolean expected) {
        Predicate<Double> p = ConditionParser.parse("(..85] & !{5} | [100..)");
        assertEquals(expected, p.test(value));
    }



    @Test
    void whitespaceIsIgnoredEverywhere() {
        Predicate<Double> compact = ConditionParser.parse("(..85]&!{5}|[100..)");
        Predicate<Double> spaced = ConditionParser.parse(
                "  ( .. 85 ]   &   ! { 5 }   |   [ 100 .. )  ");
        for (double v : new double[]{5, 50, 85, 86, 100, 150}) {
            assertEquals(compact.test(v), spaced.test(v),
                    "Расхождение при value=" + v);
        }
    }



    @Test
    void negativeAndDecimalBounds() {
        Predicate<Double> p = ConditionParser.parse("[-10.5..-1.25]");
        assertTrue(p.test(-10.5));
        assertTrue(p.test(-5.0));
        assertTrue(p.test(-1.25));
        assertFalse(p.test(-10.51));
        assertFalse(p.test(-1.24));
    }



    @Test
    void unknownCharacterThrows() {
        assertThrows(IllegalArgumentException.class, () -> ConditionParser.parse("[1..10] ^ {5}"));
    }

    @Test
    void unclosedParenthesisThrows() {
        assertThrows(IllegalArgumentException.class, () -> ConditionParser.parse("([1..10]"));
    }

    @Test
    void trailingGarbageThrows() {
        assertThrows(IllegalArgumentException.class, () -> ConditionParser.parse("[1..10] foo"));
    }

    @Test
    void emptyExpressionThrows() {
        assertThrows(IllegalArgumentException.class, () -> ConditionParser.parse(""));
    }

    @Test
    void danglingOperatorThrows() {
        assertThrows(IllegalArgumentException.class, () -> ConditionParser.parse("[1..10] &"));
    }

}