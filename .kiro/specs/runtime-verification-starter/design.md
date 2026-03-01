# Design Document: Runtime Verification Starter

## Overview

Runtime Verification Starter는 Spring Boot 애플리케이션 시작 시점에 인프라 검증을 자동으로 수행하는 Spring Boot Starter 모듈입니다. 이 모듈은 빌드 플러그인이 생성한 requirements.json 파일을 classpath에서 읽어 파일 존재, API 접근성, 디렉토리 권한을 검증합니다.

### 핵심 가치 제안

배포 전 인프라 검증을 빌드 시점에서 런타임 시점으로 이동시켜, 실제 배포 환경에서 가장 정확한 검증을 제공합니다. SSH 접근 없이 애플리케이션 자체가 필요한 리소스를 검증하므로 보안과 정확성이 향상됩니다.

### 핵심 설계 원칙

1. **Zero Code Changes (제로 코드 변경)**: 개발자는 의존성만 추가하면 되며, 소스코드 변경이 전혀 필요 없습니다. 어노테이션, import 문, Bean 정의 모두 불필요합니다.

2. **Spring Boot Auto-Configuration (자동 구성)**: Spring Boot의 표준 자동 구성 메커니즘을 활용하여 투명하게 통합됩니다. META-INF/spring.factories (Spring Boot 2.x) 또는 AutoConfiguration.imports (Spring Boot 3.x)를 통해 자동 등록됩니다.

3. **Early Verification (조기 검증)**: ApplicationContextInitializer를 사용하여 애플리케이션 컨텍스트 초기화 전에 검증을 수행합니다. 비즈니스 로직 실행 전에 인프라 문제를 발견합니다.

4. **Fail-Safe Design (안전 실패 설계)**: 개별 검증 실패가 다른 검증을 방해하지 않으며, 모든 실패를 수집하여 한 번에 보고합니다. 운영자가 모든 인프라 문제를 한 번에 파악할 수 있습니다.

5. **Environment-Aware (환경 인식)**: 프로파일(local/dev/stage/prod)에 따라 자동으로 strict/lenient 모드를 선택합니다. 운영 환경은 보호하고 개발 환경은 유연하게 유지합니다.

6. **Dependency-Only Activation (의존성만으로 활성화)**: build.gradle 또는 pom.xml에 의존성만 추가하면 즉시 작동합니다. 추가 설정이나 초기화 코드가 필요 없습니다.

### 주요 기능

- **VM 환경 인프라 검증**: 파일 존재, API 접근성, 디렉토리 권한 검증
- **Spring Boot 호환성**: Spring Boot 2.x 및 3.x 모두 지원
- **프로파일 기반 검증**: requirements-{profile}.json을 통한 환경별 검증 (지원 프로파일: local, dev, stage, prod)
- **구성 가능한 동작**: enabled, fail-on-error, timeout-seconds 등 설정 지원
- **상세한 로깅**: SLF4J 기반 DEBUG/INFO/WARN/ERROR 레벨 로깅
- **빌드 도구 중립**: Gradle 및 Maven 모두 지원
- **선택적 비활성화**: 프로파일별로 검증 비활성화 가능

## Architecture

### 모듈 구조

```
infrastructure-analyzer-spring-boot-starter/
├── src/main/java/io/infracheck/spring/
│   ├── autoconfigure/
│   │   └── InfrastructureVerificationAutoConfiguration.java
│   ├── initializer/
│   │   └── InfrastructureVerificationInitializer.java
│   ├── verifier/
│   │   ├── FileVerifier.java
│   │   ├── ApiVerifier.java
│   │   └── DirectoryVerifier.java
│   ├── loader/
│   │   └── RequirementsLoader.java
│   ├── config/
│   │   └── InfrastructureVerificationProperties.java
│   └── model/
│       └── VerificationResult.java
└── src/main/resources/
    └── META-INF/
        ├── spring.factories (Spring Boot 2.x)
        └── spring/
            └── org.springframework.boot.autoconfigure.AutoConfiguration.imports (Spring Boot 3.x)
```

