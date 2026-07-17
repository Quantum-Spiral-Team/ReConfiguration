package com.qsteam.reconf.api.property.primitivearray;

import com.qsteam.reconf.api.property.ConfigProperty;

public class BooleanArrayConfigProperty extends ConfigProperty {

    private boolean[] value;

    protected BooleanArrayConfigProperty(String name, String[] comments, Class<?> type, boolean[] defaultValue) {
        super(name, comments, type);
        this.value = defaultValue;
    }

    public boolean[] getBooleanArray() {
        return this.value;
    }

    public void setBooleanArray(boolean[] value) {
        this.value = value;
    }

    @Override
    public boolean isValid() {
        return true;
    }
}
