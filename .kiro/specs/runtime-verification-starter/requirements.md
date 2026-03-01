# Requirements Document

## Introduction

Runtime Verification Starter는 Spring Boot 애플리케이션 시작 시점에 인프라 검증을 수행하는 자동 구성 모듈입니다.
이는 애플리케이션 개발자와 인프라 팀 간의 커뮤니케이션 격차를 해소하기 위해, 애플리케이션이 완전히 시작되기 전에 필요한 인프라 리소스(파일, API, 디렉토리 권한)가 준비되었는지 검증합니다.
검증은 실제 배포 환경(VM)에서 직접 실행되므로 SSH 접근이 불필요하며 가장 정확한 검증을 제공합니다.

## Glossary

- **Runtime_Verifier**: 애플리케이션 시작 시 인프라 검증을 수행하는 Spring Boot Starter 모듈
- **Requirements_Spec**: 빌드 플러그인이 생성한 인프라 검증 명세를 담은 JSON 파일 (requirements.json)
- **Verification_Item**: 검증할 단일 인프라 리소스 (파일, API, 디렉토리 권한)
- **Build_Plugin**: 빌드 시점에 Requirements_Spec을 생성하는 Gradle 또는 Maven 플러그인
- **Application_Context_Initializer**: Spring 애플리케이션 컨텍스트가 완전히 초기화되기 전에 실행되는 Spring Boot 컴포넌트
- **Strict_Mode**: 검증 실패 시 애플리케이션 시작을 중단하는 구성 모드 (일반적으로 prod 환경)
- **Lenient_Mode**: 검증 실패 시 경고 로그만 출력하는 구성 모드 (일반적으로 local/dev/stage 환경)
- **Classpath_Resource**: 애플리케이션 JAR/WAR에 포함되어 classpath를 통해 접근 가능한 파일

## Requirements

### Requirement 1: 빌드 플러그인에서 Requirements Spec 생성

**User Story:** As a 빌드 플러그인, I want requirements.json을 resources 디렉토리에 생성하기를, so that 애플리케이션 아티팩트에 포함되어 런타임에 사용할 수 있다.

#### Acceptance Criteria

1. WHEN Build_Plugin이 실행될 때, THE Build_Plugin SHALL requirements.json 파일을 src/main/resources/infrastructure/ 디렉토리에 생성한다
2. FOR EACH 활성 프로파일 (local, dev, stage, prod), THE Build_Plugin SHALL 별도의 requirements-{profile}.json 파일을 생성한다
3. THE Build_Plugin SHALL 생성된 파일에 모든 FileCheck, ApiCheck, DirectoryCheck 명세를 포함한다
4. WHEN 애플리케이션이 빌드될 때, THE requirements.json 파일들 SHALL 최종 JAR/WAR 아티팩트에 Classpath_Resource로 포함된다

### Requirement 2: 런타임 검증 자동 구성

**User Story:** As a Spring Boot 애플리케이션, I want starter가 존재하면 런타임 검증이 자동으로 활성화되기를, so that 수동 설정 없이 인프라 검증이 수행된다.

#### Acceptance Criteria

1. WHEN Runtime_Verifier starter가 classpath에 있을 때, THE Runtime_Verifier SHALL Application_Context_Initializer를 자동으로 등록한다
2. THE Runtime_Verifier SHALL Spring Boot 2.x 및 3.x를 모두 지원한다
3. WHERE infrastructure.verification.enabled가 false로 설정된 경우, THE Runtime_Verifier SHALL 모든 검증을 건너뛴다
4. THE Runtime_Verifier SHALL Spring 애플리케이션 컨텍스트가 완전히 초기화되기 전에 실행된다
5. THE Runtime_Verifier SHALL 활성화 상태를 INFO 레벨로 로깅한다

### Requirement 3: Requirements 명세 로드

**User Story:** As a Runtime Verifier, I want classpath에서 requirements 명세를 로드하기를, so that 어떤 인프라 리소스를 검증해야 하는지 알 수 있다.

#### Acceptance Criteria

1. WHEN Runtime_Verifier가 초기화될 때, THE Runtime_Verifier SHALL 활성 Spring 프로파일을 확인한다
2. THE Runtime_Verifier SHALL classpath:infrastructure/에서 requirements-{profile}.json 로드를 시도한다
3. IF requirements-{profile}.json을 찾을 수 없는 경우, THEN THE Runtime_Verifier SHALL 대체로 requirements.json 로드를 시도한다
4. IF Requirements_Spec을 찾을 수 없는 경우, THEN THE Runtime_Verifier SHALL 경고를 로깅하고 검증을 건너뛴다
5. WHEN Requirements_Spec을 파싱할 때, THE Runtime_Verifier SHALL JSON 구조를 검증한다
6. IF Requirements_Spec이 잘못된 형식인 경우, THEN THE Runtime_Verifier SHALL 상세 오류 메시지를 ERROR 레벨로 로깅하고 구성된 모드에 따라 실패 처리한다
7. WHERE infrastructure.verification.requirements-path가 구성된 경우, THE Runtime_Verifier SHALL 기본 경로 대신 사용자 지정 경로를 사용한다

