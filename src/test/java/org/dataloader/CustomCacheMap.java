package org.dataloader;

import java.util.LinkedHashMap;
import java.util.Map;

public class CustomCacheMap implements CacheMap<String, Object> {

    public Map<String, Object> stash;

    public CustomCacheMap() {
        stash = new LinkedHashMap<>();
    }

    @Override
    public boolean containsKey(String key) {
        return stash.containsKey(key);
    }

    @Override
    public Object get(String key) {
        return stash.get(key);
    }

    @Override
    public CacheMap<String, Object> set(String key, Object value) {
        stash.put(key, value);
        return this;
    }

    @Override
    public CacheMap<String, Object> delete(String key) {
        stash.remove(key);
        return this;
    }

    @Override
    public CacheMap<String, Object> clear() {
        stash.clear();
        return this;
    }
}
