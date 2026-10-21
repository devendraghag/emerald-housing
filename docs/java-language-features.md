# Java language and collection features in this repository

The code uses Java 21. These are concrete application examples for interviews,
not features added to domain entities solely for demonstration.

| Feature | Example | Source |
| --- | --- | --- |
| Lambda | `sort -> sort.isAscending() ? cb.asc(...) : cb.desc(...)` builds query orders | Both services' `api/DirectoryQueries.java` |
| Method reference | `FlatMembershipEntity::getFlatId` maps memberships to flat IDs | Member `auth/AccountIdentityService.java` |
| Constructor reference | `ArrayList::new` supplies a collection to a collector | Both `api/DirectoryQueries.java` files |
| Streams | `filter`, `map`, `sorted`, `collect` and `toList` transform/query in-memory values | Directory queries, account identity and service response mapping |
| Collection factory methods | `List.of(key)`, `Set.of("ACTIVE", "INACTIVE")`, `Map.of(...)` | Membership lock, query validation and API errors |
| Defensive copy factories | `List.copyOf(...)` snapshots cursor rows; `Set.copyOf(...)` protects identity scope | Directory queries and identity records |
| Mutable list collector | `Collectors.toCollection(ArrayList::new)` builds query orders before appending the ID tie-breaker | Both `api/DirectoryQueries.java` files |
| Unmodifiable result | `Stream.toList()` returns an unmodifiable list; `Collectors.toUnmodifiableSet()` returns an unmodifiable set | Query scope construction, response mapping and account identity |
| Comparator | `Comparator.naturalOrder()` explicitly selects natural UUID order for scope canonicalization | Both `api/DirectoryQueries.java` files |
| Comparable | UUID's built-in natural order supplies the comparison used by that comparator; Duration also provides `compareTo` | Query scopes and Society cache duration validation |
| Functional interfaces | `Supplier<T>` wraps protected operations; `Function<S,T>` maps page results | RedisMembershipLock, SocietyCacheMetrics and PageResponse |
| Static methods | `PageResponse.from(...)` creates a response without an instance; `MembershipPeriod.currentOn(date)` builds a predicate | PageResponse and Member `entity/MembershipPeriod.java` |
| Interface default methods | `isCurrentOn(date)` supplies reusable membership-access behavior to FlatMembershipEntity | Member `entity/MembershipPeriod.java` |
| Private interface methods | `hasStartedOn(date)` encapsulates part of the default policy and is not exposed to callers | Member `entity/MembershipPeriod.java` |
| Private static methods | `MembershipWriter.response(...)` maps entity data without using instance state | Member `service/MembershipWriter.java` |
| Records | Request/response DTOs, cursor positions, identity records and application events | Both services and Gateway |
| Record compact constructors | PageResponse/CursorPage take shallow unmodifiable list snapshots with List.copyOf | Both services' page and cursor records |
| Pattern-matching instanceof | `principal instanceof SessionIdentityResponse actor` validates and binds the type without a separate cast | MemberAccess, SocietyAccess, audits and Gateway security |
| ExecutorService / Executors | A Spring-managed ExecutorService owns the Society HTTP client's virtual-thread tasks | Member SocietyRestClientConfig |
| Virtual threads | A named Thread.ofVirtual factory creates one virtual thread per submitted HTTP-client task | Member SocietyRestClientConfig |
| IntStream | rangeClosed generates numbered concurrent executor test tasks without boxing until a comparison needs it | Member SocietyRestClientConfigTest |
| Bounded infinite stream | Stream.iterate(...).limit(3) creates a finite executor test batch | Member SocietyRestClientConfigTest |

## Important distinctions

- Java has no `List.modifiableList()` factory. Use `new ArrayList<>()`,
  `new ArrayList<>(existing)` or `Collectors.toCollection(ArrayList::new)` when
  later changes are required. Query ordering needs a mutable list because an ID
  tie-breaker is appended after mapping client-supplied sort orders.
