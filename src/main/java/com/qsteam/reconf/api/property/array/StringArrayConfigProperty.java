package com.qsteam.reconf.api.property.array;

import com.qsteam.reconf.api.property.object.ObjectConfigProperty;
import com.qsteam.reconf.config.ConfigManager;
import com.qsteam.reconf.util.property.ArrayValidators;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

public class StringArrayConfigProperty extends ObjectConfigProperty<String[]> {

    public StringArrayConfigProperty(String name, String[] comments, String[] defaultValue, @Nullable Predicate<String> validator) {
        super(name, comments, String[].class, defaultValue, validator);

        if (!isValid()) {
            throw new IllegalArgumentException("Default value for property '" + name + "' is invalid");
        }
    }

    @Override
    public boolean set(String[] value) {
        if (ArrayValidators.testAll(value, this.validator)) {
            this.value = value;
            return true;
        } else {
            ConfigManager.LOGGER.error("Failed to set property '{}': invalid value '{}'", getName(), value);
            return false;
        }
    }

    @Override
    public String[] get() {
        return value;
    }

    @Override
    public boolean isValid() {
        return ArrayValidators.testAll(this.defaultValue, this.validator);
    }

}
