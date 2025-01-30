package org.dataloader;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

class ValueCacheOptionsTest {

    @Test
    void saneDefaults() {
        ValueCacheOptions newOptions = ValueCacheOptions.newOptions();
        assertThat(newOptions.isCompleteValueAfterCacheSet(), equalTo(false));

        ValueCacheOptions differentOptions = newOptions.setCompleteValueAfterCacheSet(true);
        assertThat(differentOptions.isCompleteValueAfterCacheSet(), equalTo(true));
        assertThat(differentOptions == newOptions, equalTo(false));
    }
}