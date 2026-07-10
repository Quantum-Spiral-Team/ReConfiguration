package reconf.condition;

import com.qsteam.reconf.util.condition.DoubleConditionParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.function.DoublePredicate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link DoubleConditionParser}.
 *
 * <p>Tests the parser's ability to compile textual condition expressions
 * (e.g. "(1..10) & !{5} | [100..)") into executable {@link DoublePredicate}
 * lambdas that evaluate conditions against {@code double} values.
 *
 * <p>Covers interval syntax (open/closed/half-open with optional bounds),
 * set membership, boolean operators (AND, OR, NOT), negation, grouping,
 * and error handling for malformed input.
 *
 * <p><strong>Note:</strong> The parser accepts integer literal bounds
 * (e.g. {@code [1..10]}) and interprets them as {@code [1.0..10.0]}.
 */
class DoubleConditionParserTest {

    // ========== Interval Tests: Open, Closed, Half-Open ==========

    /**
     * Verifies that an open interval {@code (1..10)} excludes both endpoints
     * and includes all values strictly between them.
     */
    @ParameterizedTest
    @CsvSource({
            "1.0,  false",
            "1.1,  true",
            "5.0,  true",
            "9.9,  true",
            "10.0, false"
    })
    void openInterval(double value, boolean expected) {
        DoublePredicate p = DoubleConditionParser.parse("(1..10)");
        assertEquals(expected, p.test(value));
    }

    /**
     * Verifies that a closed interval {@code [1..10]} includes both endpoints.
     */
    @ParameterizedTest
    @CsvSource({
            "0.9,  false",
            "1.0,  true",
            "10.0, true",
            "10.1, false"
    })
    void closedInterval(double value, boolean expected) {
        DoublePredicate p = DoubleConditionParser.parse("[1..10]");
        assertEquals(expected, p.test(value));
    }

    /**
     * Verifies that a half-open interval {@code (1..10]} excludes the lower
     * bound but includes the upper bound.
     */
    @ParameterizedTest
    @CsvSource({
            "1.0,  false",
            "1.1,  true",
            "10.0, true"
    })
    void mixedIntervalExclusiveLowInclusiveHigh(double value, boolean expected) {
        DoublePredicate p = DoubleConditionParser.parse("(1..10]");
        assertEquals(expected, p.test(value));
    }

    /**
     * Verifies that a half-open interval {@code [1..10)} includes the lower
     * bound but excludes the upper bound.
     */
    @ParameterizedTest
    @CsvSource({
            "1.0,  true",
            "10.0, false"
    })
    void mixedIntervalInclusiveLowExclusiveHigh(double value, boolean expected) {
        DoublePredicate p = DoubleConditionParser.parse("[1..10)");
        assertEquals(expected, p.test(value));
    }



    // ========== Unbounded Interval Tests ==========

    /**
     * Verifies that an interval with no lower bound (only upper bound)
     * matches all values up to and including the upper limit.
     */
    @ParameterizedTest
    @CsvSource({
            "-1000.0, true",
            "85.0,    true",
            "85.1,    false"
    })
    void unboundedBelow(double value, boolean expected) {
        DoublePredicate p = DoubleConditionParser.parse("(..85]");
        assertEquals(expected, p.test(value));
    }

    /**
     * Verifies that an interval with no upper bound (only lower bound)
     * matches all values from the lower limit upward.
     */
    @ParameterizedTest
    @CsvSource({
            "99.9,   false",
            "100.0,  true",
            "100000, true"
    })
    void unboundedAbove(double value, boolean expected) {
        DoublePredicate p = DoubleConditionParser.parse("[100..)");
        assertEquals(expected, p.test(value));
    }

    /**
     * Verifies that a fully unbounded interval {@code (..)} matches every value.
     */
    @Test
    void fullyUnboundedMatchesEverything() {
        DoublePredicate p = DoubleConditionParser.parse("(..)");
        assertTrue(p.test(-1_000_000.0));
        assertTrue(p.test(0.0));
        assertTrue(p.test(1_000_000.0));
    }



    // ========== Set Membership Tests ==========

    /**
     * Verifies that a single-element set matches only that exact value,
     * using {@link Double#compare} semantics (not {@code ==}).
     */
    @Test
    void setMatchesExactValueOnly() {
        DoublePredicate p = DoubleConditionParser.parse("{5}");
        assertTrue(p.test(5.0));
        assertFalse(p.test(5.0001));
        assertFalse(p.test(4.9999));
    }

    /**
     * Verifies that set literals support negative and decimal values.
     */
    @Test
    void setSupportsNegativeAndDecimalValues() {
        assertTrue(DoubleConditionParser.parse("{-3.5}").test(-3.5));
        assertFalse(DoubleConditionParser.parse("{-3.5}").test(3.5));
    }

