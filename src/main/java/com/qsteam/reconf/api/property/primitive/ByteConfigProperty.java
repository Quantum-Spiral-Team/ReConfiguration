package com.qsteam.reconf.api.property.primitive;

import com.qsteam.reconf.api.property.ConfigProperty;
import com.qsteam.reconf.config.ConfigManager;
import it.unimi.dsi.fastutil.longs.LongPredicate;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ByteConfigProperty extends ConfigProperty {

    private volatile byte value;
    private final @Nullable LongPredicate validator;

    public ByteConfigProperty(String name, List<String> comments, byte defaultValue, @Nullable LongPredicate validator) {
        super(name, comments, byte.class);
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

    @Override
    public boolean isValid() {
        return validator == null || validator.test(this.value);
    }

}