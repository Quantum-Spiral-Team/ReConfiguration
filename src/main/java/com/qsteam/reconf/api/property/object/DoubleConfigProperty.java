package com.qsteam.reconf.api.property.object;

import com.qsteam.reconf.api.property.ConfigProperty;
import com.qsteam.reconf.config.ConfigManager;
import it.unimi.dsi.fastutil.doubles.DoublePredicate;
import org.jetbrains.annotations.Nullable;

public class DoubleConfigProperty extends ConfigProperty {

    private final double defaultValue;
    private volatile double value;
    private final @Nullable DoublePredicate validator;

    public DoubleConfigProperty(String name, String[] comments, double defaultValue, @Nullable DoublePredicate validator) {
        super(name, comments, double.class);
        this.defaultValue = defaultValue;
        this.value = defaultValue;
        this.validator = validator;

        if (!isValid()) {
            throw new IllegalArgumentException("Default value for property '" + name + "' is invalid");
        }
    }

    public double getDouble() {
        return this.value;
    }

    public boolean setDouble(double value) {
        if (validator == null || validator.test(value)) {
            this.value = value;
            return true;
        } else {
            ConfigManager.LOGGER.error("Failed to set property '{}': invalid value '{}'", getName(), value);
            return false;
        }
    }

    public double getDefaultValue() {
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