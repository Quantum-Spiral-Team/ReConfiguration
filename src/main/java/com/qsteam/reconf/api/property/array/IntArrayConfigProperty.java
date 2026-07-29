package com.qsteam.reconf.api.property.array;

import com.qsteam.reconf.api.property.ConfigProperty;
import com.qsteam.reconf.config.ConfigManager;
import com.qsteam.reconf.util.property.ArrayValidators;
import it.unimi.dsi.fastutil.longs.LongPredicate;
import org.jetbrains.annotations.Nullable;

public class IntArrayConfigProperty extends ConfigProperty {

    private final int[] defaultValue;
    private volatile int[] value;
    private final @Nullable LongPredicate validator;

    public IntArrayConfigProperty(String name, String[] comments, int[] defaultValue, @Nullable LongPredicate validator) {
        super(name, comments, int[].class);
        this.defaultValue = defaultValue;
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
        if (ArrayValidators.testAll(value, this.validator)) {
            this.value = value;
            return true;
        } else {
            ConfigManager.LOGGER.error("Failed to set property '{}': invalid array", getName());
            return false;
        }
    }

    public int[] getDefaultValue() {
        return defaultValue;
    }

    public void resetToDefault() {
        this.value = this.defaultValue;
    }

    @Override
    public boolean isValid() {
        return ArrayValidators.testAll(this.defaultValue, this.validator);
    }

}