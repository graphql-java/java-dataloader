package org.dataloader.fixtures.parameterized;

import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.provider.Arguments;

import java.util.stream.Stream;

@SuppressWarnings("unused")
public class TestDataLoaderFactories {

    public static Stream<Arguments> get() {
        return Stream.of(
                Arguments.of(Named.of("List DataLoader", new ListDataLoaderFactory())),
                Arguments.of(Named.of("Mapped DataLoader", new MappedDataLoaderFactory())),
                Arguments.of(Named.of("Publisher DataLoader", new PublisherDataLoaderFactory())),
                Arguments.of(Named.of("Mapped Publisher DataLoader", new MappedPublisherDataLoaderFactory())),

                // runs all the above via a DelegateDataLoader
                Arguments.of(Named.of("Delegate List DataLoader", new DelegatingDataLoaderFactory(new ListDataLoaderFactory()))),
                Arguments.of(Named.of("Delegate Mapped DataLoader", new DelegatingDataLoaderFactory(new MappedDataLoaderFactory()))),
                Arguments.of(Named.of("Delegate Publisher DataLoader", new DelegatingDataLoaderFactory(new PublisherDataLoaderFactory()))),
                Arguments.of(Named.of("Delegate Mapped Publisher DataLoader", new DelegatingDataLoaderFactory(new MappedPublisherDataLoaderFactory())))
        );
    }
}
