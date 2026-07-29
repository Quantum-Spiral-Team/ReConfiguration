package com.qsteam.reconf.api.property.array;

import com.qsteam.reconf.api.property.ConfigProperty;
import com.qsteam.reconf.config.ConfigManager;
import com.qsteam.reconf.util.property.ArrayValidators;
import it.unimi.dsi.fastutil.doubles.DoublePredicate;
import org.jetbrains.annotations.Nullable;

public class FloatArrayConfigProperty extends ConfigProperty {

    private final float[] defaultValue;
    private volatile float[] value;
    private final @Nullable DoublePredicate validator;

    public FloatArrayConfigProperty(String name, String[] comments, float[] defaultValue, @Nullable DoublePredicate validator) {
        super(name, comments, float[].class);
        this.defaultValue = defaultValue;
        this.value = defaultValue;
        this.validator = validator;

        if (!isValid()) {
            throw new IllegalArgumentException("Default value for property '" + name + "' is invalid");
        }
    }

    public float[] getFloatArray() {
        return this.value;
    }

    public boolean setFloatArray(float[] value) {
        if (ArrayValidators.testAll(value, this.validator)) {
            this.value = value;
            return true;
        } else {
            ConfigManager.LOGGER.error("Failed to set property '{}': invalid array", getName());
            return false;
        }
    }

    public float[] getDefaultValue() {
        return this.defaultValue;
    }

    public void resetToDefault() {
        this.value = this.defaultValue;
    }

    @Override
    public boolean isValid() {
        return ArrayValidators.testAll(this.defaultValue, this.validator);
    }

}