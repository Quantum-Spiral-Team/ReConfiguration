package com.qsteam.reconf.config.meta;

import com.qsteam.reconf.api.ReConfig;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public record ConfigMeta(
        Class<?> configClass,
        String modId,
        String name,
        String dir,
        ReConfig.Type type,
        String rootCategory,
        List<FieldMeta> fields
) {
    public ConfigMeta(Class<?> configClass,
                      String modId,
                      @Nullable String name,
                      String dir,
                      ReConfig.Type type,
                      String rootCategory,
                      List<FieldMeta> fields
    ) {
        this.configClass = configClass;
        this.modId = modId;
        this.name = name == null || name.isEmpty() ? modId : name;
        this.dir = dir;
        this.type = type;
        this.rootCategory = rootCategory;
        this.fields = List.copyOf(fields);
    }
}