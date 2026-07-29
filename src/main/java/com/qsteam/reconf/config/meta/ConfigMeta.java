package com.qsteam.reconf.config.meta;

import com.qsteam.reconf.api.ReConfig;

import java.io.File;
import java.util.List;

public record ConfigMeta(
        Class<?> configClass,
        String modId,
        File file,
        ReConfig.Type type,
        String rootCategory,
        List<FieldMeta> fields
) {
    public ConfigMeta(Class<?> configClass,
                      String modId,
                      File file,
                      ReConfig.Type type,
                      String rootCategory,
                      List<FieldMeta> fields
    ) {
        this.configClass = configClass;
        this.modId = modId;
        this.file = file;
        this.type = type;
        this.rootCategory = rootCategory;
        this.fields = List.copyOf(fields);
    }
}