package com.qsteam.reconf.api.property;

public abstract class ConfigProperty {

    private final String name;
    private final String[] comments;
    private final Class<?> type;

    protected ConfigProperty(String name, String[] comments, Class<?> type) {
        this.name = name;
        this.comments = comments;
        this.type = type;
    }

    public abstract boolean isValid();

    public String getName() {
        return this.name;
    }

    public String[] getComments() {
        return this.comments;
    }

    public Class<?> getType() {
        return this.type;
    }

}