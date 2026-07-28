package com.qsteam.reconf.api.property.objectarray;

import com.qsteam.reconf.api.property.ConfigProperty;
import com.qsteam.reconf.config.ConfigManager;
import com.qsteam.reconf.util.property.ArrayTypeInspector;
import com.qsteam.reconf.util.property.PropertyUtils;
import it.unimi.dsi.fastutil.doubles.DoublePredicate;
import it.unimi.dsi.fastutil.longs.LongPredicate;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Array;
import java.util.function.Predicate;

public class NestedArrayConfigProperty extends ConfigProperty {

    private volatile Object value;
    private final int depth;
    private final Class<?> leafType;

    private final @Nullable Predicate<String> validator;
    private final @Nullable DoublePredicate doubleValidator;
    private final @Nullable LongPredicate longValidator;

    public NestedArrayConfigProperty(String name, String[] comments, Class<?> arrayType,
                                     Object defaultValue,
                                     @Nullable LongPredicate longValidator,
                                     @Nullable DoublePredicate doubleValidator,
                                     @Nullable Predicate<String> validator) {
        super(name, comments, arrayType);

        this.depth = ArrayTypeInspector.depth(arrayType);
        this.leafType = ArrayTypeInspector.leafType(arrayType);
        this.value = defaultValue;
        this.validator = validator;
        this.doubleValidator = doubleValidator;
        this.longValidator = longValidator;

        if (!isValid()) {
            throw new IllegalArgumentException("Default value for property '" + name + "' is invalid");
        }
    }

    public Object get() {
        return value;
    }

    public boolean set(Object newValue) {
        if (validateRecursive(newValue, this.depth)) {
            this.value = newValue;
            return true;
        } else {
            ConfigManager.LOGGER.error("Failed to set property '{}': invalid nested array", getName());
            return false;
        }
    }

    @Override
    public boolean isValid() {
        return validateRecursive(this.value, this.depth);
    }

    private boolean validateRecursive(Object array, int remainingDepth ) {
        if (array == null) return false;
        int len = Array.getLength(array);

        if (remainingDepth == 1) {
            for (int i = 0; i < len; i++) {
                if (!testLeaf(Array.get(array, i))) return false;
            }
        } else {
            for (int i = 0; i < len; i++) {
                Object sub =  Array.get(array, i);
                if (!validateRecursive(sub, remainingDepth - 1)) return false;
            }
        }
        return true;
    }

    private boolean testLeaf(Object value) {
        if (this.value == null) return this.validator == null || this.validator.test("null");
        if (this.leafType == boolean.class) return true;

        if (longValidator != null) {
            long asLong = value instanceof Character c ? c : ((Number) value).longValue();
            return longValidator.test(asLong);
        }
        if (doubleValidator != null) {
            return doubleValidator.test(((Number) value).doubleValue());
        }
        if (validator != null) {
            return this.leafType == String.class ? validator.test((String) value) : validator.test(PropertyUtils.serialize(value));
        }
        return true;
    }
}
