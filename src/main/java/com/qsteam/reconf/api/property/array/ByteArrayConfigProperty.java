package com.qsteam.reconf.api.property.array;

import com.qsteam.reconf.api.property.ConfigProperty;
import com.qsteam.reconf.config.ConfigManager;
import it.unimi.dsi.fastutil.longs.LongPredicate;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ByteArrayConfigProperty extends ConfigProperty {

    private volatile byte[] value;
    private final @Nullable LongPredicate validator;

    public ByteArrayConfigProperty(String name, List<String> comments, byte[] defaultValue, @Nullable LongPredicate validator) {
        super(name, comments, byte[].class);
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

    private boolean testAll(byte[] value) {
        if (this.validator == null) return true;

        for (byte i : value) {
            if (!this.validator.test(i)) {
                return false;
            }
        }
        return true;
    }

}