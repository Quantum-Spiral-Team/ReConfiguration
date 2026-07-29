package com.qsteam.reconf.api.property.object;

import com.qsteam.reconf.api.property.ConfigProperty;
import com.qsteam.reconf.config.ConfigManager;
import com.qsteam.reconf.util.property.PropertyUtils;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

public class ObjectConfigProperty<T> extends ConfigProperty {

    protected final T defaultValue;
    protected volatile T value;
    protected final @Nullable Predicate<String> validator;

    public ObjectConfigProperty(String name, String[] comments, Class<?> type, T defaultValue, @Nullable Predicate<String> validator) {
        super(name, comments, type);
        this.defaultValue = defaultValue;
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

    public T getDefaultValue() {
        return this.defaultValue;
    }

    public void resetToDefault() {
        this.value = this.defaultValue;
    }

    @Override
    public boolean isValid() {
        return this.validator == null || this.validator.test(PropertyUtils.serialize(this.defaultValue));
    }

}