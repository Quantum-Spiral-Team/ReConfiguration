package com.qsteam.reconf.config.meta;

import it.unimi.dsi.fastutil.doubles.DoublePredicate;

import java.lang.reflect.Field;
import java.util.function.Predicate;

public record FieldMeta(
        Field field,
        String configName,
        String[] comment,
        String langKey,
        boolean requiresMcRestart,
        boolean requiresWorldRestart,
        boolean isSlidingOption,
        RangeInfo range
) {
    public sealed interface RangeInfo {
        record None()                               implements RangeInfo {}
        record Numeric(DoublePredicate range)       implements RangeInfo {}
        record StringBased(Predicate<String> range) implements RangeInfo {}

        RangeInfo NONE = new None();
    }
}