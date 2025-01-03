package org.dataloader.fixtures.parameterized;

import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.provider.Arguments;

import java.util.stream.Stream;

public class TestDataLoaderFactories {
    public static Stream<Arguments> get() {
        return Stream.of(
            Arguments.of(Named.of("List DataLoader", new ListDataLoaderFactory())),
            Arguments.of(Named.of("Mapped DataLoader", new MappedDataLoaderFactory())),
            Arguments.of(Named.of("Publisher DataLoader", new PublisherDataLoaderFactory())),
            Arguments.of(Named.of("Mapped Publisher DataLoader", new MappedPublisherDataLoaderFactory()))
        );
    }

    // TODO: Remove in favour of #get when ValueCache supports Publisher Factories.
    public static Stream<Arguments> getWithoutPublisher() {
        return Stream.of(
            Arguments.of(Named.of("List DataLoader", new ListDataLoaderFactory())),
            Arguments.of(Named.of("Mapped DataLoader", new MappedDataLoaderFactory()))
        );
    }
}