- `List.of`, `List.copyOf`, `Set.of` and `Map.of` produce unmodifiable collections.
  Their elements/values are not necessarily deeply immutable. Copy factories make
  a shallow snapshot and may reuse an already suitable unmodifiable input.
- `Stream.toList()` is unmodifiable but permits null elements. `List.of`,
  `List.copyOf` and `Collectors.toUnmodifiableList()` reject null elements.
  `Collectors.toList()` does not guarantee a particular implementation or mutability.
- `Collections.unmodifiableList(existing)` is a read-only view: changes made through
  the original list remain visible. It is not the same as `List.copyOf(existing)`.
  The application does not need an extra view wrapper where it already returns
  an unmodifiable stream result or a defensive copy.
- `Comparable` defines a type's natural order through `compareTo`.
  `Comparator` selects an external ordering strategy. Existing UUID, Duration,
  Instant and other JDK types already supply natural orders; no custom domain
  `implements Comparable` class is needed for the current workflows.
- UUID sorting in cursor scope construction makes the access-scope fingerprint
  deterministic. It does not perform database pagination. PostgreSQL still sorts
  and filters the actual cursor query; do not replace it with Java-side sorting.

## Short interview answer

“I use lambdas and method references with streams to map records and build query
orders. Collection factories create fixed values, and copy factories protect
returned identity and cursor collections. I collect into ArrayList when I need to
append an ordering tie-breaker. Comparator.naturalOrder uses UUID's Comparable
implementation to canonicalize the access scope. I choose mutable or unmodifiable
collections according to their purpose rather than using one everywhere.”

## Static, default and private methods

- **Static:** belongs to the class/interface, not an instance. Use it for factories
  and operations that need no object state. Call `MembershipPeriod.currentOn(date)`
  on the interface; it returns a predicate for a stream's `filter`.
- **Default:** an interface method with an inherited implementation. The entity
  implements the period's date accessors and inherits `isCurrentOn(date)`.
  This method uses that particular entity's dates. Default methods arrived in Java 8.
- **Private:** implementation detail available only inside its declaring
  class/interface. The interface's `hasStartedOn(date)` helper supports the default
  method without becoming part of the public contract. Private interface methods
  arrived in Java 9 and are supported by this Java 21 project.
- **Private static:** combine restricted visibility with no instance state. Existing
  entity-to-response mappers are examples. Static/private helpers do not receive
  instance-based Spring proxy interception; retain transaction and method-security
  boundaries on suitable instance service methods.

The resident flat lookup now reads:

```java
.filter(MembershipPeriod.currentOn(today))
.map(FlatMembershipEntity::getFlatId)
.collect(Collectors.toUnmodifiableSet());
```

The policy remains the previous one: a membership must have started and must have
no end date. A future-dated start does not yet grant access; any explicitly recorded
end date removes current access. It is not a general historical date-range check.
The caller still supplies the UTC reference date used for account validation.

**Interview answer:** “I use static methods for factories, default interface methods
for reusable instance behavior, and private methods for internal implementation.
For example, a membership inherits a default current-access check, a static factory
turns that check into a stream predicate, and a private helper checks its start date.
I keep transaction and authorization methods as Spring-managed instance methods.”

## Records, instanceof and collection interfaces

- **Record:** a concise data carrier with generated accessors, equals, hashCode and
  toString. It is not automatically deeply immutable. Page and cursor records now
  copy their lists so caller mutation cannot change the response's list structure.
  Their JSON fields remain unchanged. List.copyOf rejects null lists/elements.
  Entity objects are still mutable JPA classes rather than being converted to records.
- **Pattern-matching instanceof:** checks the runtime type and binds a typed variable
  in one expression. Existing security and resilience checks already use it.
- **Interfaces versus implementations:** declare `List<Order>` while collecting into
  `ArrayList<Order>` when an ID ordering tie-breaker must be appended. Existing
  code also uses Set, Map, Collection, ConcurrentMap, HashMap and ConcurrentHashMap.
  Choose each implementation for ordering, uniqueness or concurrent access needs;
  do not replace them simply to demonstrate another class.

