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

> **✅ COMPLETED**: All phases have been implemented. See status below.

### Phase 1: Core Package (`org.dataloader`) - High Priority

| File | Status | Notes |
|------|--------|-------|
| `Try.java` | ✅ Done | Added `@NullMarked`, marked throwable field as nullable, used `nonNull()` assertions |
| `DataLoaderOptions.java` | ✅ Done | Added `@NullMarked`, marked nullable fields and builder setters |
| `BatchLoaderContextProvider.java` | ✅ Done | Marked `getContext()` return as `@Nullable` |

### Phase 2: Implementation Package (`org.dataloader.impl`)

| File | Status | Notes |
|------|--------|-------|
| `Assertions.java` | ✅ Done | Added `@NullMarked`, marked `nonNull()` input parameter as nullable |
| `CompletableFutureKit.java` | ✅ Done | Added `@NullMarked`, marked `cause()` return as nullable |
| `PromisedValues.java` | ✅ Done | Added `@NullMarked`, marked nullable returns (`cause()`, `get()`) |
| `PromisedValuesImpl.java` | ✅ Done | Added `@NullMarked`, marked nullable returns |
| `NoOpValueCache.java` | ✅ Done | Added `@NullMarked`, added nullable type bound `V extends @Nullable Object` |
| `DataLoaderAssertionException.java` | ✅ Done | Added `@NullMarked` |

### Phase 3: Statistics Package (`org.dataloader.stats`)

| File | Status | Notes |
|------|--------|-------|
| `Statistics.java` | ✅ Done | Added `@NullMarked` |
| `StatisticsCollector.java` | ✅ Done | Added `@NullMarked`, marked context parameters as nullable |
| `SimpleStatisticsCollector.java` | ✅ Done | Added `@NullMarked` |
| `ThreadLocalStatisticsCollector.java` | ✅ Done | Added `@NullMarked` |
| `NoOpStatisticsCollector.java` | ✅ Done | Added `@NullMarked` |
| `DelegatingStatisticsCollector.java` | ✅ Done | Added `@NullMarked` |

#### Statistics Context Subpackage (`org.dataloader.stats.context`)

| File | Status | Notes |
|------|--------|-------|
| `IncrementBatchLoadCountByStatisticsContext.java` | ✅ Done | Added `@NullMarked`, nullable callContext |
| `IncrementBatchLoadExceptionCountStatisticsContext.java` | ✅ Done | Added `@NullMarked`, nullable callContexts |
| `IncrementCacheHitCountStatisticsContext.java` | ✅ Done | Added `@NullMarked`, nullable callContext |
| `IncrementLoadCountStatisticsContext.java` | ✅ Done | Added `@NullMarked`, nullable callContext |
| `IncrementLoadErrorCountStatisticsContext.java` | ✅ Done | Added `@NullMarked`, nullable callContext |

### Phase 4: Instrumentation Package (`org.dataloader.instrumentation`)

| File | Status | Notes |
|------|--------|-------|
| `DataLoaderInstrumentation.java` | ✅ Done | Added `@NullMarked`, nullable returns and loadContext param |
| `DataLoaderInstrumentationContext.java` | ✅ Done | Added `@NullMarked`, nullable `onCompleted` parameters |
| `DataLoaderInstrumentationHelper.java` | ✅ Done | Added `@NullMarked`, nullable parameters |
| `SimpleDataLoaderInstrumentationContext.java` | ✅ Done | Added `@NullMarked`, nullable fields and callbacks |
| `ChainedDataLoaderInstrumentation.java` | ✅ Done | Added `@NullMarked`, handle nullable contexts |

### Phase 5: Scheduler Package (`org.dataloader.scheduler`)

| File | Status | Notes |
|------|--------|-------|
| `BatchLoaderScheduler.java` | ✅ Done | Added `@NullMarked` |

### Phase 6: Registries Package (`org.dataloader.registries`)

| File | Status | Notes |
|------|--------|-------|
| `DispatchPredicate.java` | ✅ Done | Added `@NullMarked` |

