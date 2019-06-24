package org.dataloader;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

public class JsonObject {

    private final Map<String, Object> values;

    public JsonObject() {
        values = new LinkedHashMap<>();
    }

    public JsonObject put(String key, Object value) {
        values.put(key, value);
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        JsonObject that = (JsonObject) o;

        return values.equals(that.values);
    }

    @Override
    public int hashCode() {
        return values.hashCode();
    }

    public Stream<Map.Entry<String, Object>> stream() {
        return values.entrySet().stream();
    }
}
