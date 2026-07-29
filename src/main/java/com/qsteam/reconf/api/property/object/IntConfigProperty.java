package com.qsteam.reconf.api.property.object;

import com.qsteam.reconf.api.property.ConfigProperty;
import com.qsteam.reconf.config.ConfigManager;
import it.unimi.dsi.fastutil.longs.LongPredicate;
import org.jetbrains.annotations.Nullable;

public class IntConfigProperty extends ConfigProperty {

    private final int defaultValue;
    private volatile int value;
    private final @Nullable LongPredicate validator;

    public IntConfigProperty(String name, String[] comments, int defaultValue, @Nullable LongPredicate validator) {
        super(name, comments, int.class);
        this.defaultValue = defaultValue;
        this.value = defaultValue;
        this.validator = validator;

        if (!isValid()) {
            throw new IllegalArgumentException("Default value for property '" + name + "' is invalid");
        }
    }

    public int getInt() {
        return this.value;
    }

    public boolean setInt(int value) {
        if (validator == null || validator.test(value)) {
            this.value = value;
            return true;
        } else {
            ConfigManager.LOGGER.error("Failed to set property '{}': invalid value '{}'", getName(), value);
            return false;
        }
    }

    public int getDefaultValue() {
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