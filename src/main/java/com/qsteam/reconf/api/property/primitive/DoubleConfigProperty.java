package com.qsteam.reconf.api.property.primitive;

import com.qsteam.reconf.api.property.ConfigProperty;
import com.qsteam.reconf.config.ConfigManager;
import it.unimi.dsi.fastutil.doubles.DoublePredicate;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class DoubleConfigProperty extends ConfigProperty {

    private volatile double value;
    private final @Nullable DoublePredicate validator;

    public DoubleConfigProperty(String name, List<String> comments, double defaultValue, @Nullable DoublePredicate validator) {
        super(name, comments, double.class);
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

    @Override
    public boolean isValid() {
        return validator == null || validator.test(this.value);
    }

}