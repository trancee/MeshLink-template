---
name: test-analysis-extensions
description: Provides per-language reference files for the polyglot test analysis skills (test-anti-patterns, test-gap-analysis, assertion-quality, test-smell-detection, test-tagging, grade-tests). Loads extensions/kotlin.md for Kotlin (JUnit 5, Kotest, MockK) with test markers, assertion APIs, sleep patterns, skip annotations, mystery guest indicators, integration markers, setup/teardown, and tag support. Not used directly — invoked by analysis skills that need framework-specific lookup tables.
license: MIT
---

<objective>
Provide framework-specific reference data used by the polyglot test analysis
skills. Not invoked directly — called by `test-anti-patterns`,
`test-gap-analysis`, `assertion-quality`, `test-smell-detection`,
`test-tagging`, and `grade-tests` to discover language/framework-specific
patterns.

Available extension files:
- `kotlin.md` — Kotlin (JUnit 5, Kotest, MockK, Spek, TestNG)
- `dotnet.md`, `python.md`, `typescript.md`, `java.md`, `go.md`, `ruby.md`,
  `rust.md`, `swift.md`, `cpp.md`, `powershell.md`

This skill is a **reference-only** skill — it loads extension files so other
skills can look up framework-specific assertion APIs, test markers, sleep
patterns, skip annotations, mystery-guest indicators, integration markers,
setup/teardown lifecycles, and tag-support capabilities per language.
</objective>

<quick_start>
1. List available extension files
2. Read the file matching the target codebase's language (e.g., `kotlin.md` for Kotlin)
3. Return the relevant sections to the calling skill
</quick_start>

## Kotlin Extension Data

The following data is used by the polyglot test analysis skills when analyzing
Kotlin code in this repository.

### Capability Tags

| Capability | Support |
|---|---|
| Test discovery | Strong — JUnit 5 conventions, Kotest spec classes |
| Assertion detection | Strong — JUnit + Kotest matchers + MockK verifications |
| Sleep/delay detection | Strong — `Thread.sleep`, `delay()` |
| Skip/ignore detection | Strong — `@Disabled`, `.config(enabled = false)` |
| Setup/teardown detection | Strong — JUnit + Kotest lifecycle |
| Tag support | **auto-edit** — JUnit 5 `@Tag`, Kotest `tags`, project-defined |

### Test File Identification

| Framework | File convention | Test method markers |
|---|---|---|
| JUnit 5 (Jupiter) | `*Test.kt`, `*Tests.kt`, `*IT.kt` | `@Test fun foo()` |
| Kotest | `*Spec.kt` (any style) | inherits a spec class (`StringSpec`, `FunSpec`, `BehaviorSpec`, `ShouldSpec`, `DescribeSpec`, `FeatureSpec`, `WordSpec`, `FreeSpec`, `AnnotationSpec`) |
| Spek | `*Spec.kt` | `object FooSpec : Spek({ ... })` |
| TestNG | `*Test.kt` | `@Test fun foo()` (TestNG annotation) |

### Assertion APIs

| Category | JUnit 5 | Kotest matchers | AssertK |
|---|---|---|---|
| Equality | `assertEquals(expected, actual)` | `actual shouldBe expected` | `assertThat(actual).isEqualTo(expected)` |
| Boolean | `assertTrue(b)` / `assertFalse(b)` | `b.shouldBeTrue()` / `b.shouldBeFalse()` | `assertThat(b).isTrue()` |
| Null | `assertNull(x)` / `assertNotNull(x)` | `x.shouldBeNull()` / `x.shouldNotBeNull()` | `assertThat(x).isNull()` |
| Throws | `assertThrows<SomeException> { … }` | `shouldThrow<SomeException> { … }` | `assertFailure { … }.isInstanceOf(SomeException::class)` |
| Type | `assertTrue(x is T)` | `x.shouldBeInstanceOf<T>()` | `assertThat(x).isInstanceOf(T::class)` |
| String | `assertTrue(s.contains(sub))` | `s shouldContain sub` / `s shouldMatch Regex("...")` | `assertThat(s).contains(sub)` |
| Collection | `assertIterableEquals(...)` | `col shouldContainExactly listOf(...)` | `assertThat(col).containsExactly(...)` |
| Coroutine result | `runTest { … }` + `assertEquals` | `coroutineScope { … } shouldBe expected` | within `runTest` |
| Fail | `fail("reason")` | `fail("reason")` | `Assertions.fail("reason")` |

