package com.qsteam.reconf.api.property.primitivearray;

import com.qsteam.reconf.api.property.ConfigProperty;
import com.qsteam.reconf.config.ConfigManager;
import com.qsteam.reconf.util.property.ArrayValidators;
import it.unimi.dsi.fastutil.doubles.DoublePredicate;
import org.jetbrains.annotations.Nullable;

public class DoubleArrayConfigProperty extends ConfigProperty {

    private volatile double[] value;
    private final @Nullable DoublePredicate validator;

    public DoubleArrayConfigProperty(String name, String[] comments, double[] defaultValue, @Nullable DoublePredicate validator) {
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
        if (ArrayValidators.testAll(value, this.validator)) {
            this.value = value;
            return true;
        } else {
            ConfigManager.LOGGER.error("Failed to set property '{}': invalid array", getName());
            return false;
        }
    }

    @Override
    public boolean isValid() {
        return ArrayValidators.testAll(this.value, this.validator);
    }

}