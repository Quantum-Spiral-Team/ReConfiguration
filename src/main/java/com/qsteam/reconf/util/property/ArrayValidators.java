package com.qsteam.reconf.util.property;

import it.unimi.dsi.fastutil.chars.CharPredicate;
import it.unimi.dsi.fastutil.doubles.DoublePredicate;
import it.unimi.dsi.fastutil.longs.LongPredicate;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

public class ArrayValidators {

    private ArrayValidators() {}

    // ==== Array tests ====

    public static boolean testAll(byte[] bytes, @Nullable LongPredicate validator) {
        if (validator == null) return true;
        for (byte value : bytes) if (!validator.test(value)) return false;
        return true;
    }

    public static boolean testAll(short[] shorts, @Nullable LongPredicate validator) {
        if (validator == null) return true;
        for (short value : shorts) if (!validator.test(value)) return false;
        return true;
    }

    public static boolean testAll(int[] ints, @Nullable LongPredicate validator) {
        if (validator == null) return true;
        for (int value : ints) if (!validator.test(value)) return false;
        return true;
    }

    public static boolean testAll(long[] longs, @Nullable LongPredicate validator) {
        if (validator == null) return true;
        for (long value : longs) if (!validator.test(value)) return false;
        return true;
    }


    public static boolean testAll(float[] floats, @Nullable DoublePredicate validator) {
        if (validator == null) return true;
        for (float value : floats) if (!validator.test(value)) return false;
        return true;
    }

    public static boolean testAll(double[] doubles, @Nullable DoublePredicate validator) {
        if (validator == null) return true;
        for (double value : doubles) if (!validator.test(value)) return false;
        return true;
    }

    public static boolean testAll(char[] chars, @Nullable CharPredicate validator) {
        if (validator == null) return true;
        for (char value : chars) if (!validator.test(value)) return false;
        return true;
    }

    public static boolean testAll(String[] strings, @Nullable Predicate<String> validator) {
        if (validator == null) return true;
        for (String value : strings) if (!validator.test(value)) return false;
        return true;
    }

    public static <T> boolean testAll(T[] objects, @Nullable Predicate<String> validator) {
        if (validator == null) return true;
        for (T value : objects) if (!validator.test(PropertyUtils.serialize(value))) return false;
        return true;
    }

    // ==== Lifts ====

    public static Predicate<char[]> liftChar(@Nullable CharPredicate validator) {
        return array -> testAll(array, validator);
    }

    public static Predicate<byte[]> liftByte(@Nullable LongPredicate validator) {
        return array -> testAll(array, validator);
    }

    public static Predicate<short[]> liftShort(@Nullable LongPredicate validator) {
        return array -> testAll(array, validator);
    }

    public static Predicate<int[]> liftInt(@Nullable LongPredicate validator) {
        return array -> testAll(array, validator);
    }

    public static Predicate<long[]> liftLong(@Nullable LongPredicate validator) {
        return array -> testAll(array, validator);
    }

    public static Predicate<double[]> liftDouble(@Nullable DoublePredicate validator) {
        return array -> testAll(array, validator);
    }

    public static Predicate<float[]> liftFloat(@Nullable DoublePredicate validator) {
        return array -> testAll(array, validator);
    }

}
