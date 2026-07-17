package com.qsteam.reconf.util.property;

public class ArrayTypeInspector {

    private  ArrayTypeInspector() {}

    public static int depth(Class<?> type) {
        int d = 0;
        while (type.isArray()) {
            d++;
            type = type.getComponentType();
        }
        return d;
    }

    public static Class<?> leafType(Class<?> type) {
        while (type.isArray()) {
            type = type.getComponentType();
        }
        return type;
    }

}
