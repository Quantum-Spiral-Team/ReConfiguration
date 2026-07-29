package com.qsteam.reconf.api.property.object;

import com.qsteam.reconf.config.ConfigManager;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

public class StringConfigProperty extends ObjectConfigProperty<String> {

    public StringConfigProperty(String name, String[] comments, String defaultValue, @Nullable Predicate<String> validator) {
        super(name, comments, String.class, defaultValue, validator);
    }

    @Override
    public boolean set(String value) {
        if (validator == null || validator.test(value)) {
            this.value = value;
            return true;
        } else {
            ConfigManager.LOGGER.error("Failed to set property '{}': invalid value '{}'", getName(), value);
            return false;
        }
    }

    @Override
    public boolean isValid() {
        return validator != null && validator.test(this.defaultValue);
    }

}
