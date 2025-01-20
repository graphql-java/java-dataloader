package org.dataloader;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

public class DataLoaderBuilderTest {

    BatchLoader<String, Object> batchLoader1 = keys -> null;

    BatchLoader<String, Object> batchLoader2 = keys -> null;

    DataLoaderOptions defaultOptions = DataLoaderOptions.newOptions();
    DataLoaderOptions differentOptions = DataLoaderOptions.newOptions().setCachingEnabled(false);

    @Test
    void canBuildNewDataLoaders() {
        DataLoaderFactory.Builder<String, Object> builder = DataLoaderFactory.builder();
        builder.options(differentOptions);
        builder.batchLoadFunction(batchLoader1);
        DataLoader<String, Object> dataLoader = builder.build();

        assertThat(dataLoader.getOptions(), equalTo(differentOptions));
        assertThat(dataLoader.getBatchLoadFunction(), equalTo(batchLoader1));
        //
        // and we can copy ok
        //
        builder = DataLoaderFactory.builder(dataLoader);
        dataLoader = builder.build();

        assertThat(dataLoader.getOptions(), equalTo(differentOptions));
        assertThat(dataLoader.getBatchLoadFunction(), equalTo(batchLoader1));
        //
        // and we can copy and transform ok
        //
        builder = DataLoaderFactory.builder(dataLoader);
        builder.options(defaultOptions);
        builder.batchLoadFunction(batchLoader2);
        dataLoader = builder.build();

        assertThat(dataLoader.getOptions(), equalTo(defaultOptions));
        assertThat(dataLoader.getBatchLoadFunction(), equalTo(batchLoader2));
    }

    @Test
    void theDataLoaderCanTransform() {
        DataLoader<String, Object> dataLoader1 = DataLoaderFactory.newDataLoader(batchLoader1, defaultOptions);
        assertThat(dataLoader1.getOptions(), equalTo(defaultOptions));
        assertThat(dataLoader1.getBatchLoadFunction(), equalTo(batchLoader1));
        //
        // we can transform the data loader
        //
        DataLoader<String, Object> dataLoader2 = dataLoader1.transform(it -> {
            it.options(differentOptions);
            it.batchLoadFunction(batchLoader2);
        });

        assertThat(dataLoader2, not(equalTo(dataLoader1)));
        assertThat(dataLoader2.getOptions(), equalTo(differentOptions));
        assertThat(dataLoader2.getBatchLoadFunction(), equalTo(batchLoader2));
    }
}
