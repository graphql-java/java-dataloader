package org.dataloader;

import java.util.Collections;
import java.util.Map;

import static org.dataloader.impl.Assertions.nonNull;

/**
 * This object is passed to a batch loader as calling context.  It could contain security credentials
 * of the calling users for example or database parameters that allow the data layer call to succeed.
 */
public class BatchLoaderEnvironment {

    private final Object context;
    private final Map<Object, Object> keyContexts;

    private BatchLoaderEnvironment(Object context, Map<Object, Object> keyContexts) {
        this.context = context;
        this.keyContexts = keyContexts;
    }

    /**
     * Returns the overall context object provided by {@link org.dataloader.BatchLoaderContextProvider}
     *
     * @param <T> the type you would like the object to be
     *
     * @return a context object or null if there isn't one
     */
    @SuppressWarnings("unchecked")
    public <T> T getContext() {
        return (T) context;
    }

    /**
     * Each call to {@link org.dataloader.DataLoader#load(Object, Object)} or
     * {@link org.dataloader.DataLoader#loadMany(java.util.List, java.util.List)} can be given
     * a context object when it is invoked.  A map of them is present by this method.
     *
     * @return a map of key context objects
     */
    public Map<Object, Object> getKeyContexts() {
        return keyContexts;
    }

    public static Builder newBatchLoaderEnvironment() {
        return new Builder();
    }

    public static class Builder {
        private Object context;
        private Map<Object, Object> keyContexts = Collections.emptyMap();

        private Builder() {

        }

        public Builder context(Object context) {
            this.context = context;
            return this;
        }

        public Builder keyContexts(Map<Object, Object> keyContexts) {
            this.keyContexts = nonNull(keyContexts);
            return this;
        }

        public BatchLoaderEnvironment build() {
            return new BatchLoaderEnvironment(context, keyContexts);
        }
    }
}
