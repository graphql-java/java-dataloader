package org.dataloader.instrumentation;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;

public class SimpleDataLoaderInstrumentationContextTest {

    @Test
    void canRunCompletedCodeAsExpected() {
        AtomicReference<Object> actual = new AtomicReference<>();
        AtomicReference<Object> actualErr = new AtomicReference<>();

        DataLoaderInstrumentationContext<Object> ctx = DataLoaderInstrumentationHelper.whenCompleted((r, err) -> {
            actualErr.set(err);
            actual.set(r);
        });

        ctx.onDispatched(); // nothing happens
        assertThat(actual.get(), nullValue());
        assertThat(actualErr.get(), nullValue());

        ctx.onCompleted("X", null);
        assertThat(actual.get(), Matchers.equalTo("X"));
        assertThat(actualErr.get(), nullValue());

        ctx.onCompleted(null, new RuntimeException());
        assertThat(actual.get(), nullValue());
        assertThat(actualErr.get(), Matchers.instanceOf(RuntimeException.class));
    }

    @Test
    void canRunOnDispatchCodeAsExpected() {
        AtomicBoolean dispatchedCalled = new AtomicBoolean();

        DataLoaderInstrumentationContext<Object> ctx = DataLoaderInstrumentationHelper.whenDispatched(() -> dispatchedCalled.set(true));

        ctx.onCompleted("X", null); // nothing happens
        assertThat(dispatchedCalled.get(), Matchers.equalTo(false));

        ctx.onDispatched();
        assertThat(dispatchedCalled.get(), Matchers.equalTo(true));
    }
}