    /**
     * Verifies that a multi-element set matches if the value is in the set.
     */
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
        DoublePredicate p = DoubleConditionParser.parse("{2, 4, 5, 6}");
        assertEquals(expected, p.test(value));
    }

    /**
     * Verifies that multi-element sets support negative, decimal,
     * and zero values together.
     */
    @Test
    void setWithMultipleValuesSupportsNegativesAndDecimals() {
        DoublePredicate p = DoubleConditionParser.parse("{-1.5, 0, 2.25}");
        assertTrue(p.test(-1.5));
        assertTrue(p.test(0.0));
        assertTrue(p.test(2.25));
        assertFalse(p.test(1.0));
    }

    /**
     * Verifies that whitespace around commas in set literals is ignored.
     */
    @Test
    void setIgnoresWhitespaceAroundCommas() {
        DoublePredicate compact = DoubleConditionParser.parse("{2,4,5,6}");
        DoublePredicate spaced = DoubleConditionParser.parse("{ 2 ,  4,5 ,   6 }");
        for (double v : new double[]{2, 3, 4, 5, 6, 7}) {
            assertEquals(compact.test(v), spaced.test(v), "Mismatch at value=" + v);
        }
    }

    /**
     * Verifies that duplicate values in a set are handled gracefully.
     */
    @Test
    void setWithDuplicateValuesStillWorks() {
        DoublePredicate p = DoubleConditionParser.parse("{1, 1, 2}");
        assertTrue(p.test(1.0));
        assertTrue(p.test(2.0));
        assertFalse(p.test(3.0));
    }

    /**
     * Verifies that set membership can be combined with other operators
     * to create complex conditions.
     */
    @Test
    void multiValueSetCombinesWithOtherOperators() {
        DoublePredicate p = DoubleConditionParser.parse("{2, 4, 6} & !{4}");
        assertTrue(p.test(2.0));
        assertFalse(p.test(4.0));
        assertTrue(p.test(6.0));
        assertFalse(p.test(8.0));
    }

    /**
     * Verifies that an empty set literal is rejected.
     */
    @Test
    void emptySetThrows() {
        assertThrows(IllegalArgumentException.class, () -> DoubleConditionParser.parse("{}"));
    }

    /**
     * Verifies that a trailing comma in a set literal is rejected.
     */
    @Test
    void trailingCommaInSetThrows() {
        assertThrows(IllegalArgumentException.class, () -> DoubleConditionParser.parse("{1, 2,}"));
    }




    // ========== Complex Expression Tests ==========

    /**
     * Verifies that complex expressions combining intervals, sets,
     * negation, AND, and OR operators work correctly.
     *
     * <p>Expression: {@code (..85] & !{5} | [100..)}
     * Matches: values up to 85 (except 5), or values from 100 onwards.
     */
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
        DoublePredicate p = DoubleConditionParser.parse("(..85] & !{5} | [100..)");
        assertEquals(expected, p.test(value));
    }

    // ========== Whitespace and Format Tests ==========

    /**
     * Verifies that whitespace is allowed and ignored anywhere
     * within a condition expression.
     */
    @Test
    void whitespaceIsIgnoredEverywhere() {
        DoublePredicate compact = DoubleConditionParser.parse("(..85]&!{5}|[100..)");
        DoublePredicate spaced = DoubleConditionParser.parse(
                "  ( .. 85 ]   &   ! { 5 }   |   [ 100 .. )  ");
        for (double v : new double[]{5, 50, 85, 86, 100, 150}) {
            assertEquals(compact.test(v), spaced.test(v),
                    "Mismatch at value=" + v);
        }
    }

    /**
     * Verifies that intervals support negative and decimal bounds.
     */
    @Test
    void negativeAndDecimalBounds() {
        DoublePredicate p = DoubleConditionParser.parse("[-10.5..-1.25]");
        assertTrue(p.test(-10.5));
        assertTrue(p.test(-5.0));
        assertTrue(p.test(-1.25));
        assertFalse(p.test(-10.51));
        assertFalse(p.test(-1.24));
    }

    // ========== Error Handling Tests ==========

    /**
     * Verifies that an unrecognized character is rejected.
     */
    @Test
    void unknownCharacterThrows() {
        assertThrows(IllegalArgumentException.class, () -> DoubleConditionParser.parse("[1..10] ^ {5}"));
    }

    /**
     * Verifies that an unclosed parenthesis is detected as an error.
     */
    @Test
    void unclosedParenthesisThrows() {
        assertThrows(IllegalArgumentException.class, () -> DoubleConditionParser.parse("([1..10]"));
    }

    /**
     * Verifies that trailing garbage after a valid expression is rejected.
     */
    @Test
    void trailingGarbageThrows() {
        assertThrows(IllegalArgumentException.class, () -> DoubleConditionParser.parse("[1..10] foo"));
    }

    /**
     * Verifies that an empty expression is rejected.
     */
    @Test
    void emptyExpressionThrows() {
        assertThrows(IllegalArgumentException.class, () -> DoubleConditionParser.parse(""));
    }

    /**
     * Verifies that a dangling operator at the end is rejected.
     */
    @Test
    void danglingOperatorThrows() {
        assertThrows(IllegalArgumentException.class, () -> DoubleConditionParser.parse("[1..10] &"));
    }

}