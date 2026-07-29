package com.qsteam.reconf.api.property.object;

import com.qsteam.reconf.api.property.ConfigProperty;
import com.qsteam.reconf.config.ConfigManager;
import it.unimi.dsi.fastutil.longs.LongPredicate;
import org.jetbrains.annotations.Nullable;

public class ByteConfigProperty extends ConfigProperty {

    private final byte defaultValue;
    private volatile byte value;
    private final @Nullable LongPredicate validator;

    public ByteConfigProperty(String name, String[] comments, byte defaultValue, @Nullable LongPredicate validator) {
        super(name, comments, byte.class);
        this.defaultValue = defaultValue;
        this.value = defaultValue;
        this.validator = validator;

        if (!isValid()) {
            throw new IllegalArgumentException("Default value for property '" + name + "' is invalid");
        }
    }

    public byte getByte() {
        return this.value;
    }

    public boolean setByte(byte value) {
        if (validator == null || validator.test(value)) {
            this.value = value;
            return true;
        } else {
            ConfigManager.LOGGER.error("Failed to set property '{}': invalid value '{}'", getName(), value);
            return false;
        }
    }

    public byte getDefaultValue() {
        return this.defaultValue;
    }

    public void resetToDefault() {
        this.value = this.defaultValue;
    }

    @Override
    public boolean isValid() {
        return validator == null || validator.test(this.defaultValue);
    }

}