package com.qsteam.reconf.config;

import com.qsteam.reconf.util.LogUtil;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.Set;

public class ConfigManager {

    public static final Set<String> CONFIG_NAMES = new HashSet<>();

    public static final Logger LOGGER = LogUtil.getLogger(ConfigManager.class);

}
