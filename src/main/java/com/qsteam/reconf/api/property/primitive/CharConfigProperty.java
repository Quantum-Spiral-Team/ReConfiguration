package com.qsteam.reconf.api.property.primitive;

import com.qsteam.reconf.api.property.ConfigProperty;
import com.qsteam.reconf.config.ConfigManager;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

public class CharConfigProperty extends ConfigProperty {

    private char value;
    private final @Nullable Predicate<String> validator;

    public CharConfigProperty(String name, List<String> comments, char defaultValue, @Nullable Predicate<String> validator) {
        super(name, comments, char.class);
        this.value = defaultValue;
        this.validator = validator;

        if (!isValid()) {
            throw new IllegalArgumentException("Default value for property '" + name + "' is invalid");
        }
    }

    public boolean setChar(char value) {
        if (validator == null || validator.test("" + value)) {
            this.value = value;
            return true;
        } else {
            ConfigManager.LOGGER.error("Failed to set property '{}': invalid value '{}'", getName(), value);
            return false;
        }
    }

    public char getChar() {
        return this.value;
    }

    @Override
    public boolean isValid() {
        return validator == null || validator.test("" + this.value);
    }

}
