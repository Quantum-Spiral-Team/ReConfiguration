package com.qsteam.reconf.api.property.object;

import com.qsteam.reconf.api.property.ConfigProperty;
import com.qsteam.reconf.config.ConfigManager;
import it.unimi.dsi.fastutil.longs.LongPredicate;
import org.jetbrains.annotations.Nullable;

public class LongConfigProperty extends ConfigProperty {

    private final long defaultValue;
    private volatile long value;
    private final @Nullable LongPredicate validator;

    public LongConfigProperty(String name, String[] comments, long defaultValue, @Nullable LongPredicate validator) {
        super(name, comments, long.class);
        this.defaultValue = defaultValue;
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

    public long getDefaultValue() {
        return this.defaultValue;
    }

    public void resetToDefault() {
        this.value = defaultValue;
    }

    @Override
    public boolean isValid() {
        return validator == null || validator.test(this.defaultValue);
    }

}