### Requirement 4: 파일 존재 검증

**User Story:** As a Runtime Verifier, I want 필요한 파일이 로컬 파일 시스템에 존재하는지 검증하기를, so that 애플리케이션이 런타임에 파일 누락으로 실패하지 않는다.

#### Acceptance Criteria

1. FOR EACH Requirements_Spec의 FileCheck, THE Runtime_Verifier SHALL 지정된 경로에 파일이 존재하는지 확인한다
2. WHEN 파일 존재를 확인할 때, THE Runtime_Verifier SHALL java.nio.file.Files.exists()를 사용한다
3. IF 필수 파일이 존재하지 않는 경우, THEN THE Runtime_Verifier SHALL 파일 경로와 함께 검증 실패를 기록한다
4. THE Runtime_Verifier SHALL FileCheck에 지정된 절대 경로를 사용하여 파일을 검증한다
5. WHEN FileCheck가 성공한 경우, THE Runtime_Verifier SHALL DEBUG 레벨로 성공을 로깅한다

### Requirement 5: API 접근성 검증

**User Story:** As a Runtime Verifier, I want 외부 API가 접근 가능한지 검증하기를, so that 애플리케이션이 외부 서비스 호출 시 실패하지 않는다.

#### Acceptance Criteria

1. FOR EACH Requirements_Spec의 ApiCheck, THE Runtime_Verifier SHALL 지정된 URL로 HTTP 연결을 시도한다
2. THE Runtime_Verifier SHALL API 검증을 위해 java.net.http.HttpClient를 사용한다
3. THE Runtime_Verifier SHALL 5초 타임아웃으로 HTTP HEAD 요청을 전송한다
4. WHEN API가 HTTP 상태 2xx, 3xx, 401, 또는 403으로 응답하는 경우, THE Runtime_Verifier SHALL 검증을 성공으로 간주한다
5. IF API에 도달할 수 없거나 타임아웃되는 경우, THEN THE Runtime_Verifier SHALL URL과 오류 상세 정보와 함께 검증 실패를 기록한다
6. THE Runtime_Verifier SHALL 회사 도메인 API (*.company.com)를 외부 API보다 먼저 검증한다
7. WHEN ApiCheck가 성공한 경우, THE Runtime_Verifier SHALL DEBUG 레벨로 성공을 로깅한다

### Requirement 6: 디렉토리 권한 검증

**User Story:** As a Runtime Verifier, I want VM 배포에서 디렉토리 권한을 검증하기를, so that 애플리케이션이 필요한 읽기/쓰기/실행 권한을 가진다.

#### Acceptance Criteria

1. FOR EACH Requirements_Spec의 DirectoryCheck, THE Runtime_Verifier SHALL 디렉토리가 존재하는지 검증한다
2. WHERE readable이 true인 경우, THE Runtime_Verifier SHALL Files.isReadable()을 사용하여 디렉토리에 읽기 권한이 있는지 검증한다
3. WHERE writable이 true인 경우, THE Runtime_Verifier SHALL Files.isWritable()을 사용하여 디렉토리에 쓰기 권한이 있는지 검증한다
4. WHERE executable이 true인 경우, THE Runtime_Verifier SHALL Files.isExecutable()을 사용하여 디렉토리에 실행 권한이 있는지 검증한다
5. IF 필요한 권한이 누락된 경우, THEN THE Runtime_Verifier SHALL 디렉토리 경로와 누락된 권한과 함께 검증 실패를 기록한다
6. WHEN DirectoryCheck가 성공한 경우, THE Runtime_Verifier SHALL DEBUG 레벨로 성공을 로깅한다

### Requirement 7: 검증 실패 처리

**User Story:** As a Runtime Verifier, I want 환경 구성에 따라 검증 실패를 처리하기를, so that 운영 배포는 보호되고 개발 환경은 유연하게 유지된다.

#### Acceptance Criteria