## Functional interfaces in real workflows

A functional interface has one abstract method and can be implemented with a lambda
or method reference. Default, static and private methods do not add abstract methods.

| Interface | Method | Repository example |
| --- | --- | --- |
| Function<S,T> | apply | PageResponse.from accepts a mapping function |
| Supplier<T> | get | RedisMembershipLock and SocietyCacheMetrics wrap protected operations |
| Predicate<T> | test | MembershipPeriod.currentOn builds a filter policy |
| Consumer<T> | accept | forEach processes cache names or sort entries |
| Comparator<T> | compare | Natural ordering canonicalizes the flat-access scope |
| Callable<T> | call | ExecutorService.submit accepts result-producing HTTP-executor test tasks |
| Runnable | run | ExecutorService.execute accepts work with no result; the HTTP client schedules internal work |
| ThreadFactory | newThread | The configured factory creates named virtual threads |

There is no need to wrap these standard contracts in an unused custom functional
interface solely for interview coverage. Collectors is a utility class; Collector
is the interface describing reduction into a result container. Streams are lazy
until a terminal operation, and parallelStream is not a virtual-thread executor.

## Virtual threads and executor lifecycle

Member's SocietyRestClientConfig registers a dedicated ExecutorService backed by:

```java
Executors.newThreadPerTaskExecutor(
    Thread.ofVirtual().name("society-http-", 0).factory());
```

HttpClient uses it for asynchronous/dependent transport tasks. Spring owns both
resources and closes the HTTP client before closing its executor. The executor is
marked `defaultCandidate=false` so it does not replace Boot's general application
executor. This is a live application use, not an unused demonstration bean.

This does **not** enable virtual threads for every servlet request, change database
transactions into async transactions, or replace Gateway's Reactor event loop.
Security context and transactions remain on the calling business request. Existing
Society bulkhead, retry and timeout policies continue to limit logical calls. The
executor itself creates an unbounded number of lightweight threads; it is not a
concurrency limiter and does not increase database capacity or speed up CPU work.
No throughput improvement is claimed without measurement.

Tests check virtual task execution, resource closure, actual HTTP transport and
read-timeout behavior. Java 21 does not require preview flags for virtual threads.

## IntStream and infinite streams

Interpreting “IninitStream” as IntStream, the executor test generates numbered tasks:

```java
IntStream.rangeClosed(1, 8)
    .mapToObj(index -> executor.submit(() ->
        new TaskObservation(index, Thread.currentThread().isVirtual())))
    .toList();
```

IntStream is a primitive-int stream. range excludes its upper bound, rangeClosed
includes it, boxed converts to Stream<Integer>, and mapToObj creates object results.
The example is intentionally in a concurrency test; the application has no new
numeric business loop that needs an artificial IntStream refactor.

If “IninitStream” meant an **infinite stream**, the test also covers a bounded form:

```java
Stream.iterate(1, index -> index + 1).limit(3);
```

The two-argument iterate and generate can produce unbounded streams. Add a finite
termination policy before collecting or submitting tasks. Do not put an unbounded
stream into an API request or use it to enqueue unlimited executor work.

**Interview answer:** “I use records for API data, pattern-matching instanceof for
typed checks, and standard functional interfaces with streams. A dedicated managed
virtual-thread ExecutorService handles the Society HTTP client's transport tasks.
I retain timeouts, bulkheads and database limits. IntStream and a limited infinite
stream generate finite task batches in tests.”

References: [Java 21 Executors](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Executors.html),
[HttpClient executor contract](https://docs.oracle.com/en/java/javase/21/docs/api/java.net.http/java/net/http/HttpClient.Builder.html#executor(java.util.concurrent.Executor)),
[Spring Boot custom executors](https://docs.spring.io/spring-boot/reference/features/task-execution-and-scheduling.html).
