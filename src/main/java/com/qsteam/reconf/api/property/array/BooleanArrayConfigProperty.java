package com.qsteam.reconf.api.property.array;

import com.qsteam.reconf.api.property.ConfigProperty;

public class BooleanArrayConfigProperty extends ConfigProperty {

    private final boolean[] defaultValue;
    private boolean[] value;

    protected BooleanArrayConfigProperty(String name, String[] comments, Class<?> type, boolean[] defaultValue) {
        super(name, comments, type);
        this.defaultValue = defaultValue;
        this.value = defaultValue;
    }

    public boolean[] getBooleanArray() {
        return this.value;
    }

    public void setBooleanArray(boolean[] value) {
        this.value = value;
    }

    public boolean[] getDefaultValue() {
        return this.defaultValue;
    }

    public void resetToDefault() {
        this.value = defaultValue;
    }

    @Override
    public boolean isValid() {
        return true;
    }
}
