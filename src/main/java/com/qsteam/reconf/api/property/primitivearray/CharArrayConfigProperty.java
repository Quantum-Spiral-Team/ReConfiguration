package com.qsteam.reconf.api.property.primitivearray;

import com.qsteam.reconf.api.property.ConfigProperty;
import com.qsteam.reconf.config.ConfigManager;
import com.qsteam.reconf.util.property.ArrayValidators;
import it.unimi.dsi.fastutil.longs.LongPredicate;

public class CharArrayConfigProperty extends ConfigProperty {

    private char[] value;
    private final LongPredicate validator;

    protected CharArrayConfigProperty(String name, String[] comments, char[] defaultValue, LongPredicate validator) {
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
