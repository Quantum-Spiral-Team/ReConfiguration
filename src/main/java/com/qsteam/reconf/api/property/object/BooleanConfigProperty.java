package com.qsteam.reconf.api.property.object;

import com.qsteam.reconf.api.property.ConfigProperty;

public class BooleanConfigProperty extends ConfigProperty {

    private final boolean defaultValue;
    private boolean value;

    public BooleanConfigProperty(String name, String[] comments, boolean defaultValue) {
        super(name, comments, boolean.class);
        this.defaultValue = defaultValue;
        this.value = defaultValue;
    }

    public boolean getBoolean() {
        return value;
    }

    public boolean setBoolean(boolean value) {
        this.value = value;
        return true;
    }

    public boolean getDefaultValue() {
        return this.defaultValue;
    }

    public void resetToDefault() {
        this.value = this.defaultValue;
    }

    @Override
    public boolean isValid() {
        return true;
    }
}
