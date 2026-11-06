/* Immutable registry of one-way analysis result encoders. MIT License. */
package com.kniazkov.cpg.passes;

import com.kniazkov.cpg.lattice.LatticeElement;
import com.kniazkov.json.JsonElement;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import java.util.function.Function;

/** One-way JSON conversion for typed result values. Registrations are exact declared
 * classes and override the default conversions, including {@link LatticeElement#toJson}.
 * Each {@link #with} call creates a new
 * registry; it never changes an existing runner.
 *
 * <p>Encoders return JSON-compatible strings, booleans, finite standard Java numbers,
 * null, lists, string-keyed maps, or {@link JsonElement} trees. Their output is copied
 * and validated before publication, with object keys sorted for deterministic export
 * and nesting limited to 256 levels.
 * Encoders are never called by JSON export, and no decoder/importer is required.
 * Typed values themselves must obey {@link ResultKey}'s immutable-value contract.</p> */
public final class ResultTypes {
    private final Map<Class<?>, Function<Object, ?>> encoders;

    private ResultTypes(Map<Class<?>, Function<Object, ?>> encoders) {
        this.encoders = Map.copyOf(encoders);
    }

    /** Built-in conversions for String, Boolean, Byte, Short, Integer, Long, Float,
     * Double, BigInteger and BigDecimal. A Number key also accepts these numeric values;
     * other Number subclasses require an explicit encoder returning a supported value.
     * Any {@link LatticeElement} value is converted through its public {@code toJson()}
     * contract without registration, whether its key declares the interface or a concrete
     * implementation. Lattice output is validated and frozen just like registered output. */
    public static ResultTypes defaults() {
        return new ResultTypes(Map.of());
    }

    /** Adds or replaces the conversion for one exact declared result type. */
    public <T> ResultTypes with(Class<T> type, Function<? super T, ?> encoder) {
        Objects.requireNonNull(type, "Result type");
        Objects.requireNonNull(encoder, "Result encoder");
        if (type.isPrimitive()) throw new IllegalArgumentException("Use a boxed result type");
        var changed = new HashMap<>(encoders);
        changed.put(type, value -> encoder.apply(type.cast(value)));
        return new ResultTypes(changed);
    }

    /** Converts once into a deeply immutable, detached JSON-compatible value.
     * A failure aborts publication; there is no reflective fallback or stringification.
     * The encoded value may be JSON null, although a typed result value must not be null. */
    public Object encode(Class<?> type, Object value) {
        Objects.requireNonNull(type, "Result type");
        Objects.requireNonNull(value, "Result value");
        if (!type.isInstance(value))
            throw new IllegalArgumentException("Result value does not match " + type.getName());
        Function<Object, ?> encoder = encoders.get(type);
        Object encoded;
        if (encoder != null) encoded = encoder.apply(value);
        else if (value instanceof LatticeElement element)
            encoded = Objects.requireNonNull(element.toJson(), "Lattice JSON representation");
        else if (type == String.class || type == Boolean.class || type == Number.class || numericType(type))
            encoded = value;
        else throw new IllegalArgumentException("No JSON encoder registered for " + type.getName());
        return freeze(encoded, Collections.newSetFromMap(new IdentityHashMap<>()), 0);
    }

    public <T> Object encode(ResultKey<T> key, T value) {
        return encode(Objects.requireNonNull(key, "Result key").type(), value);
    }

    private static boolean numericType(Class<?> type) {
        return type == Byte.class || type == Short.class || type == Integer.class || type == Long.class
                || type == Float.class || type == Double.class || type == BigInteger.class || type == BigDecimal.class;
    }

    private static Object freeze(Object value, Set<Object> active, int depth) {
        if (depth > 256) throw new IllegalArgumentException("JSON result exceeds maximum nesting depth");
        if (value == null || value instanceof String || value instanceof Boolean) return value;
        if (value instanceof Number number) {
            if (!numericType(number.getClass())
                    || number instanceof Double n && !Double.isFinite(n)
                    || number instanceof Float n && !Float.isFinite(n))
                throw new IllegalArgumentException("JSON result needs a finite standard Java number");
            return number;
        }
        if (!active.add(value)) throw new IllegalArgumentException("Cyclic JSON result value");
        try {
            if (value instanceof JsonElement json) return freeze(json.toJavaObject(), active, depth + 1);
            if (value instanceof Map<?, ?> map) {
                var copy = new TreeMap<String, Object>();
                for (var entry : map.entrySet()) {
                    if (!(entry.getKey() instanceof String name))
                        throw new IllegalArgumentException("JSON result object keys must be strings");
                    copy.put(name, freeze(entry.getValue(), active, depth + 1));
                }
                return Collections.unmodifiableMap(copy);
            }
            if (value instanceof List<?> list) {
                var copy = new ArrayList<Object>(list.size());
                for (Object item : list) copy.add(freeze(item, active, depth + 1));
                return Collections.unmodifiableList(copy);
            }
            throw new IllegalArgumentException("Unsupported JSON result value: " + value.getClass().getName());
        } finally {
            active.remove(value);
        }
    }
}
