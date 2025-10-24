package org.dataloader;

import org.dataloader.annotations.PublicApi;
import org.jspecify.annotations.NullMarked;

@NullMarked
@PublicApi
public interface DispatchStrategy {

    DispatchStrategy NO_OP = new DispatchStrategy() {
    };

    default void loadCalled(DataLoader<?, ?> dataLoader) {

    }
}
