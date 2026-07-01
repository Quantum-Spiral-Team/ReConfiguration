package com.qsteam.reconf.util;

public class PropertyUtils {

    public static String serialize(Object value) {
        // TODO add serialize logic
        return value == null ? "" : value.toString();
    }

    public static Object deserialize(String value) {
        //TODO add deserialize logic
        return new Object();
    }

}
