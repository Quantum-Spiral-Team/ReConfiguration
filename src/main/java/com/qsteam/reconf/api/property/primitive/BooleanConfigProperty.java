package com.qsteam.reconf.api.property.primitive;

import com.qsteam.reconf.api.property.ConfigProperty;

public class BooleanConfigProperty extends ConfigProperty {

    private boolean value;

    public BooleanConfigProperty(String name, String[] comments, boolean defaultValue) {
        super(name, comments, boolean.class);
        this.value = defaultValue;
    }

    public boolean getBoolean() {
        return value;
    }

    public boolean setBoolean(boolean value) {
        this.value = value;
        return true;
    }

    @Override
    public boolean isValid() {
        return true;
    }
}