1. WHEN 모든 Verification_Item이 통과한 경우, THE Runtime_Verifier SHALL INFO 레벨로 성공 요약을 로깅한다
2. WHEN Strict_Mode에서 Verification_Item이 실패한 경우, THE Runtime_Verifier SHALL ERROR 레벨로 모든 실패를 로깅하고 System.exit(1)로 애플리케이션을 종료한다
3. WHEN Lenient_Mode에서 Verification_Item이 실패한 경우, THE Runtime_Verifier SHALL WARN 레벨로 모든 실패를 로깅하고 애플리케이션이 계속 진행되도록 허용한다
4. WHERE infrastructure.verification.fail-on-error가 명시적으로 설정된 경우, THE Runtime_Verifier SHALL 해당 값을 사용하여 모드를 결정한다
5. WHERE infrastructure.verification.fail-on-error가 설정되지 않고 프로파일이 "prod"인 경우, THE Runtime_Verifier SHALL Strict_Mode를 사용한다
6. WHERE infrastructure.verification.fail-on-error가 설정되지 않고 프로파일이 "local", "dev", 또는 "stage"인 경우, THE Runtime_Verifier SHALL Lenient_Mode를 사용한다
7. THE Runtime_Verifier SHALL 요약 로그에 통과 및 실패한 검증 수를 포함한다

### Requirement 8: 구성 속성

**User Story:** As an 애플리케이션 운영자, I want application.yml을 통해 런타임 검증 동작을 구성하기를, so that 코드 변경 없이 검증을 제어할 수 있다.

#### Acceptance Criteria

1. THE Runtime_Verifier SHALL 기본값 true를 가진 구성 속성 infrastructure.verification.enabled를 지원한다
2. THE Runtime_Verifier SHALL 기본값 없이 구성 속성 infrastructure.verification.fail-on-error를 지원한다 (프로파일에 따라 자동 결정)
3. THE Runtime_Verifier SHALL 기본값 "classpath:infrastructure/requirements-{profile}.json"을 가진 구성 속성 infrastructure.verification.requirements-path를 지원한다
4. THE Runtime_Verifier SHALL API 검사를 위한 기본값 5를 가진 구성 속성 infrastructure.verification.timeout-seconds를 지원한다
5. WHEN infrastructure.verification.enabled가 false인 경우, THE Runtime_Verifier SHALL 모든 검증을 건너뛰고 INFO 레벨로 메시지를 로깅한다
6. THE Runtime_Verifier SHALL timeout-seconds가 양의 정수인지 검증한다
7. IF 구성 속성에 잘못된 값이 있는 경우, THEN THE Runtime_Verifier SHALL 오류를 로깅하고 기본값을 사용한다

### Requirement 9: Gradle 및 Maven 플러그인 호환성

**User Story:** As a 빌드 도구 사용자, I want 런타임 검증이 Gradle과 Maven 플러그인 모두에서 작동하기를, so that 선호하는 빌드 시스템을 사용할 수 있다.

#### Acceptance Criteria

1. THE Runtime_Verifier SHALL Gradle 또는 Maven Build_Plugin이 생성한 Requirements_Spec 파일을 읽는다
2. THE Requirements_Spec JSON 형식 SHALL Gradle과 Maven 플러그인 모두에서 동일하다
3. THE Runtime_Verifier SHALL Gradle 전용 또는 Maven 전용 아티팩트에 의존하지 않는다
4. THE Runtime_Verifier SHALL spring.factories 또는 AutoConfiguration 메타데이터를 가진 표준 Spring Boot Starter로 배포된다

### Requirement 10: 로깅 및 관찰성

**User Story:** As an 애플리케이션 운영자, I want 검증 결과의 상세 로깅을 원하기를, so that 인프라 문제를 빠르게 진단할 수 있다.

#### Acceptance Criteria

1. WHEN 검증이 시작될 때, THE Runtime_Verifier SHALL INFO 레벨로 Requirements_Spec 파일 경로와 프로파일을 로깅한다
2. FOR EACH Verification_Item, THE Runtime_Verifier SHALL DEBUG 레벨로 검증 시도를 로깅한다
3. WHEN Verification_Item이 실패한 경우, THE Runtime_Verifier SHALL WARN 또는 ERROR 레벨로 리소스 식별자와 오류 이유와 함께 실패를 로깅한다
4. WHEN 검증이 완료될 때, THE Runtime_Verifier SHALL INFO 레벨로 통과/실패/전체 검증 수를 포함한 요약을 로깅한다
5. THE Runtime_Verifier SHALL 완료 요약에 실행 시간을 포함한다
6. THE Runtime_Verifier SHALL 애플리케이션의 로깅 프레임워크와 통합하기 위해 SLF4J를 사용한다
7. THE Runtime_Verifier SHALL Secret 내용이나 API 자격 증명과 같은 민감한 정보를 로깅하지 않는다

### Requirement 11: 오류 복구 및 복원력

