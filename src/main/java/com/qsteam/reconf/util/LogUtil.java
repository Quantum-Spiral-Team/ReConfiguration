package com.qsteam.reconf.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LogUtil {

    public static Logger getLogger(Class<?> clazz) {
        return getLogger(clazz.getSimpleName());
    }

    public static Logger getLogger(Class<?> clazz, String name) {
        return getLogger(clazz.getSimpleName() + "/" + name);
    }

    public static Logger getLogger(String name) {
        return LoggerFactory.getLogger(name);
    }

}
