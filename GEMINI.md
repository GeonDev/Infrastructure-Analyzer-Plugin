# Infrastructure Analyzer Project Context

## Project Overview
This project is an **Infrastructure Verification Integrated Solution** that automates the identification and validation of application infrastructure dependencies (files, APIs, directories, and Kubernetes resources). It consists of three main components integrated into a single JAR:
1.  **Core Analyzer:** Static analysis logic using `JavaParser` to extract hardcoded paths/URLs from source code and `SnakeYAML` to parse configuration files. It uses a hybrid approach (explicit declaration + auto-extraction fallback).
2.  **Build Plugins (Gradle/Maven):** Generates infrastructure specification files (`requirements-{profile}.json`) during the build phase.
3.  **Runtime Starter (Spring Boot):** An `ApplicationContextInitializer` that validates the infrastructure state against the generated specification during application startup, potentially blocking startup on failure (Strict Mode).

### Core Technologies
- **Java 17** (Source/Target Compatibility)
- **Gradle** (Build System)
- **Spring Boot 3.1.0** (Runtime Integration)
- **JavaParser 3.25.8** (Static Analysis)
- **SnakeYAML 2.2** (Config Parsing)
- **Gson 2.10.1** (JSON Generation)

## Architecture & Structure
- `io.infracheck.core`: Shared models and analysis logic.
    - `analyzer`: `SourceCodeAnalyzer` (JavaParser based) and `InfrastructureExtractor` (Hybrid extraction).
    - `util`: YAML/Config parsing and pattern matching.
- `io.infracheck.gradle`: Gradle plugin and task implementation.
- `io.infracheck.maven`: Maven Mojo implementation.
- `io.infracheck.spring`: Spring Boot auto-configuration, properties, and verifiers.
    - `initializer`: `InfrastructureVerificationInitializer` triggers verification on startup.
    - `verifier`: Concrete implementations for `FileVerifier`, `ApiVerifier`, `DirectoryVerifier`.

## Building and Running

### Build Commands
- **Install to local Maven repository:**
  ```bash
  ./gradlew clean publishToMavenLocal
  ```
- **Build the project:**
  ```bash
  ./gradlew build
  ```
- **Run tests:**
  ```bash
  ./gradlew test
  ```

### Usage in Client Projects
- **Gradle Plugin:**
  ```gradle
  plugins {
      id 'io.infracheck.infrastructure-analyzer' version '1.0.0-SNAPSHOT'
  }
  ```
- **Maven Plugin:**
  ```xml
  <plugin>
      <groupId>io.infracheck</groupId>
      <artifactId>infrastructure-analyzer</artifactId>
      <version>1.0.0-SNAPSHOT</version>
  </plugin>
  ```

### Configuration (`application.yml`)
```yaml
infrastructure:
  validation:
    domain: "company.com"
    verification:
      enabled: true
      fail-on-error: true # true for prod/stage by default
      timeout-seconds: 5
```

## Development Conventions

### Coding Style
- **Package Structure:** Organized by function (core, gradle, maven, spring).
- **Naming:** Follows standard Java/Spring conventions (e.g., `*Verifier`, `*Extractor`, `*Properties`).
- **Error Handling:** Runtime verification failures in "Strict Mode" (prod/stage) result in `System.exit(1)` to prevent application startup.

### Testing Practices
- **JUnit 4** is used for core tests.
- **JUnit-Quickcheck** is utilized for property-based testing in the core module.
- **Spring Boot Test** is used for starter-related verification tests.

### Implementation Details
- **Static Analysis:** Scans `src/main/java` (excluding `test` directories). It avoids strings within annotations (like `@Schema`).
- **Requirement Files:** Generated at `build/infrastructure/requirements-{profile}.json`.
- **Hybrid Extraction:** 
    1. Explicitly defined items in `infrastructure.validation` in YAML.
    2. Auto-extraction from YAML values matching specific patterns (regex).
    3. Static analysis of Java source code for hardcoded literals.