MockK verifications: `verify(exactly = 1) { mock.method() }` — counts as a state/side-effect assertion.

### Sleep/Delay Patterns

| Pattern | Example | Smell? |
|---|---|---|
| Thread sleep | `Thread.sleep(2000)` | ✅ Yes |
| Coroutine delay in `runBlocking` | `delay(1000)` | ✅ Yes |
| Virtual time in `runTest` | `advanceTimeBy(1000)` | ❌ No |
| Awaitility-style | `Awaitility.await().atMost(5, SECONDS).until { … }` | ❌ No (explicit) |

### Skip/Ignore Annotations

| Framework | Annotation |
|---|---|
| JUnit 5 | `@Disabled`, `@Disabled("reason")`, `@DisabledIf(...)`, `@EnabledIf(...)`, `@DisabledOnOs(...)` |
| JUnit 5 (dynamic) | `Assumptions.assumeTrue(cond)` |
| Kotest | `.config(enabled = false)`, `xtest("…")`, `xshould("…")`, `xdescribe("…")` |
| Kotest (project-wide) | `EnabledCondition` / `EnabledIf` extensions |
| TestNG | `@Test(enabled = false)`, `throw SkipException("reason")` |

### Mystery Guest — Kotlin/Android Patterns

| Indicator | What to look for |
|---|---|
| File system | `File(path).readText()`, hard-coded paths |
| Database | `Room.databaseBuilder(...)` without `inMemoryDatabaseBuilder` |
| Network | `Retrofit.create<…>()` against real URL, `OkHttp` without `MockWebServer` |
| Environment | `System.getenv("X")` |
| Android | `Context.assets.open(...)`, file system writes to internal/external storage |
| Acceptable | `MockWebServer`, `MockK`, `inMemoryDatabaseBuilder`, `@MockK`, Robolectric, `TemporaryFolder` |

### Integration Test Markers

- File suffix: `*IT.kt`, `*IntegrationTest.kt`, `*E2ETest.kt`
- Annotations: `@Tag("integration")`, `@SpringBootTest`, `@DataJpaTest`
- Android: `androidTest/` source set = integration; `test/` source set = unit
- Use of Testcontainers, embedded servers, real devices

### Setup/Teardown

| Framework | Per-test | Per-class |
|---|---|---|
| JUnit 5 | `@BeforeEach` | `@BeforeAll` (`@JvmStatic` in companion unless `@TestInstance(PER_CLASS)`) |
| JUnit 5 | `@AfterEach` | `@AfterAll` |
| Kotest | `beforeTest { }` / `beforeEach { }` | `beforeSpec { }` |
| Kotest | `afterTest { }` / `afterEach { }` | `afterSpec { }` |
| TestNG | `@BeforeMethod` | `@BeforeClass`, `@BeforeSuite` |
| Spek | `beforeEachTest { }` | `beforeGroup { }` |

### Tag/Trait Attributes

| Framework | Tag mechanism | Example |
|---|---|---|
| JUnit 5 | `@Tag("positive")` (stackable) | `@Tag("positive") @Tag("critical-path")` |
| Kotest | per-test: `.config(tags = setOf(Positive))`; per-spec: `override fun tags() = setOf(Positive)` | `object Positive : Tag()` |
| TestNG | `@Test(groups = ["positive"])` | `@Test(groups = ["positive", "boundary"])` |

### Language-Specific Calibration Notes

- **`suspend fun` test bodies without a coroutine scope** (`runTest`/`runBlocking`) make the test silently incomplete — flag those
- **`runTest`** (virtual time) preferred over `runBlocking` (real time) for time-dependent code
- **MockK `verify { }`** without `exactly = N` only checks "at least once" — tests asserting exact behavior should set the count
- **Kotest `forAll(...)`** (data-driven) is parametrized, NOT duplicate tests
- **`@OptIn(ExperimentalCoroutinesApi::class)`** in coroutine tests — not a smell
- **Android `@MediumTest` / `@LargeTest`** from `androidx.test.filters` — treat as integration markers
- **Compose UI tests** (`createComposeRule`) — UI integration tests
- **Bare `assert(x)`** in tests — acceptable but framework matchers give richer failure messages
- **`shouldBe` chained Kotest matchers** are single conceptual assertions — do not over-count chain length
