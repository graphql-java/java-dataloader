# JSpecify Migration Plan for java-dataloader

This document outlines the plan for adding comprehensive [JSpecify](https://jspecify.dev/) nullness annotations to the java-dataloader library.

## Current State

The repository already has partial JSpecify integration:

- **JSpecify 1.0.0 dependency** is configured as an API dependency
- **NullAway** is configured with JSpecify mode (`JSpecifyMode=true`) and `OnlyNullMarked=true`
- **~21 files** are already annotated with `@NullMarked` and `@Nullable`
- OSGi bundle configuration makes JSpecify annotations optional for users

### Already Annotated Files

The following files already have `@NullMarked` annotations:

#### Core Package (`org.dataloader`)
- `DataLoader.java` ✅
- `DataLoaderFactory.java` ✅ (assumed based on usage)
- `DataLoaderHelper.java` ✅
- `DataLoaderRegistry.java` ✅
- `BatchLoader.java` ✅
- `BatchLoaderWithContext.java` ✅
- `BatchLoaderContextProvider.java` ✅
- `BatchLoaderEnvironment.java` ✅
- `BatchLoaderEnvironmentProvider.java` ✅
- `MappedBatchLoader.java` ✅
- `MappedBatchLoaderWithContext.java` ✅
- `BatchPublisher.java` ✅
- `BatchPublisherWithContext.java` ✅
- `MappedBatchPublisher.java` ✅
- `MappedBatchPublisherWithContext.java` ✅
- `CacheMap.java` ✅
- `CacheKey.java` ✅
- `ValueCache.java` ✅
- `ValueCacheOptions.java` ✅
- `DelegatingDataLoader.java` ✅
- `DispatchResult.java` ✅

#### Implementation Package (`org.dataloader.impl`)
- `DefaultCacheMap.java` ✅

#### Registries Package (`org.dataloader.registries`)
- `ScheduledDataLoaderRegistry.java` ✅

## Files Requiring Annotations

### Phase 1: Core Package (`org.dataloader`) - High Priority

| File | Status | Notes |
|------|--------|-------|
| `Try.java` | ❌ Needs annotation | Generic type `V` may be nullable; internal throwable field is nullable |
| `DataLoaderOptions.java` | ❌ Needs annotation | Many nullable fields (cacheKeyFunction, cacheMap, valueCache, batchLoaderScheduler) |

### Phase 2: Implementation Package (`org.dataloader.impl`)

| File | Status | Notes |
|------|--------|-------|
| `Assertions.java` | ❌ Needs annotation | `nonNull()` method accepts nullable input, returns non-null |
| `CompletableFutureKit.java` | ❌ Needs annotation | Review for nullable handling |
| `PromisedValues.java` | ❌ Needs annotation | Interface with nullable considerations |
| `PromisedValuesImpl.java` | ❌ Needs annotation | Implementation class |
| `NoOpValueCache.java` | ❌ Needs annotation | Implements `ValueCache` |
| `DataLoaderAssertionException.java` | ❌ Needs annotation | Exception class |

### Phase 3: Statistics Package (`org.dataloader.stats`)

| File | Status | Notes |
|------|--------|-------|
| `Statistics.java` | ❌ Needs annotation | Simple data class, likely all non-null |
| `StatisticsCollector.java` | ❌ Needs annotation | Interface |
| `SimpleStatisticsCollector.java` | ❌ Needs annotation | Implements `StatisticsCollector` |
| `ThreadLocalStatisticsCollector.java` | ❌ Needs annotation | Implements `StatisticsCollector` |
| `NoOpStatisticsCollector.java` | ❌ Needs annotation | Implements `StatisticsCollector` |
| `DelegatingStatisticsCollector.java` | ❌ Needs annotation | Implements `StatisticsCollector` |

#### Statistics Context Subpackage (`org.dataloader.stats.context`)

| File | Status | Notes |
|------|--------|-------|
| `IncrementBatchLoadCountByStatisticsContext.java` | ❌ Needs annotation | Context class |
| `IncrementBatchLoadExceptionCountStatisticsContext.java` | ❌ Needs annotation | Context class |
| `IncrementCacheHitCountStatisticsContext.java` | ❌ Needs annotation | Context class |
| `IncrementLoadCountStatisticsContext.java` | ❌ Needs annotation | Context class |
| `IncrementLoadErrorCountStatisticsContext.java` | ❌ Needs annotation | Context class |

### Phase 4: Instrumentation Package (`org.dataloader.instrumentation`)

| File | Status | Notes |
|------|--------|-------|
| `DataLoaderInstrumentation.java` | ❌ Needs annotation | Methods return `@Nullable DataLoaderInstrumentationContext` |
| `DataLoaderInstrumentationContext.java` | ❌ Needs annotation | Interface with nullable generics |
| `DataLoaderInstrumentationHelper.java` | ❌ Needs annotation | Helper with NOOP implementations |
| `SimpleDataLoaderInstrumentationContext.java` | ❌ Needs annotation | Simple implementation |
| `ChainedDataLoaderInstrumentation.java` | ❌ Needs annotation | Chains multiple instrumentations |

### Phase 5: Scheduler Package (`org.dataloader.scheduler`)

| File | Status | Notes |
|------|--------|-------|
| `BatchLoaderScheduler.java` | ❌ Needs annotation | Interface; `environment` parameter documented as possibly null |

### Phase 6: Registries Package (`org.dataloader.registries`)

| File | Status | Notes |
|------|--------|-------|
| `DispatchPredicate.java` | ❌ Needs annotation | Functional interface |

### Phase 7: Reactive Package (`org.dataloader.reactive`)

| File | Status | Notes |
|------|--------|-------|
| `ReactiveSupport.java` | ❌ Needs annotation | Factory methods for subscribers |
| `AbstractBatchSubscriber.java` | ❌ Needs annotation | Base subscriber implementation |
| `BatchSubscriberImpl.java` | ❌ Needs annotation | Subscriber implementation |
| `MappedBatchSubscriberImpl.java` | ❌ Needs annotation | Subscriber implementation |

### Phase 8: Annotations Package (`org.dataloader.annotations`)

| File | Status | Notes |
|------|--------|-------|
| `ExperimentalApi.java` | ❌ Consider | Annotation definition |
| `GuardedBy.java` | ❌ Consider | Annotation definition |
| `Internal.java` | ❌ Consider | Annotation definition |
| `PublicApi.java` | ❌ Consider | Annotation definition |
| `PublicSpi.java` | ❌ Consider | Annotation definition |
| `VisibleForTesting.java` | ❌ Consider | Annotation definition |

> **Note**: Annotation definitions typically don't need `@NullMarked` unless they have methods that return potentially null values.

## Implementation Strategy

### Step 1: Add `@NullMarked` to Each Class

For each file, add the `@NullMarked` annotation at the class level:

```java
import org.jspecify.annotations.NullMarked;

@NullMarked
public class MyClass {
    // ...
}
```

### Step 2: Mark Nullable Elements

For any parameter, return type, or field that can be null, add `@Nullable`:

```java
import org.jspecify.annotations.Nullable;

@NullMarked
public class Example {
    private @Nullable String optionalField;
    
    public @Nullable Result findResult(String key) {
        // ...
    }
    
    public void process(@Nullable String maybeNull) {
        // ...
    }
}
```

### Step 3: Handle Nullable Generic Type Parameters

For generic types where the type parameter can be null:

```java
// Type parameter V can be null
public class Try<V extends @Nullable Object> {
    private @Nullable V value;
}

// In DataLoader, V can be null
public class DataLoader<K, V extends @Nullable Object> {
    // ...
}
```

### Step 4: Verify with NullAway

After annotating each phase, run:

```bash
./gradlew compileJava
```

NullAway will report any nullability errors. Fix any issues before proceeding.

### Step 5: Enable Full Package Checking (Final Step)

Once all files are annotated, uncomment the following in `build.gradle`:

```gradle
option("NullAway:AnnotatedPackages", "org.dataloader")
```

This will enable strict null checking for the entire package.

## Testing Strategy

1. **Compile-time verification**: NullAway catches nullability issues during compilation
2. **Existing tests**: Run the full test suite to ensure no behavioral regressions
3. **IDE integration**: JSpecify annotations work with IDEs that support them (IntelliJ, Eclipse with plugins)

## Compatibility Considerations

### Binary Compatibility
- Adding JSpecify annotations is binary compatible
- The annotations are available at runtime but not required (resolution:=optional in OSGi)

### Source Compatibility
- Users who don't use null-checking tools will see no difference
- Users with NullAway/Checker Framework/IntelliJ will benefit from null checking

### API Design Decisions

When annotating the API, consider:

1. **Return types**: Should methods return `@Nullable` or use `Optional`?
   - Existing uses of `Optional` remain unchanged
   - Raw nullable returns get `@Nullable`

2. **Parameters**: Document and annotate nullable parameters
   - If the Javadoc says "can be null", add `@Nullable`

3. **Generic bounds**: For generics that can hold null values
   - Use `V extends @Nullable Object` pattern

## Priority Order

1. **Core public API** (`org.dataloader` package) - Users interact with these directly
2. **SPI interfaces** (`instrumentation`, `scheduler`) - Extension points
3. **Statistics** - Commonly used for monitoring
4. **Implementation details** (`impl`, `reactive`) - Internal but important for correctness

## Estimated Effort

- **Phase 1-2** (Core + Impl): ~8 files, 2-3 hours
- **Phase 3** (Stats): ~11 files, 1-2 hours
- **Phase 4-5** (Instrumentation + Scheduler): ~6 files, 1-2 hours
- **Phase 6-7** (Registries + Reactive): ~5 files, 1 hour
- **Phase 8** (Annotations): ~6 files, 30 minutes
- **Final verification and testing**: 1-2 hours

**Total estimated effort**: 6-10 hours

## References

- [JSpecify User Guide](https://jspecify.dev/docs/user-guide/)
- [NullAway Wiki](https://github.com/uber/NullAway/wiki)
- [JSpecify 1.0.0 Release Notes](https://jspecify.dev/blog/jspecify-1.0/)
