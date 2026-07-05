# Integration Plan: Introducing Mockito into Nunki

## 1. Current Dependency Landscape
- **Spring Boot Parent**: `3.2.5`
- **JUnit**: JUnit Jupiter (`org.junit.jupiter:junit-jupiter:5.10.2`) supplied by `spring-boot-starter-test` (test scope).
- **Mockito**: Transitively pulled in via `spring-boot-starter-test` → `mockito-core:5.7.0` and `mockito-junit-jupiter:5.7.0` (both test scope).
- **ByteBuddy**: `net.bytebuddy:byte-buddy:1.14.13` (test scope) used by Mockito.
- No explicit version pins → risk of accidental upgrades.
- No JUnit 4 tests (`@RunWith` annotation absent) – all tests currently use JUnit 5 annotations (`@Test`, `@BeforeEach`).

## 2. Compatibility Analysis
| Component | Current Version | Conflict Risk | Recommended Action |
|-----------|----------------|--------------|--------------------|
| JUnit Jupiter | 5.10.2 (via Spring Boot) | None – already JUnit 5 | Keep default
| Mockito core | 5.7.0 (transitive) | Implicit version may drift | **Explicitly pin** to `5.7.0`
| Mockito JUnit Jupiter | 5.7.0 (transitive) | Same as above | **Explicitly pin** to `5.7.0`
| ByteBuddy | 1.14.13 (transitive via Mockito) | Implicit version may drift, byte‑code incompatibility with newer ASM versions | Pin via `<dependencyManagement>` to `1.14.13`
| Mockito‑inline (optional) | not present | None | Add as test‑scope for final‑class/static‑method mocking

No existing `mockito-inline` → adding it does not clash with current libraries.

## 3. New Dependency Declarations
Add the following block **inside `<dependencies>`** of `pom.xml` **after** the existing `spring-boot-starter-test` entry:
```xml
    <!-- Explicit Mockito integration (JUnit 5) -->
    <dependency>
        <groupId>org.mockito</groupId>
        <artifactId>mockito-core</artifactId>
        <version>5.7.0</version>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.mockito</groupId>
        <artifactId>mockito-junit-jupiter</artifactId>
        <version>5.7.0</version>
        <scope>test</scope>
    </dependency>
    <!-- Optional inline mock maker for final classes / static methods -->
    <dependency>
        <groupId>org.mockito</groupId>
        <artifactId>mockito-inline</artifactId>
        <version>5.7.0</version>
        <scope>test</scope>
    </dependency>
```
Add a **dependencyManagement** section **inside `<project>`** (if not already present) to lock ByteBuddy:
```xml
    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>net.bytebuddy</groupId>
                <artifactId>byte-buddy</artifactId>
                <version>1.14.13</version>
                <scope>test</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>
```
> **Why**: Guarantees deterministic byte‑code generation across test runs and prevents Maven from pulling newer, potentially incompatible versions via transitive paths.

## 4. Test Infrastructure Alignment
- All existing test classes already import `org.junit.jupiter.api.*` – continue this pattern.
- Replace any future legacy JUnit 4 usage with **JUnit 5**.
- For Mockito, adopt the JUnit‑5 extension:
```java
@ExtendWith(MockitoExtension.class)
class MyServiceTest {
    @Mock
    private Dependency dep;

    @InjectMocks
    private MyService service;

    @Test
    void shouldDoSomething() {
        // test body
    }
}
```
- If a test requires mocking final classes or static methods, add `mockito-inline` to the classpath (already done) – no extra annotation needed.

## 5. Build Integrity Assurance – Verification Steps
1. **Clean compile & package**:
   ```bash
   mvn clean compile package
   ```
   - Ensures no compilation errors from added dependencies.
2. **Run tests with Maven Surefire** (part of `package`), but also explicitly invoke the test phase to highlight failures:
   ```bash
   mvn test-compile test
   ```
   - Verifies that the test classpath resolves the pinned Mockito and ByteBuddy versions without duplication.
3. **Check for multiple ByteBuddy/ASM versions**:
   ```bash
   mvn dependency:tree -Dincludes=net.bytebuddy,org.ow2.asm | grep -E "byte-buddy|asm"
   ```
   - Expected output: a **single** `byte-buddy:1.14.13` entry and ASM only from `org.ow2.asm:asm:9.6` used by other test libraries.
4. **Run a sample test** (optional sanity check):
   ```bash
   mvn -Dtest=PingWebSocketTest test
   ```
   - Should pass unchanged, proving backward compatibility.

## 6. Roll‑out Procedure
| Step | Action | Owner |
|------|--------|-------|
| 1 | Add the dependency snippets to `pom.xml` (backup the file first). | Engineer |
| 2 | Commit the changes with an ADR‑linked commit message. | Engineer |
| 3 | Run the verification steps locally. | Engineer |
| 4 | Push to a feature branch and open a PR targeting `main`. | Engineer |
| 5 | Review CI results – ensure `mvn clean compile package test` succeeds. | Reviewer |
| 6 | Merge PR once green. | Reviewer |

## 7. Documentation & Traceability
- **ADR**: `docs/adr/ADR-0010.md` records the decision.
- **Design**: This `docs/DESIGN.md` captures the implementation plan.
- All future test contributors should reference the ADR for guidance on Mockito usage.

---
*Prepared by the Architecture team on 2026‑07‑05.*
```