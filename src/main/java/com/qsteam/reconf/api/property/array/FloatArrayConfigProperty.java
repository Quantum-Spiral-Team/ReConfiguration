package com.qsteam.reconf.api.property.array;

import com.qsteam.reconf.api.property.ConfigProperty;
import com.qsteam.reconf.config.ConfigManager;
import it.unimi.dsi.fastutil.doubles.DoublePredicate;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class FloatArrayConfigProperty extends ConfigProperty {

    private volatile float[] value;
    private final @Nullable DoublePredicate validator;

    public FloatArrayConfigProperty(String name, List<String> comments, float[] defaultValue, @Nullable DoublePredicate validator) {
        super(name, comments, float[].class);
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

    private boolean testAll(float[] value) {
        if (this.validator == null) return true;

        for (float i : value) {
            if (!this.validator.test(i)) {
                return false;
            }
        }
        return true;
    }

}