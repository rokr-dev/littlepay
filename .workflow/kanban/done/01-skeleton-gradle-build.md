---
id: 01
slug: skeleton-gradle-build
language: java
depends-on: []
parallel-safe: true
skeleton: true
files-touched:
  - build.gradle
  - settings.gradle
  - gradle/wrapper/gradle-wrapper.properties
  - gradle/wrapper/gradle-wrapper.jar
  - gradlew
  - gradlew.bat
  - src/main/java/com/littlepay/Main.java
  - src/main/resources/simplelogger.properties
  - src/test/java/com/littlepay/SmokeTest.java
  - .gitignore
acceptance:
  - "./gradlew --version works (wrapper present and executable)"
  - "./gradlew test passes"
  - "./gradlew jar produces build/libs/littlepay.jar"
  - "java -jar build/libs/littlepay.jar exits 0 and prints a usage banner to stderr"
failing-tests:
  - SmokeTest#jar_main_class_exits_zero_when_invoked_with_no_args_is_allowed_for_skeleton
---

# Walking-skeleton Gradle build

Tracer bullet that proves the build, packaging, and entry-point exist before any
domain code lands.

## Scope

- Gradle Groovy DSL with the `application` plugin.
- Java 21 toolchain pinned.
- Dependencies: Apache Commons CSV, SLF4J API + slf4j-simple, JUnit 5, AssertJ.
- Fat-JAR task so `java -jar` runs without classpath flags.
- `Main.java` stub that prints a one-line usage banner to stderr and exits 0
  for the skeleton (real argument handling lands in ticket 07).
- `simplelogger.properties` at INFO with a clean format string.

## Setup Steps

Project has no Gradle wrapper yet. Implementer must bootstrap it locally without installing gradle globally or via brew. Use download-based bootstrap:

1. Create `settings.gradle` with `rootProject.name = 'littlepay'`
2. Create `gradle/wrapper/gradle-wrapper.properties` with:
   ```
   distributionBase=GRADLE_USER_HOME
   distributionPath=wrapper/dists
   distributionUrl=https\://services.gradle.org/distributions/gradle-8.10-bin.zip
   networkTimeout=10000
   validateDistributionUrl=true
   zipStoreBase=GRADLE_USER_HOME
   zipStorePath=wrapper/dists
   ```
3. Download `gradle-wrapper.jar` into `gradle/wrapper/`:
   ```
   curl -fsSL -o gradle/wrapper/gradle-wrapper.jar https://raw.githubusercontent.com/gradle/gradle/v8.10.0/gradle/wrapper/gradle-wrapper.jar
   ```
4. Download `gradlew` script into project root:
   ```
   curl -fsSL -o gradlew https://raw.githubusercontent.com/gradle/gradle/v8.10.0/gradlew
   curl -fsSL -o gradlew.bat https://raw.githubusercontent.com/gradle/gradle/v8.10.0/gradlew.bat
   chmod +x gradlew
   ```
5. Run `./gradlew --version` — wrapper downloads gradle 8.10 distribution to `~/.gradle/wrapper/dists/` automatically; project stays clean
6. Verify SHA of `gradle-wrapper.jar` matches gradle's published checksum (security best practice)

## Acceptance test (exercises the binary)

`SmokeTest` invokes `Main.main(new String[0])` and asserts no exception is
thrown. A second assertion verifies the assembled JAR file exists at
`build/libs/littlepay.jar` after `./gradlew jar`.

## References

- Design doc §2 Toolchain (Java 21, Gradle Groovy DSL, Commons CSV, SLF4J).
- Design doc §2.5 Project layout — overrides the global rule; tests live under
  `src/test/java/com/littlepay/`.
