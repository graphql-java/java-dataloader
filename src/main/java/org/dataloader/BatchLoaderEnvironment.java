package org.dataloader;

/**
 * This object is passed to a batch loader as calling context.  It could contain security credentials
 * of the calling users for example or database parameters that allow the data layer call to succeed.
 */
public class BatchLoaderEnvironment {

    private final Object context;

    private BatchLoaderEnvironment(Object context) {
        this.context = context;
    }

    @SuppressWarnings("unchecked")
    public <T> T getContext() {
        return (T) context;
    }

    public static Builder newBatchLoaderEnvironment() {
        return new Builder();
    }

    public static class Builder {
        private Object context;

        private Builder() {

        }

        public Builder context(Object context) {
            this.context = context;
            return this;
        }

        public BatchLoaderEnvironment build() {
            return new BatchLoaderEnvironment(context);
        }
    }
}
