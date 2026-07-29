package com.qsteam.reconf.api.property.array;

import com.qsteam.reconf.api.property.ConfigProperty;
import com.qsteam.reconf.config.ConfigManager;
import com.qsteam.reconf.util.property.ArrayValidators;
import it.unimi.dsi.fastutil.longs.LongPredicate;
import org.jetbrains.annotations.Nullable;

public class ByteArrayConfigProperty extends ConfigProperty {

    private final byte[] defaultValue;
    private volatile byte[] value;
    private final @Nullable LongPredicate validator;

    public ByteArrayConfigProperty(String name, String[] comments, byte[] defaultValue, @Nullable LongPredicate validator) {
        super(name, comments, byte[].class);
        this.defaultValue = defaultValue;
        this.value = defaultValue;
        this.validator = validator;

        if (!isValid()) {
            throw new IllegalArgumentException("Default value for property '" + name + "' is invalid");
        }
    }

    public byte[] getByteArray() {
        return this.value;
    }

    public boolean setByteArray(byte[] value) {
        if (ArrayValidators.testAll(value, this.validator)) {
            this.value = value;
            return true;
        } else {
            ConfigManager.LOGGER.error("Failed to set property '{}': invalid array", getName());
            return false;
        }
    }

    public byte[] getDefaultValue() {
        return this.defaultValue;
    }

    public void resetToDefault() {
        this.value = defaultValue;
    }

    @Override
    public boolean isValid() {
        return ArrayValidators.testAll(this.defaultValue, this.validator);
    }

}