### Phase 7: Reactive Package (`org.dataloader.reactive`)

| File | Status | Notes |
|------|--------|-------|
| `ReactiveSupport.java` | ✅ Done | Added `@NullMarked`, nullable callContexts |
| `AbstractBatchSubscriber.java` | ✅ Done | Added `@NullMarked`, nullable callContexts |
| `BatchSubscriberImpl.java` | ✅ Done | Added `@NullMarked` |
| `MappedBatchSubscriberImpl.java` | ✅ Done | Added `@NullMarked`, handle nullable futures |

### Phase 8: Annotations Package (`org.dataloader.annotations`)

| File | Status | Notes |
|------|--------|-------|
| `ExperimentalApi.java` | ⏭️ Skipped | Annotation definition doesn't need null annotations |
| `GuardedBy.java` | ⏭️ Skipped | Annotation definition doesn't need null annotations |
| `Internal.java` | ⏭️ Skipped | Annotation definition doesn't need null annotations |
| `PublicApi.java` | ⏭️ Skipped | Annotation definition doesn't need null annotations |
| `PublicSpi.java` | ⏭️ Skipped | Annotation definition doesn't need null annotations |
| `VisibleForTesting.java` | ⏭️ Skipped | Annotation definition doesn't need null annotations |

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

### Per-Phase Breakdown

| Phase | Files | Time Estimate | Justification |
|-------|-------|---------------|---------------|
| **Phase 1-2** (Core + Impl) | 8 files | 2-3 hours | `Try.java` and `DataLoaderOptions.java` require careful analysis of nullable generics and multiple nullable fields. Implementation classes need review of internal null handling. |
| **Phase 3** (Stats) | 11 files | 1-2 hours | Mostly straightforward classes with few nullable members. Context classes are simple records. |
| **Phase 4-5** (Instrumentation + Scheduler) | 6 files | 1-2 hours | Methods explicitly return nullable `DataLoaderInstrumentationContext`. Requires attention to null callback patterns. |
| **Phase 6-7** (Registries + Reactive) | 5 files | 1 hour | `DispatchPredicate` is simple. Reactive subscribers follow established patterns. |
| **Phase 8** (Annotations) | 6 files | 30 minutes | Annotation definitions rarely need null annotations; quick review only. |
| **Final verification** | N/A | 1-2 hours | Full build, test suite, and enable `AnnotatedPackages` option. |

### Time Estimate Rationale

Each file requires the following activities:

1. **Analysis** (~5-10 min/file): Review all fields, parameters, return types, and generic bounds
2. **Annotation** (~5-10 min/file): Add `@NullMarked` and `@Nullable` annotations  
3. **Compilation check** (~2-5 min/file): Run `./gradlew compileJava` and fix NullAway errors
4. **Iteration** (variable): Complex classes may need multiple rounds of fixes

**Complexity factors that increase time:**
- Generic types with nullable bounds (e.g., `Try<V>`, `DataLoader<K, V>`)
- Classes with many optional/nullable fields (e.g., `DataLoaderOptions`)
- Interfaces that return nullable values (e.g., `DataLoaderInstrumentation`)
- Chained builders or fluent APIs

**Factors that decrease time:**
- Simple data classes with all non-null fields
- Annotation definitions (usually no changes needed)
- Classes that already follow non-null patterns

### Total Estimated Effort

| Scenario | Hours | Conditions |
|----------|-------|------------|
| **Optimistic** | 6 hours | Experienced with JSpecify, no unexpected issues |
| **Expected** | 8 hours | Normal pace, some debugging of NullAway errors |
| **Pessimistic** | 10 hours | Complex edge cases, API design decisions needed |

**Recommendation**: Plan for 8 hours, which allows for one full working day or 2-3 focused sessions.

## References

- [JSpecify User Guide](https://jspecify.dev/docs/user-guide/)
- [NullAway Wiki](https://github.com/uber/NullAway/wiki)
- [JSpecify 1.0.0 Release Notes](https://jspecify.dev/blog/jspecify-1.0/)
