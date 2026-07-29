package com.qsteam.reconf.api.property.object;

import com.qsteam.reconf.api.property.ConfigProperty;
import com.qsteam.reconf.config.ConfigManager;
import it.unimi.dsi.fastutil.chars.CharPredicate;
import org.jetbrains.annotations.Nullable;

public class CharConfigProperty extends ConfigProperty {

    private final char defaultValue;
    private char value;
    private final @Nullable CharPredicate validator;

    public CharConfigProperty(String name, String[] comments, char defaultValue, @Nullable CharPredicate validator) {
        super(name, comments, char.class);
        this.defaultValue = defaultValue;
        this.value = defaultValue;
        this.validator = validator;

        if (!isValid()) {
            throw new IllegalArgumentException("Default value for property '" + name + "' is invalid");
        }
    }

    public boolean setChar(char value) {
        if (validator == null || validator.test(value)) {
            this.value = value;
            return true;
        } else {
            ConfigManager.LOGGER.error("Failed to set property '{}': invalid value '{}'", getName(), value);
            return false;
        }
    }

    public char getDefaultValue() {
        return this.defaultValue;
    }

    public char getChar() {
        return this.value;
    }

    public void resetToDefault() {
        this.value = this.defaultValue;
    }

    @Override
    public boolean isValid() {
        return validator == null || validator.test(this.defaultValue);
    }

}
