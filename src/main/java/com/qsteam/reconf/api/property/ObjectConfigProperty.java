package com.qsteam.reconf.api.property;

import com.qsteam.reconf.config.ConfigManager;
import com.qsteam.reconf.util.PropertyUtils;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

public class ObjectConfigProperty<T> extends ConfigProperty {

    private volatile T value;
    private final @Nullable Predicate<String> validator;

    public ObjectConfigProperty(String name, List<String> comments, Class<?> type, T defaultValue, @Nullable Predicate<String> validator) {
        super(name, comments, type);
        this.value = defaultValue;
        this.validator = validator;

        if (!isValid()) {
            throw new IllegalArgumentException("Default value for property '" + name + "' is invalid");
        }
    }

    public T get() {
        return this.value;
    }

    public boolean set(T value) {
        if (validator == null || validator.test(PropertyUtils.serialize(value))) {
            this.value = value;
            return true;
        } else {
            ConfigManager.LOGGER.error("Failed to set property '{}': invalid value '{}'", getName(), value);
            return false;
        }
    }

    @Override
    public boolean isValid() {
        return validator == null || validator.test(PropertyUtils.serialize(this.value));
    }

}