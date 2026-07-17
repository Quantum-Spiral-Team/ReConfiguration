package com.qsteam.reconf.api.property.primitivearray;

import com.qsteam.reconf.api.property.ConfigProperty;
import com.qsteam.reconf.config.ConfigManager;
import com.qsteam.reconf.util.property.ArrayValidators;
import it.unimi.dsi.fastutil.longs.LongPredicate;
import org.jetbrains.annotations.Nullable;

public class ShortArrayConfigProperty extends ConfigProperty {

    private volatile short[] value;
    private final @Nullable LongPredicate validator;

    public ShortArrayConfigProperty(String name, String[] comments, short[] defaultValue, @Nullable LongPredicate validator) {
        super(name, comments, short[].class);
        this.value = defaultValue;
        this.validator = validator;

        if (!isValid()) {
            throw new IllegalArgumentException("Default value for property '" + name + "' is invalid");
        }
    }

    public short[] getShortArray() {
        return this.value;
    }

    public boolean setShortArray(short[] value) {
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