### 실행 흐름

```
Application Startup
    ↓
Spring Boot Auto-Configuration
    ↓
InfrastructureVerificationAutoConfiguration
    ↓
Register ApplicationContextInitializer
    ↓
InfrastructureVerificationInitializer.initialize()
    ↓
├─ Check if enabled (infrastructure.verification.enabled)
├─ Load Requirements Spec (RequirementsLoader)
├─ Determine Mode (strict/lenient based on profile)
├─ Run Verifications (FileVerifier, ApiVerifier, DirectoryVerifier)
├─ Collect Results
└─ Handle Failures (log + exit or log + continue)
    ↓
Application Context Initialization Continues
```

### 레이어 분리

1. **Auto-Configuration Layer**: Spring Boot 통합 및 자동 등록
2. **Initialization Layer**: 애플리케이션 시작 시점 제어
3. **Configuration Layer**: 속성 바인딩 및 검증
4. **Loading Layer**: Requirements 파일 로드 및 파싱
5. **Verification Layer**: 실제 인프라 검증 수행
6. **Reporting Layer**: 결과 수집 및 로깅

### 프로파일 전략

이 모듈은 다음 Spring 프로파일을 지원합니다:

- **local**: 로컬 개발 환경 (lenient mode - 경고만 표시)
- **dev**: 개발 환경 (lenient mode - 경고만 표시)
- **stage**: 스테이징 환경 (lenient mode - 경고만 표시)
- **prod**: 운영 환경 (strict mode - 검증 실패 시 애플리케이션 종료)

각 프로파일에 대해 별도의 requirements 파일을 사용할 수 있습니다:
- `requirements-local.json`
- `requirements-dev.json`
- `requirements-stage.json`
- `requirements-prod.json`

프로파일별 파일이 없는 경우 기본 `requirements.json`으로 폴백됩니다.

## Components and Interfaces

### 1. InfrastructureVerificationAutoConfiguration

Spring Boot 자동 구성 클래스로, 조건부로 InfrastructureVerificationInitializer를 등록합니다.

```java
@Configuration
@ConditionalOnProperty(
    name = "infrastructure.verification.enabled",
    havingValue = "true",
    matchIfMissing = true
)
@EnableConfigurationProperties(InfrastructureVerificationProperties.class)
public class InfrastructureVerificationAutoConfiguration {
    
    @Bean
    public InfrastructureVerificationInitializer infrastructureVerificationInitializer(
        InfrastructureVerificationProperties properties,
        RequirementsLoader loader,
        List<Verifier> verifiers
    ) {
        return new InfrastructureVerificationInitializer(properties, loader, verifiers);
    }
    
    @Bean
    public RequirementsLoader requirementsLoader() {
        return new RequirementsLoader();
    }
    
    @Bean
    public FileVerifier fileVerifier() {
        return new FileVerifier();
    }
    
    @Bean
    public ApiVerifier apiVerifier(InfrastructureVerificationProperties properties) {
        return new ApiVerifier(properties.getTimeoutSeconds());
    }
    
    @Bean
    public DirectoryVerifier directoryVerifier() {
        return new DirectoryVerifier();
    }
}
```

### 2. InfrastructureVerificationInitializer

ApplicationContextInitializer를 구현하여 컨텍스트 초기화 전에 검증을 수행합니다.

