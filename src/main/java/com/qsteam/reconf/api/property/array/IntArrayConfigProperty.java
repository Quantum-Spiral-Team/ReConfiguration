package com.qsteam.reconf.api.property.array;

import com.qsteam.reconf.api.property.ConfigProperty;
import com.qsteam.reconf.config.ConfigManager;
import it.unimi.dsi.fastutil.longs.LongPredicate;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class IntArrayConfigProperty extends ConfigProperty {

    private volatile int[] value;
    private final @Nullable LongPredicate validator;

    public IntArrayConfigProperty(String name, List<String> comments, int[] defaultValue, @Nullable LongPredicate validator) {
        super(name, comments, int[].class);
        this.value = defaultValue;
        this.validator = validator;

        if (!isValid()) {
            throw new IllegalArgumentException("Default value for property '" + name + "' is invalid");
        }
    }

    public int[] getIntArray() {
        return this.value;
    }

    public boolean setIntArray(int[] value) {
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

    private boolean testAll(int[] value) {
        if (this.validator == null) return true;

        for (int i : value) {
            if (!this.validator.test(i)) {
                return false;
            }
        }
        return true;
    }

}