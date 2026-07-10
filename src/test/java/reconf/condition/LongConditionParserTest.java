package reconf.condition;

import com.qsteam.reconf.util.condition.DoubleConditionParser;
import com.qsteam.reconf.util.condition.LongConditionParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.function.LongPredicate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link LongConditionParser}.
 *
 * <p>Tests the parser's ability to compile textual condition expressions
 * (e.g. "[0..1000) & !{13} | [1000000..)") into executable {@link LongPredicate}
 * lambdas that evaluate conditions against {@code long} integer values.
 *
 * <p>Covers interval syntax (open/closed/half-open with optional bounds),
 * set membership, boolean operators (AND, OR, NOT), negation, grouping,
 * and error handling for malformed input.
 *
 * <p><strong>Precision guarantee:</strong> All interval bounds and set values
 * are parsed directly from text with {@link Long#parseLong}, never routed
 * through {@code double}. This ensures exact representation across the full
 * {@code long} range, including values beyond 2^53 where {@code double}
 * precision would be lost.
 *
 * <p><strong>Decimal rejection:</strong> Unlike {@link DoubleConditionParser},
 * this parser rejects decimal notation (e.g. {@code [1.5..10]} throws an error).
 * All bounds and set entries must be plain integer literals.
 */
class LongConditionParserTest {

    // ========== Interval Tests: Open, Closed, Half-Open ==========

    /**
     * Verifies that an open interval {@code (1..10)} excludes both endpoints
     * and includes all values strictly between them.
     */
    @ParameterizedTest
    @CsvSource({
            "1,  false",
            "2,  true",
            "5,  true",
            "9,  true",
            "10, false"
    })
    void openInterval(long value, boolean expected) {
        LongPredicate p = LongConditionParser.parse("(1..10)");
        assertEquals(expected, p.test(value));
    }

    /**
     * Verifies that a closed interval {@code [1..10]} includes both endpoints.
     */
    @ParameterizedTest
    @CsvSource({
            "0,  false",
            "1,  true",
            "10, true",
            "11, false"
    })
    void closedInterval(long value, boolean expected) {
        LongPredicate p = LongConditionParser.parse("[1..10]");
        assertEquals(expected, p.test(value));
    }

    /**
     * Verifies that a half-open interval {@code (1..10]} excludes the lower
     * bound but includes the upper bound.
     */
    @ParameterizedTest
    @CsvSource({
            "1,  false",
            "2,  true",
            "10, true"
    })
    void mixedIntervalExclusiveLowInclusiveHigh(long value, boolean expected) {
        LongPredicate p = LongConditionParser.parse("(1..10]");
        assertEquals(expected, p.test(value));
    }

    /**
     * Verifies that a half-open interval {@code [1..10)} includes the lower
     * bound but excludes the upper bound.
     */
    @ParameterizedTest
    @CsvSource({
            "1,  true",
            "10, false"
    })
    void mixedIntervalInclusiveLowExclusiveHigh(long value, boolean expected) {
        LongPredicate p = LongConditionParser.parse("[1..10)");
        assertEquals(expected, p.test(value));
    }

    // ========== Unbounded Interval Tests ==========

    /**
     * Verifies that an interval with no lower bound (only upper bound)
     * matches all values up to and including the upper limit.
     */
    @ParameterizedTest
    @CsvSource({
            "-1000000, true",
            "85,      true",
            "86,      false"
    })
    void unboundedBelow(long value, boolean expected) {
        LongPredicate p = LongConditionParser.parse("(..85]");
        assertEquals(expected, p.test(value));
    }

    /**
     * Verifies that an interval with no upper bound (only lower bound)
     * matches all values from the lower limit upward.
     */
    @ParameterizedTest
    @CsvSource({
            "99,  false",
            "100, true",
            "100000000, true"
    })
    void unboundedAbove(long value, boolean expected) {
        LongPredicate p = LongConditionParser.parse("[100..)");
        assertEquals(expected, p.test(value));
    }

    /**
     * Verifies that a fully unbounded interval {@code (..)} matches every value.
     */
    @Test
    void fullyUnboundedMatchesEverything() {
        LongPredicate p = LongConditionParser.parse("(..)");
        assertTrue(p.test(Long.MIN_VALUE));
        assertTrue(p.test(0L));
        assertTrue(p.test(Long.MAX_VALUE));
    }

    // ========== Set Membership Tests ==========

    /**
     * Verifies that a single-element set matches only that exact value.
     */
    @Test
    void setMatchesExactValueOnly() {
        LongPredicate p = LongConditionParser.parse("{5}");
        assertTrue(p.test(5L));
        assertFalse(p.test(4L));
        assertFalse(p.test(6L));
    }

    /**
     * Verifies that set literals support negative values.
     */
    @Test
    void setSupportsNegativeValues() {
        assertTrue(LongConditionParser.parse("{-3}").test(-3L));
        assertFalse(LongConditionParser.parse("{-3}").test(3L));
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
    void setMatchesAnyOfMultipleValues(long value, boolean expected) {
        LongPredicate p = LongConditionParser.parse("{2, 4, 5, 6}");
        assertEquals(expected, p.test(value));
    }

    /**
     * Verifies that multi-element sets support negative and zero values.
     */
    @Test
    void setWithMultipleValuesSupportsNegatives() {
        LongPredicate p = LongConditionParser.parse("{-10, 0, 5}");
        assertTrue(p.test(-10L));
        assertTrue(p.test(0L));
        assertTrue(p.test(5L));
        assertFalse(p.test(1L));
    }

    /**
     * Verifies that whitespace around commas in set literals is ignored.
     */
    @Test
    void setIgnoresWhitespaceAroundCommas() {
        LongPredicate compact = LongConditionParser.parse("{2,4,5,6}");
        LongPredicate spaced = LongConditionParser.parse("{ 2 ,  4,5 ,   6 }");
        for (long v : new long[]{2, 3, 4, 5, 6, 7}) {
            assertEquals(compact.test(v), spaced.test(v), "Mismatch at value=" + v);
        }
    }

    /**
     * Verifies that duplicate values in a set are handled gracefully.
     */
    @Test
    void setWithDuplicateValuesStillWorks() {
        LongPredicate p = LongConditionParser.parse("{1, 1, 2}");
        assertTrue(p.test(1L));
        assertTrue(p.test(2L));
        assertFalse(p.test(3L));
    }

    /**
     * Verifies that set membership can be combined with other operators
     * to create complex conditions.
     */
    @Test
    void multiValueSetCombinesWithOtherOperators() {
        LongPredicate p = LongConditionParser.parse("{2, 4, 6} & !{4}");
        assertTrue(p.test(2L));
        assertFalse(p.test(4L));
        assertTrue(p.test(6L));
        assertFalse(p.test(8L));
    }

    /**
     * Verifies that an empty set literal is rejected.
     */
    @Test
    void emptySetThrows() {
        assertThrows(IllegalArgumentException.class, () -> LongConditionParser.parse("{}"));
    }

    /**
     * Verifies that a trailing comma in a set literal is rejected.
     */
    @Test
    void trailingCommaInSetThrows() {
        assertThrows(IllegalArgumentException.class, () -> LongConditionParser.parse("{1, 2,}"));
    }

    // ========== Precision Tests (Large Integers Beyond Double's Range) ==========

    /**
     * Verifies that the parser handles integers larger than 2^53 exactly,
     * without precision loss (which would occur if routed through {@code double}).
     *
     * <p>Note: A {@code double} can only precisely represent integers up to 2^53.
     * Beyond that, consecutive integers cannot be distinguished. This test ensures
     * that {@link LongConditionParser} avoids this precision loss by using
     * {@link Long#parseLong} directly.
     */
    @Test
    void largeIntegerBeyondDoubleRangePrecision() {
        long largeValue = 9_223_372_036_854_775_000L;  // close to Long.MAX_VALUE
        long slightly_less = 9_223_372_036_854_774_999L;

        LongPredicate p = LongConditionParser.parse("[9223372036854774999..9223372036854775000]");
        assertTrue(p.test(slightly_less));
        assertTrue(p.test(largeValue));
        assertFalse(p.test(largeValue + 1L));
    }

    /**
     * Verifies that large negative integers are handled exactly.
     */
    @Test
    void largeNegativeIntegerPrecision() {
        LongPredicate p = LongConditionParser.parse("[-9223372036854775000..-9223372036854774999]");
        assertTrue(p.test(-9_223_372_036_854_775_000L));
        assertTrue(p.test(-9_223_372_036_854_774_999L));
        assertFalse(p.test(-9_223_372_036_854_774_998L));
    }

    /**
     * Verifies that set membership works with very large integers.
     */
    @Test
    void largeIntegersInSet() {
        LongPredicate p = LongConditionParser.parse("{9223372036854775000, 1000}");
        assertTrue(p.test(9_223_372_036_854_775_000L));
        assertTrue(p.test(1000L));
        assertFalse(p.test(9_223_372_036_854_774_999L));
    }

    // ========== Complex Expression Tests ==========

    /**
     * Verifies that complex expressions combining intervals, sets,
     * negation, AND, and OR operators work correctly.
     *
     * <p>Expression: {@code [0..1000) & !{13} | [1000000..)}
     * Matches: integers from 0 to 999 (except 13), or integers from 1000000 onwards.
     */
    @ParameterizedTest
    @CsvSource({
            "13,  false",
            "500, true",
            "999, true",
            "1000, false",
            "999999, false",
            "1000000, true",
            "2000000, true"
    })
    void combinedExample(long value, boolean expected) {
        LongPredicate p = LongConditionParser.parse("[0..1000) & !{13} | [1000000..)");
        assertEquals(expected, p.test(value));
    }

    // ========== Whitespace and Format Tests ==========

    /**
     * Verifies that whitespace is allowed and ignored anywhere
     * within a condition expression.
     */
    @Test
    void whitespaceIsIgnoredEverywhere() {
        LongPredicate compact = LongConditionParser.parse("[0..1000)&!{13}|[1000000..)");
        LongPredicate spaced = LongConditionParser.parse(
                "  [ 0 .. 1000 )   &   ! { 13 }   |   [ 1000000 .. )  ");
        for (long v : new long[]{13, 500, 1000, 999999, 1000000, 2000000}) {
            assertEquals(compact.test(v), spaced.test(v),
                    "Mismatch at value=" + v);
        }
    }

    /**
     * Verifies that intervals support negative bounds.
     */
    @Test
    void negativeBounds() {
        LongPredicate p = LongConditionParser.parse("[-10..-1]");
        assertTrue(p.test(-10L));
        assertTrue(p.test(-5L));
        assertTrue(p.test(-1L));
        assertFalse(p.test(-11L));
        assertFalse(p.test(0L));
    }

    // ========== Error Handling Tests ==========

    /**
     * Verifies that a decimal bound (e.g., 1.5) is rejected,
     * since the parser only accepts integer literals.
     */
    @Test
    void decimalBoundIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> LongConditionParser.parse("[1.5..10]"));
    }

    /**
     * Verifies that a decimal set value is rejected.
     */
    @Test
    void decimalSetValueIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> LongConditionParser.parse("{1.5, 2, 3}"));
    }

    /**
     * Verifies that an unrecognized character is rejected.
     */
    @Test
    void unknownCharacterThrows() {
        assertThrows(IllegalArgumentException.class, () -> LongConditionParser.parse("[1..10] ^ {5}"));
    }

    /**
     * Verifies that an unclosed parenthesis is detected as an error.
     */
    @Test
    void unclosedParenthesisThrows() {
        assertThrows(IllegalArgumentException.class, () -> LongConditionParser.parse("([1..10]"));
    }

    /**
     * Verifies that trailing garbage after a valid expression is rejected.
     */
    @Test
    void trailingGarbageThrows() {
        assertThrows(IllegalArgumentException.class, () -> LongConditionParser.parse("[1..10] foo"));
    }

    /**
     * Verifies that an empty expression is rejected.
     */
    @Test
    void emptyExpressionThrows() {
        assertThrows(IllegalArgumentException.class, () -> LongConditionParser.parse(""));
    }

    /**
     * Verifies that a dangling operator at the end is rejected.
     */
    @Test
    void danglingOperatorThrows() {
        assertThrows(IllegalArgumentException.class, () -> LongConditionParser.parse("[1..10] &"));
    }

    /**
     * Verifies that an out-of-range integer literal is rejected.
     */
    @Test
    void integerOverflowThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> LongConditionParser.parse("[" + Long.MAX_VALUE + "1" + "..10]"));
    }
}