```java
public class InfrastructureVerificationInitializer 
    implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    
    private final InfrastructureVerificationProperties properties;
    private final RequirementsLoader loader;
    private final List<Verifier> verifiers;
    
    @Override
    public void initialize(ConfigurableApplicationContext context) {
        if (!properties.isEnabled()) {
            log.info("Infrastructure verification is disabled");
            return;
        }
        
        String profile = getActiveProfile(context);
        Requirements requirements = loader.load(profile, properties.getRequirementsPath());
        
        if (requirements == null) {
            log.warn("No requirements specification found, skipping verification");
            return;
        }
        
        boolean strictMode = determineStrictMode(profile, properties);
        List<VerificationResult> results = runVerifications(requirements);
        
        handleResults(results, strictMode);
    }
    
    private boolean determineStrictMode(String profile, InfrastructureVerificationProperties props) {
        if (props.getFailOnError() != null) {
            return props.getFailOnError();
        }
        // Strict mode only for prod, lenient for local/dev/stage
        return "prod".equals(profile);
    }
}
```

### 3. InfrastructureVerificationProperties

구성 속성을 바인딩하는 클래스입니다.

```java
@ConfigurationProperties(prefix = "infrastructure.verification")
public class InfrastructureVerificationProperties {
    
    private boolean enabled = true;
    private Boolean failOnError; // null = auto-detect from profile
    private String requirementsPath = "classpath:infrastructure/requirements-{profile}.json";
    private int timeoutSeconds = 5;
    
    // getters and setters with validation
    
    public void setTimeoutSeconds(int timeoutSeconds) {
        if (timeoutSeconds <= 0) {
            log.error("Invalid timeout-seconds: {}, using default: 5", timeoutSeconds);
            this.timeoutSeconds = 5;
        } else {
            this.timeoutSeconds = timeoutSeconds;
        }
    }
}
```

### 4. RequirementsLoader

Requirements 파일을 classpath에서 로드하고 파싱합니다.

```java
public class RequirementsLoader {
    
    public Requirements load(String profile, String pathTemplate) {
        String profilePath = pathTemplate.replace("{profile}", profile);
        
        try {
            Resource resource = resourceLoader.getResource(profilePath);
            if (resource.exists()) {
                return parseRequirements(resource);
            }
        } catch (IOException e) {
            log.debug("Profile-specific requirements not found: {}", profilePath);
        }
        
        // Fallback to default requirements.json
        String defaultPath = "classpath:infrastructure/requirements.json";
        try {
            Resource resource = resourceLoader.getResource(defaultPath);
            if (resource.exists()) {
                return parseRequirements(resource);
            }
        } catch (IOException e) {
            log.warn("Default requirements not found: {}", defaultPath);
        }
        
        return null;
    }
    
    private Requirements parseRequirements(Resource resource) {
        try {
            String json = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            Requirements req = gson.fromJson(json, Requirements.class);
            validateRequirements(req);
            return req;
        } catch (JsonSyntaxException e) {
            log.error("Invalid JSON format in requirements file: {}", e.getMessage());
            throw new IllegalStateException("Malformed requirements specification", e);
        }
    }
}
```

### 5. Verifier Interface

모든 검증기가 구현하는 공통 인터페이스입니다.

```java
public interface Verifier {
    List<VerificationResult> verify(Requirements requirements);
}
```

### 6. FileVerifier

파일 존재를 검증합니다.

```java
public class FileVerifier implements Verifier {
    
    @Override
    public List<VerificationResult> verify(Requirements requirements) {
        List<VerificationResult> results = new ArrayList<>();
        
        for (FileCheck fileCheck : requirements.getInfrastructure().getFiles()) {
            log.debug("Verifying file: {}", fileCheck.getPath());
            
            try {
                Path path = Paths.get(fileCheck.getPath());
                boolean exists = Files.exists(path);
                
                if (exists) {
                    log.debug("File exists: {}", fileCheck.getPath());
                    results.add(VerificationResult.success("file", fileCheck.getPath()));
                } else {
                    log.warn("File not found: {}", fileCheck.getPath());
                    results.add(VerificationResult.failure(
                        "file", 
                        fileCheck.getPath(), 
                        "File does not exist",
                        fileCheck.isCritical()
                    ));
                }
            } catch (Exception e) {
                log.error("Error verifying file {}: {}", fileCheck.getPath(), e.getMessage());
                results.add(VerificationResult.failure(
                    "file",
                    fileCheck.getPath(),
                    "Exception: " + e.getMessage(),
                    fileCheck.isCritical()
                ));
            }
        }
        
        return results;
    }
}
```

