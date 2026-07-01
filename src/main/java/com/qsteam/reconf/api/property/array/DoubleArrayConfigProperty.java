package com.qsteam.reconf.api.property.array;

import com.qsteam.reconf.api.property.ConfigProperty;
import com.qsteam.reconf.config.ConfigManager;
import it.unimi.dsi.fastutil.doubles.DoublePredicate;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class DoubleArrayConfigProperty extends ConfigProperty {

    private volatile double[] value;
    private final @Nullable DoublePredicate validator;

    public DoubleArrayConfigProperty(String name, List<String> comments, double[] defaultValue, @Nullable DoublePredicate validator) {
        super(name, comments, double[].class);
        this.value = defaultValue;
        this.validator = validator;

        if (!isValid()) {
            throw new IllegalArgumentException("Default value for property '" + name + "' is invalid");
        }
    }

    public double[] getDoubleArray() {
        return this.value;
    }

    public boolean setDoubleArray(double[] value) {
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

    private boolean testAll(double[] value) {
        if (this.validator == null) return true;

        for (double i : value) {
            if (!this.validator.test(i)) {
                return false;
            }
        }
        return true;
    }

}