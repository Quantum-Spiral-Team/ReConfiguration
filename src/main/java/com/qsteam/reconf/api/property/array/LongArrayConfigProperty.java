package com.qsteam.reconf.api.property.array;

import com.qsteam.reconf.api.property.ConfigProperty;
import com.qsteam.reconf.config.ConfigManager;
import it.unimi.dsi.fastutil.longs.LongPredicate;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class LongArrayConfigProperty extends ConfigProperty {

    private volatile long[] value;
    private final @Nullable LongPredicate validator;

    public LongArrayConfigProperty(String name, List<String> comments, long[] defaultValue, @Nullable LongPredicate validator) {
        super(name, comments, long[].class);
        this.value = defaultValue;
        this.validator = validator;

        if (!isValid()) {
            throw new IllegalArgumentException("Default value for property '" + name + "' is invalid");
        }
    }

    public long[] getLongArray() {
        return this.value;
    }

    public boolean setLongArray(long[] value) {
        if (this.testAll(value)) {
            this.value = value;
            return true;
        } else {
            ConfigManager.LOGGER.error("Failed to set property '{}': invalid array", getName());
            return false;
        }
    }

    @Override
    public boolean isValid() {
        return this.testAll(this.value);
    }

    private boolean testAll(long[] value) {
        if (this.validator == null) return true;

        for (long i : value) {
            if (!this.validator.test(i)) {
                return false;
            }
        }
        return true;
    }

}