### 7. ApiVerifier

API 접근성을 검증합니다.

```java
public class ApiVerifier implements Verifier {
    
    private final HttpClient httpClient;
    private final int timeoutSeconds;
    
    public ApiVerifier(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(timeoutSeconds))
            .build();
    }
    
    @Override
    public List<VerificationResult> verify(Requirements requirements) {
        List<VerificationResult> results = new ArrayList<>();
        List<ApiCheck> apis = requirements.getInfrastructure().getExternal_apis();
        
        // Sort: company domain first
        String companyDomain = requirements.getInfrastructure().getCompany_domain();
        List<ApiCheck> sortedApis = sortByCompanyDomain(apis, companyDomain);
        
        for (ApiCheck apiCheck : sortedApis) {
            log.debug("Verifying API: {}", apiCheck.getUrl());
            
            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiCheck.getUrl()))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .build();
                
                HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
                int statusCode = response.statusCode();
                
                if (isAcceptableStatus(statusCode)) {
                    log.debug("API accessible: {} (status: {})", apiCheck.getUrl(), statusCode);
                    results.add(VerificationResult.success("api", apiCheck.getUrl()));
                } else {
                    log.warn("API returned unexpected status: {} (status: {})", apiCheck.getUrl(), statusCode);
                    results.add(VerificationResult.failure(
                        "api",
                        apiCheck.getUrl(),
                        "Unexpected status code: " + statusCode,
                        apiCheck.isCritical()
                    ));
                }
            } catch (Exception e) {
                log.error("API unreachable: {} - {}", apiCheck.getUrl(), e.getMessage());
                results.add(VerificationResult.failure(
                    "api",
                    apiCheck.getUrl(),
                    "Unreachable: " + e.getMessage(),
                    apiCheck.isCritical()
                ));
            }
        }
        
        return results;
    }
    
    private boolean isAcceptableStatus(int status) {
        return (status >= 200 && status < 400) || status == 401 || status == 403;
    }
}
```

### 8. DirectoryVerifier

디렉토리 권한을 검증합니다.

```java
public class DirectoryVerifier implements Verifier {
    
    @Override
    public List<VerificationResult> verify(Requirements requirements) {
        List<VerificationResult> results = new ArrayList<>();
        
        for (DirectoryCheck dirCheck : requirements.getInfrastructure().getDirectories()) {
            log.debug("Verifying directory: {}", dirCheck.getPath());
            
            try {
                Path path = Paths.get(dirCheck.getPath());
                
                if (!Files.exists(path)) {
                    results.add(VerificationResult.failure(
                        "directory",
                        dirCheck.getPath(),
                        "Directory does not exist",
                        dirCheck.isCritical()
                    ));
                    continue;
                }
                
                String permissions = dirCheck.getPermissions();
                List<String> missingPermissions = new ArrayList<>();
                
                if (permissions.contains("r") && !Files.isReadable(path)) {
                    missingPermissions.add("read");
                }
                if (permissions.contains("w") && !Files.isWritable(path)) {
                    missingPermissions.add("write");
                }
                if (permissions.contains("x") && !Files.isExecutable(path)) {
                    missingPermissions.add("execute");
                }
                
                if (missingPermissions.isEmpty()) {
                    log.debug("Directory permissions OK: {}", dirCheck.getPath());
                    results.add(VerificationResult.success("directory", dirCheck.getPath()));
                } else {
                    String missing = String.join(", ", missingPermissions);
                    log.warn("Directory missing permissions: {} - {}", dirCheck.getPath(), missing);
                    results.add(VerificationResult.failure(
                        "directory",
                        dirCheck.getPath(),
                        "Missing permissions: " + missing,
                        dirCheck.isCritical()
                    ));
                }
            } catch (Exception e) {
                log.error("Error verifying directory {}: {}", dirCheck.getPath(), e.getMessage());
                results.add(VerificationResult.failure(
                    "directory",
                    dirCheck.getPath(),
                    "Exception: " + e.getMessage(),
                    dirCheck.isCritical()
                ));
            }
        }
        
        return results;
    }
}
```

