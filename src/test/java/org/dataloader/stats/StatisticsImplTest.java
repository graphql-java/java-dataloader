package org.dataloader.stats;

import org.junit.Test;

import java.util.Map;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

public class StatisticsImplTest {

    @Test
    public void combine_works() throws Exception {
        StatisticsImpl one = new StatisticsImpl(1, 5, 6, 2, 4, 3);
        StatisticsImpl two = new StatisticsImpl(6, 10, 6, 7, 9, 8);

        Statistics combine = one.combine(two);

        assertThat(combine.getLoadCount(), equalTo(7L));
        assertThat(combine.getBatchLoadCount(), equalTo(9L));
        assertThat(combine.getCacheHitCount(), equalTo(11L));
        assertThat(combine.getBatchLoadExceptionCount(), equalTo(13L));
        assertThat(combine.getLoadErrorCount(), equalTo(15L));
        assertThat(combine.getBatchInvokeCount(), equalTo(12L));
    }

    @Test
    public void to_map_works() throws Exception {

        StatisticsImpl one = new StatisticsImpl(10, 2, 11, 3, 4, 5);
        Map<String, Number> map = one.toMap();

        assertThat(map.get("loadCount"), equalTo(10L));
        assertThat(map.get("loadErrorCount"), equalTo(2L));
        assertThat(map.get("loadErrorRatio"), equalTo(0.2d));
        assertThat(map.get("batchInvokeCount"), equalTo(11L));
        assertThat(map.get("batchLoadCount"), equalTo(3L));
        assertThat(map.get("batchLoadRatio"), equalTo(0.3d));
        assertThat(map.get("batchLoadExceptionCount"), equalTo(4L));
        assertThat(map.get("batchLoadExceptionRatio"), equalTo(0.4d));
        assertThat(map.get("cacheHitCount"), equalTo(5L));
        assertThat(map.get("cacheHitRatio"), equalTo(0.5d));

    }
}