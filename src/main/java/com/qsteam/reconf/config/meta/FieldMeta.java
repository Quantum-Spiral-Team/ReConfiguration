package com.qsteam.reconf.config.meta;

import com.qsteam.reconf.api.property.ConfigProperty;

import java.lang.reflect.Field;

public record FieldMeta(
        Field field,
        ConfigProperty property,
        String langKey,
        boolean requiresMcRestart,
        boolean requiresWorldRestart,
        boolean isSlidingOption
) {}