## Data Models

### VerificationResult

검증 결과를 나타내는 모델입니다.

```java
public class VerificationResult {
    private final String type; // "file", "api", "directory"
    private final String identifier; // path or URL
    private final boolean success;
    private final String errorMessage;
    private final boolean critical;
    
    public static VerificationResult success(String type, String identifier) {
        return new VerificationResult(type, identifier, true, null, false);
    }
    
    public static VerificationResult failure(String type, String identifier, String error, boolean critical) {
        return new VerificationResult(type, identifier, false, error, critical);
    }
    
    // constructor, getters
}
```

### Requirements (재사용)

기존 infrastructure-analyzer-core 모듈의 Requirements 클래스를 재사용합니다. 이 클래스는 이미 FileCheck, ApiCheck, DirectoryCheck를 포함하고 있습니다.

## Correctness Properties

A property is a characteristic or behavior that should hold true across all valid executions of a system-essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.

### Property Reflection

After analyzing all acceptance criteria, I identified the following redundancies and consolidations:

1. **Logging properties consolidation**: Multiple criteria (4.5, 5.7, 6.6) test that successful checks log at DEBUG level. These can be combined into one property about all successful verifications.

2. **Failure recording consolidation**: Multiple criteria (4.3, 5.5, 6.5) test that failures are recorded with details. These can be combined into one property about all failed verifications.

3. **Permission verification consolidation**: Criteria 6.2, 6.3, 6.4 test individual permissions (read, write, execute). These can be combined into one property about permission verification.

4. **Configuration property support**: Criteria 8.1-8.4 test individual configuration properties. These are specific examples rather than properties.

5. **Zero-code-change principles**: Criteria 12.3, 12.4, 12.5 test that no annotations, imports, or beans are required. These can be combined into one property.

6. **Mode selection**: Criteria 7.5 and 7.6 test default mode selection for different profiles. These are specific examples.

After reflection, I will write properties that eliminate redundancy and provide unique validation value.

### Property 1: Profile-based requirements loading

For any Spring profile, the verifier should attempt to load requirements-{profile}.json first, then fall back to requirements.json if not found.

**Validates: Requirements 3.2, 3.3**

### Property 2: Configuration override behavior

For any custom requirements path configured via infrastructure.verification.requirements-path, the verifier should use that path instead of the default path.

**Validates: Requirements 3.7**

### Property 3: JSON validation

For any requirements specification file, when parsing the JSON, the verifier should validate the structure and reject malformed JSON with an error message.

**Validates: Requirements 3.5**

### Property 4: File verification completeness

For any list of FileCheck items in the requirements specification, the verifier should verify each file's existence and record the result.

**Validates: Requirements 4.1, 4.4**

### Property 5: Verification failure recording

For any failed verification item (file, API, or directory), the verifier should record the failure with the resource identifier and error details.

**Validates: Requirements 4.3, 5.5, 6.5**

### Property 6: API verification completeness

For any list of ApiCheck items in the requirements specification, the verifier should attempt HTTP connection to each URL and record the result.

**Validates: Requirements 5.1**

### Property 7: API status code acceptance

For any API that responds with HTTP status 2xx, 3xx, 401, or 403, the verifier should consider the verification successful.

**Validates: Requirements 5.4**

### Property 8: Company domain prioritization

For any requirements specification with a company domain, the verifier should verify company domain APIs before external APIs.

**Validates: Requirements 5.6**

### Property 9: Directory verification completeness

For any list of DirectoryCheck items in the requirements specification, the verifier should verify each directory's existence and permissions.