**User Story:** As a Runtime Verifier, I want 개별 검사가 실패해도 검증을 계속하기를, so that 운영자가 모든 인프라 문제를 한 번에 볼 수 있다.

#### Acceptance Criteria

1. WHEN Verification_Item이 실패한 경우, THE Runtime_Verifier SHALL 나머지 항목 검증을 계속한다
2. THE Runtime_Verifier SHALL 최종 결과를 결정하기 전에 모든 검증 실패를 수집한다
3. IF 단일 검증 중 예상치 못한 예외가 발생한 경우, THEN THE Runtime_Verifier SHALL 예외를 로깅하고 검증 실패로 처리한다
4. THE Runtime_Verifier SHALL 개별 검증의 예외가 다른 검증 실행을 방해하지 않도록 한다
5. WHEN 검증이 완료될 때, THE Runtime_Verifier SHALL 단일 요약에 모든 실패를 보고한다

### Requirement 12: 제로 코드 변경 원칙

**User Story:** As a 개발자, I want 소스코드에 어떤 변경도 하지 않고 인프라 검증이 자동으로 실행되기를, so that 비즈니스 로직과 무관한 설정이 소스코드에 노출되지 않는다.

#### Acceptance Criteria

1. WHEN Runtime_Verifier starter가 의존성에 추가될 때, THE Runtime_Verifier SHALL 소스코드 변경 없이 자동으로 활성화된다
2. THE Runtime_Verifier SHALL Spring Boot의 Auto-configuration 메커니즘을 사용하여 자동으로 등록된다
3. THE Runtime_Verifier SHALL 애플리케이션 소스코드에 어떤 어노테이션도 요구하지 않는다
4. THE Runtime_Verifier SHALL 애플리케이션 소스코드에 어떤 import 문도 요구하지 않는다
5. THE Runtime_Verifier SHALL 애플리케이션 소스코드에 어떤 Bean 정의도 요구하지 않는다
6. WHERE 개발자가 검증을 비활성화하고 싶은 경우, THE Runtime_Verifier SHALL application.yml에서만 설정 변경을 허용한다
7. THE Runtime_Verifier SHALL META-INF/spring.factories 또는 META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports를 통해 자동 등록된다
8. WHEN 애플리케이션이 시작될 때, THE Runtime_Verifier SHALL 개발자가 작성한 코드 실행 전에 검증을 완료한다

### Requirement 13: 의존성 추가만으로 활성화

**User Story:** As a 개발자, I want build.gradle 또는 pom.xml에 의존성만 추가하면 검증이 자동으로 작동하기를, so that 추가 설정 없이 즉시 사용할 수 있다.

#### Acceptance Criteria

1. WHEN 개발자가 Gradle 프로젝트의 build.gradle에 `implementation 'io.infracheck:infrastructure-analyzer-spring-boot-starter:1.0.0'`을 추가할 때, THE Runtime_Verifier SHALL 자동으로 활성화된다
2. WHEN 개발자가 Maven 프로젝트의 pom.xml에 해당 의존성을 추가할 때, THE Runtime_Verifier SHALL 자동으로 활성화된다
3. THE Runtime_Verifier SHALL 의존성 추가 외에 어떤 추가 설정도 요구하지 않는다
4. THE Runtime_Verifier SHALL 기본 설정으로 즉시 작동한다
5. WHERE requirements.json이 classpath에 존재하는 경우, THE Runtime_Verifier SHALL 자동으로 검증을 수행한다
6. WHERE requirements.json이 classpath에 존재하지 않는 경우, THE Runtime_Verifier SHALL 경고를 로깅하고 조용히 비활성화된다

### Requirement 14: 선택적 비활성화

**User Story:** As a 개발자, I want 필요시 검증을 비활성화할 수 있기를, so that 로컬 개발 환경에서 유연하게 작업할 수 있다.

#### Acceptance Criteria

1. WHERE 개발자가 검증을 비활성화하고 싶은 경우, THE Runtime_Verifier SHALL application.yml에 `infrastructure.verification.enabled: false` 설정만으로 비활성화를 허용한다
2. THE Runtime_Verifier SHALL 프로파일별로 선택적 비활성화를 지원한다
3. WHERE application-local.yml에 `infrastructure.verification.enabled: false`가 설정된 경우, THE Runtime_Verifier SHALL local 프로파일에서만 비활성화된다
4. THE Runtime_Verifier SHALL 비활성화 시 INFO 레벨로 "Infrastructure verification is disabled" 메시지를 로깅한다
5. THE Runtime_Verifier SHALL 비활성화 시 애플리케이션 시작 시간에 영향을 주지 않는다
