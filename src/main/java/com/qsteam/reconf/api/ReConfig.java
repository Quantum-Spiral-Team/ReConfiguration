package com.qsteam.reconf.api;

import net.minecraftforge.fml.relauncher.Side;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ReConfig {
    /**
     * The mod id that this configuration is associated with.
     */
    String modid() default "";

    /**
     * A user friendly name for the config file,
     * the default will be modid
     */
    String name() default "";

    String dir() default "config";

    /**
     * The type this is, right now the only value is INSTANCE.
     * This is intended to be expanded upon later for more Forge controlled
     * configs.
     */
    Type type() default Type.INSTANCE;

    /**
     * {@code Side.CLIENT} - {@code {name}-client.cfg}
     * {@code Side.SERVER} - {@code {name}-server.cfg}
     */
    Side[] sides() default {Side.CLIENT, Side.SERVER};

    /**
     * Root element category, defaults to "general", if this is an empty string then the root category is disabled.
     * Any primitive fields will cause an error, and you must specify sub-category objects
     */
    String category() default "general";

    enum Type {
        /**
         * Loaded once, directly after mod construction. Before pre-init.
         * This class must have static fields.
         */
        INSTANCE(true),

        /**
         * Loaded lazily on demand, specifically during the configuration class initialization ({@code <clinit>}).
         * <p>
         * Emulates the behavior of {@code ConfigAnytime} without requiring a manual static initialization block.
         */
        LAZY(true),

        /**
         * Loaded dynamically when a world starts loading.
         * <p>
         * Isolates configuration data on a per-world basis (creating separate categories
         * for each world save), mimicking the modern NeoForge/Forge world-scoped configuration system.
         */
        PER_WORLD(true),
        ;

        private boolean isStatic = true;

        Type(boolean isStatic) {
            this.isStatic = isStatic;
        }

        public boolean isStatic() {
            return this.isStatic;
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.TYPE})
    @interface LangKey {
        String value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @interface Comment {
        String[] value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @interface Ignore {}

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @interface RangeInt {
        long min() default Long.MIN_VALUE;
        long max() default Long.MAX_VALUE;
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @interface RangeDouble {
        double min() default -Double.MAX_VALUE;
        double max() default Double.MAX_VALUE;
    }

    //TODO add the logic with ConditionParser
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @interface Range {
        String range() default "";
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @interface Name {
        String value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.TYPE})
    @interface RequiresMcRestart {}

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.TYPE})
    @interface RequiresWorldRestart {}

    /**
     * A field marked with this annotation (and {@link RangeInt} or {@link RangeDouble} or {@link Range}) will have a slider control attached in the config UI
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @interface SlidingOption {}
}