**Validates: Requirements 6.1**

### Property 10: Permission verification

For any DirectoryCheck with required permissions (read, write, execute), the verifier should check each required permission and record failures for any missing permissions.

**Validates: Requirements 6.2, 6.3, 6.4**

### Property 11: Strict mode behavior

For any verification failure in strict mode, the verifier should log all failures at ERROR level and terminate the application with System.exit(1).

**Validates: Requirements 7.2**

### Property 12: Lenient mode behavior

For any verification failure in lenient mode, the verifier should log all failures at WARN level and allow the application to continue.

**Validates: Requirements 7.3**

### Property 13: Configuration-based mode selection

For any explicit infrastructure.verification.fail-on-error configuration value, the verifier should use that value to determine strict or lenient mode.

**Validates: Requirements 7.4**

### Property 14: Summary logging

For any verification run, the verifier should log a summary including the count of passed verifications, failed verifications, and total verifications.

**Validates: Requirements 7.7, 10.4**

### Property 15: Disabled state behavior

For any configuration with infrastructure.verification.enabled set to false, the verifier should skip all verifications and log an INFO message.

**Validates: Requirements 2.3, 8.5**

### Property 16: Timeout validation

For any infrastructure.verification.timeout-seconds configuration value, the verifier should validate that it is a positive integer.

**Validates: Requirements 8.6**

### Property 17: Verification isolation

For any verification item that throws an exception, the verifier should log the exception, treat it as a failure, and continue verifying remaining items.

**Validates: Requirements 11.1, 11.3, 11.4**

### Property 18: Failure collection

For any verification run, the verifier should collect all failures before determining the final result and report them in a single summary.

**Validates: Requirements 11.2, 11.5**

### Property 19: Startup logging

For any verification run, the verifier should log the requirements file path, active profile, and execution time at INFO level.

**Validates: Requirements 10.1, 10.5**

### Property 20: Per-item debug logging

For any verification item being checked, the verifier should log the verification attempt at DEBUG level.

**Validates: Requirements 10.2**

### Property 21: Failure detail logging

For any failed verification item, the verifier should log the resource identifier and error reason at WARN or ERROR level depending on the mode.

**Validates: Requirements 10.3**

### Property 22: Successful verification logging

For any successful verification item, the verifier should log the success at DEBUG level.

**Validates: Requirements 4.5, 5.7, 6.6**

### Property 23: Sensitive information protection

For any verification run involving sensitive data (secrets, credentials), the verifier should not log the sensitive content.

**Validates: Requirements 10.7**

### Property 24: Profile-specific disabling

For any Spring profile with infrastructure.verification.enabled set to false in its profile-specific configuration, the verifier should be disabled only for that profile.

**Validates: Requirements 14.2**

### Property 25: Automatic verification with spec

For any application with requirements.json on the classpath, the verifier should automatically perform verification without additional configuration.

**Validates: Requirements 13.5**

## Error Handling

### 1. Missing Requirements File

When requirements.json is not found on the classpath:
- Log a WARN message: "No requirements specification found, skipping verification"
- Gracefully skip verification
- Allow application to start normally

### 2. Malformed JSON

When requirements.json contains invalid JSON:
- Log an ERROR message with parsing details
- In strict mode: terminate application with System.exit(1)
- In lenient mode: log error and skip verification

### 3. Invalid Configuration

When configuration properties have invalid values:
- Log an ERROR message describing the invalid value
- Use default values
- Continue with verification using defaults

### 4. Verification Exceptions

When an individual verification throws an unexpected exception:
- Catch the exception
- Log the exception with stack trace at ERROR level
- Treat as verification failure
- Continue with remaining verifications
- Include in final failure summary

### 5. Network Timeouts

When API verification times out:
- Catch TimeoutException
- Log as verification failure with "Timeout after {n} seconds"
- Continue with remaining verifications

### 6. Permission Denied

