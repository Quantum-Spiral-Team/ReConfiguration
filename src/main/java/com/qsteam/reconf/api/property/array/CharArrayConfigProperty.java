package com.qsteam.reconf.api.property.array;

import com.qsteam.reconf.api.property.ConfigProperty;
import com.qsteam.reconf.config.ConfigManager;

import java.util.List;
import java.util.function.Predicate;

public class CharArrayConfigProperty extends ConfigProperty {

    private char[] value;
    private final Predicate<String> validator;

    protected CharArrayConfigProperty(String name, List<String> comments, char[] defaultValue, Predicate<String> validator) {
        super(name, comments, char.class);
        this.value = defaultValue;
        this.validator = validator;

        if (!isValid()) {
            throw new IllegalArgumentException("Default value for property '" + name + "' is invalid");
        }
    }

    public char[] getCharArray() {
        return this.value;
    }

    public boolean setCharArray(char[] value) {
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

    private boolean testAll(char[] value) {
        if (this.validator == null) return true;

        for (char i : value) {
            if (!this.validator.test("" + i)) {
                return false;
            }
        }
        return true;
    }
}
