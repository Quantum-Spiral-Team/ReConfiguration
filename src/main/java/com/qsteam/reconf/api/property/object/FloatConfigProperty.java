package com.qsteam.reconf.api.property.object;

import com.qsteam.reconf.api.property.ConfigProperty;
import com.qsteam.reconf.config.ConfigManager;
import it.unimi.dsi.fastutil.doubles.DoublePredicate;
import org.jetbrains.annotations.Nullable;

public class FloatConfigProperty extends ConfigProperty {

    private final float defaultValue;
    private volatile float value;
    private final @Nullable DoublePredicate validator;

    public FloatConfigProperty(String name, String[] comments, float defaultValue, @Nullable DoublePredicate validator) {
        super(name, comments, float.class);
        this.defaultValue = defaultValue;
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

    public float getDefaultValue() {
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