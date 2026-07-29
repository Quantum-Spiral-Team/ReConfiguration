package com.qsteam.reconf.api.property.array;

import com.qsteam.reconf.api.property.object.ObjectConfigProperty;
import com.qsteam.reconf.config.ConfigManager;
import com.qsteam.reconf.util.property.PropertyUtils;

import java.util.function.Predicate;

public class ObjectArrayConfigValue extends ObjectConfigProperty<Object[]> {

    public ObjectArrayConfigValue(String name, String[] comments, Object[] defaultValue, Predicate<String> validator) {
        super(name, comments, defaultValue.getClass(), defaultValue, validator);
    }

    @Override
    public boolean set(Object[] value) {
        if (this.testAllValues(value)) {
            this.value = value;
            return true;
        } else {
            ConfigManager.LOGGER.error("Failed to set property '{}': invalid value '{}'", getName(), value);
            return false;
        }
    }

    @Override
    public boolean isValid() {
        return this.testAllValues(this.defaultValue);
    }

    private boolean testAllValues(Object[] values) {
        if (this.validator == null) return true;

        for (Object o : values) {
            if (!this.validator.test(PropertyUtils.serialize(o))) {
                return false;
            }
        }
        return true;
    }
}
