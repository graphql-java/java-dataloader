package org.dataloader.reactive;

import org.dataloader.stats.StatisticsCollector;

import java.util.List;

/**
 * Just some callbacks to the data loader code to do common tasks
 *
 * @param <K> for keys
 */
public interface HelperIntegration<K> {

    StatisticsCollector getStats();

    void clearCacheView(K key);

    void clearCacheEntriesOnExceptions(List<K> keys);
}
