package com.qsteam.reconf.api.property.primitivearray;

import com.qsteam.reconf.api.property.ConfigProperty;
import com.qsteam.reconf.config.ConfigManager;
import com.qsteam.reconf.util.property.ArrayValidators;
import it.unimi.dsi.fastutil.longs.LongPredicate;
import org.jetbrains.annotations.Nullable;

public class LongArrayConfigProperty extends ConfigProperty {

    private volatile long[] value;
    private final @Nullable LongPredicate validator;

    public LongArrayConfigProperty(String name, String[] comments, long[] defaultValue, @Nullable LongPredicate validator) {
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
        if (ArrayValidators.testAll(value, this.validator)) {
            this.value = value;
            return true;
        } else {
            ConfigManager.LOGGER.error("Failed to set property '{}': invalid array", getName());
            return false;
        }
    }

    @Override
    public boolean isValid() {
        return ArrayValidators.testAll(this.value, this.validator);
    }

}