When file or directory access is denied:
- Catch AccessDeniedException
- Log as verification failure with "Access denied"
- Continue with remaining verifications

## Testing Strategy

### Dual Testing Approach

This feature requires both unit tests and property-based tests for comprehensive coverage:

- **Unit tests**: Verify specific examples, edge cases, and integration points
- **Property tests**: Verify universal properties across all inputs

Together, they provide comprehensive coverage where unit tests catch concrete bugs and property tests verify general correctness.

### Unit Testing

Unit tests should focus on:

1. **Auto-configuration activation**
   - Verify InfrastructureVerificationAutoConfiguration registers beans
   - Test @ConditionalOnProperty behavior
   - Verify Spring Boot 2.x and 3.x compatibility

2. **Requirements loading**
   - Test profile-specific file loading
   - Test fallback to default requirements.json
   - Test missing file handling

3. **Mode selection**
   - Test explicit fail-on-error configuration
   - Test default mode for prod profile (strict)
   - Test default mode for local/dev/stage profiles (lenient)

4. **Integration points**
   - Test ApplicationContextInitializer registration
   - Test execution before context initialization
   - Test interaction with Spring Environment

5. **Edge cases**
   - Missing requirements file
   - Malformed JSON
   - Invalid configuration values
   - Empty requirements specification

### Property-Based Testing

We will use **JUnit-Quickcheck** for Java property-based testing with minimum 100 iterations per test.

Each property test must include a comment tag: **Feature: runtime-verification-starter, Property {number}: {property_text}**

Property tests should focus on:

1. **Requirements loading properties**
   - Property 1: Profile-based requirements loading
   - Property 2: Configuration override behavior
   - Property 3: JSON validation

2. **Verification completeness properties**
   - Property 4: File verification completeness
   - Property 6: API verification completeness
   - Property 9: Directory verification completeness

3. **Verification behavior properties**
   - Property 5: Verification failure recording
   - Property 7: API status code acceptance
   - Property 8: Company domain prioritization
   - Property 10: Permission verification

4. **Mode and configuration properties**
   - Property 11: Strict mode behavior
   - Property 12: Lenient mode behavior
   - Property 13: Configuration-based mode selection
   - Property 15: Disabled state behavior
   - Property 16: Timeout validation

5. **Resilience properties**
   - Property 17: Verification isolation
   - Property 18: Failure collection

6. **Logging properties**
   - Property 14: Summary logging
   - Property 19: Startup logging
   - Property 20: Per-item debug logging
   - Property 21: Failure detail logging
   - Property 22: Successful verification logging
   - Property 23: Sensitive information protection

7. **Profile-specific properties**
   - Property 24: Profile-specific disabling
   - Property 25: Automatic verification with spec

### Test Configuration

```java
// Example property test configuration
@RunWith(JUnitQuickcheck.class)
public class RequirementsLoaderPropertyTest {
    
    // Feature: runtime-verification-starter, Property 1: Profile-based requirements loading
    @Property(trials = 100)
    public void profileBasedRequirementsLoading(@From(ProfileGenerator.class) String profile) {
        // For any Spring profile, verify loading behavior
        RequirementsLoader loader = new RequirementsLoader();
        // Test that profile-specific file is attempted first
        // Then fallback to default requirements.json
    }
}
```

### Test Data Generators

Create custom generators for:
- Spring profiles (local, dev, stage, prod, custom)
- Requirements specifications with varying content
- FileCheck, ApiCheck, DirectoryCheck lists
- Configuration property combinations
- Valid and invalid JSON structures

### Integration Testing

Create a test Spring Boot application to verify:
- Zero-code-change activation
- Dependency-only activation (Gradle and Maven)
- Profile-specific configuration
- Actual verification execution at startup
- Proper integration with Spring Boot lifecycle

### Performance Testing

Verify that:
- Disabled verification has negligible startup impact
- Verification with typical requirements completes in < 5 seconds
- Timeout configuration is respected
- Parallel verification (if implemented) improves performance
