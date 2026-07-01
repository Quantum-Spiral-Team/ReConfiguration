package com.qsteam.reconf.api.property.primitive;

import com.qsteam.reconf.api.property.ConfigProperty;
import com.qsteam.reconf.config.ConfigManager;
import it.unimi.dsi.fastutil.doubles.DoublePredicate;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class FloatConfigProperty extends ConfigProperty {

    private volatile float value;
    private final @Nullable DoublePredicate validator;

    public FloatConfigProperty(String name, List<String> comments, float defaultValue, @Nullable DoublePredicate validator) {
        super(name, comments, float.class);
        this.value = defaultValue;
        this.validator = validator;

        if (!isValid()) {
            throw new IllegalArgumentException("Default value for property '" + name + "' is invalid");
        }
    }

    public float getFloat() {
        return this.value;
    }

    public boolean setFloat(float value) {
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