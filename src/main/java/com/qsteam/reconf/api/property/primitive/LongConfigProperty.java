package com.qsteam.reconf.api.property.primitive;

import com.qsteam.reconf.api.property.ConfigProperty;
import com.qsteam.reconf.config.ConfigManager;
import it.unimi.dsi.fastutil.longs.LongPredicate;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class LongConfigProperty extends ConfigProperty {

    private volatile long value;
    private final @Nullable LongPredicate validator;

    public LongConfigProperty(String name, List<String> comments, long defaultValue, @Nullable LongPredicate validator) {
        super(name, comments, long.class);
        this.value = defaultValue;
        this.validator = validator;

        if (!isValid()) {
            throw new IllegalArgumentException("Default value for property '" + name + "' is invalid");
        }
    }

    public long getLong() {
        return this.value;
    }

    public boolean setLong(long value) {
        if (validator == null || validator.test(value)) {
            this.value = value;
            return true;
        } else {
            ConfigManager.LOGGER.error("Failed to set property '{}': invalid value '{}'", getName(), value);
            return false;
        }
    }

    @Override
    public boolean isValid() {
        return validator == null || validator.test(this.value);
    }

}