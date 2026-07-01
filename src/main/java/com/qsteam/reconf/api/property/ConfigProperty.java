package com.qsteam.reconf.api.property;

import java.util.List;

public abstract class ConfigProperty {

    private final String name;
    private final List<String> comments;
    private final Class<?> type;

    protected ConfigProperty(String name, List<String> comments, Class<?> type) {
        this.name = name;
        this.comments = comments;
        this.type = type;
    }

    public abstract boolean isValid();

    public String getName() {
        return this.name;
    }

    public List<String> getComments() {
        return this.comments;
    }

    public Class<?> getType() {
        return this.type;
    }

}