package com.qsteam.reconf.config;

import com.qsteam.reconf.api.ReConfig;
import com.qsteam.reconf.util.LogUtil;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.discovery.ASMDataTable;
import org.slf4j.Logger;

public class ConfigManager {

    public static final Logger LOGGER = LogUtil.getLogger(ConfigManager.class);

    /** for {@link ReConfig.Type#INSTANCE} */
    public static void loadData(ASMDataTable data) {
        //TODO
    }

    /** for {@link ReConfig.Type#PER_WORLD} */
    public static void sync(String modId, World world) {
        //TODO
    }

    /** for {@link ReConfig.Type#INSTANCE} */
    public static void sync(String modId, ReConfig.Type type) {
        //TODO
    }

    /** for {@link ReConfig.Type#LAZY} */
    public static void sync(Class<?> clazz) {
        //TODO
    }

    public static void register(Class<?> clazz) {
        //TODO
    }
}
