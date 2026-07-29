package com.qsteam.reconf.api.property.array;

import com.qsteam.reconf.api.property.ConfigProperty;
import com.qsteam.reconf.config.ConfigManager;
import com.qsteam.reconf.util.property.ArrayValidators;
import it.unimi.dsi.fastutil.chars.CharPredicate;

public class CharArrayConfigProperty extends ConfigProperty {

    private final char[] defaultValue;
    private char[] value;
    private final CharPredicate validator;

    protected CharArrayConfigProperty(String name, String[] comments, char[] defaultValue, CharPredicate validator) {
        super(name, comments, char.class);
        this.defaultValue = defaultValue;
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
        if (ArrayValidators.testAll(value, this.validator)) {
            this.value = value;
            return true;
        } else {
            ConfigManager.LOGGER.error("Failed to set property '{}': invalid array", getName());
            return false;
        }
    }

    public char[] getDefaultValue() {
        return this.defaultValue;
    }

    public void resetToDefault() {
        this.value = defaultValue;
    }

    @Override
    public boolean isValid() {
        return ArrayValidators.testAll(this.defaultValue, this.validator);
    }
}
