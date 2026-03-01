# Implementation Plan: Runtime Verification Starter

## Overview

이 구현 계획은 Spring Boot 애플리케이션 시작 시점에 인프라 검증을 자동으로 수행하는 Spring Boot Starter 모듈을 구축합니다. 모듈은 빌드 플러그인이 생성한 requirements.json을 classpath에서 읽어 파일 존재, API 접근성, 디렉토리 권한을 검증합니다. 제로 코드 변경 원칙을 따르며, 의존성 추가만으로 자동 활성화됩니다.

## Tasks

- [ ] 1. 프로젝트 구조 및 빌드 설정
  - Gradle 프로젝트 생성 (infrastructure-analyzer-spring-boot-starter)
  - build.gradle 설정: Spring Boot Starter 의존성, Gson, SLF4J
  - 패키지 구조 생성: autoconfigure, initializer, verifier, loader, config, model
  - _Requirements: 2.2, 9.3_

- [ ] 2. 데이터 모델 구현
  - [ ] 2.1 VerificationResult 클래스 구현
    - success/failure 정적 팩토리 메서드 포함
    - type, identifier, success, errorMessage, critical 필드
    - _Requirements: 11.2_
  
  - [ ] 2.2 기존 Requirements 모델 재사용 설정
    - infrastructure-analyzer-core 모듈 의존성 추가
    - Requirements, FileCheck, ApiCheck, DirectoryCheck 임포트
    - _Requirements: 9.1, 9.2_

- [ ] 3. 구성 속성 구현
  - [ ] 3.1 InfrastructureVerificationProperties 클래스 구현
    - @ConfigurationProperties(prefix = "infrastructure.verification") 어노테이션
    - enabled (기본값: true), failOnError (기본값: null), requirementsPath, timeoutSeconds (기본값: 5) 필드
    - timeoutSeconds 검증 로직 (양의 정수 확인)
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.6, 8.7_
  
  - [ ]* 3.2 InfrastructureVerificationProperties 속성 테스트
    - **Property 16: Timeout validation**
    - **Validates: Requirements 8.6**

- [ ] 4. Requirements 로더 구현
  - [ ] 4.1 RequirementsLoader 클래스 구현
    - load(String profile, String pathTemplate) 메서드
    - 프로파일별 파일 로드 시도 (requirements-{profile}.json)
    - 기본 requirements.json으로 폴백
    - JSON 파싱 및 검증 (Gson 사용)
    - 파일 없을 경우 null 반환
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.7_
  
  - [ ]* 4.2 RequirementsLoader 속성 테스트
    - **Property 1: Profile-based requirements loading**
    - **Property 2: Configuration override behavior**
    - **Property 3: JSON validation**
    - **Validates: Requirements 3.2, 3.3, 3.5, 3.7**

- [ ] 5. 검증기 인터페이스 및 구현
  - [ ] 5.1 Verifier 인터페이스 정의
    - verify(Requirements requirements) 메서드 시그니처
    - List<VerificationResult> 반환 타입
    - _Requirements: 11.1_
  
  - [ ] 5.2 FileVerifier 구현
    - Verifier 인터페이스 구현
    - Files.exists()를 사용한 파일 존재 확인
    - 절대 경로 사용
    - 성공 시 DEBUG 로깅, 실패 시 WARN 로깅
    - 예외 처리 및 실패 기록
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5_
  
  - [ ]* 5.3 FileVerifier 속성 테스트
    - **Property 4: File verification completeness**
    - **Property 5: Verification failure recording**
    - **Property 22: Successful verification logging**
    - **Validates: Requirements 4.1, 4.3, 4.5**
  
  - [ ] 5.4 ApiVerifier 구현
    - Verifier 인터페이스 구현
    - HttpClient를 사용한 HTTP HEAD 요청
    - 타임아웃 설정 (configurable)
    - 2xx, 3xx, 401, 403 상태 코드를 성공으로 간주
    - 회사 도메인 API 우선 검증 (sortByCompanyDomain)
    - 성공 시 DEBUG 로깅, 실패 시 WARN 로깅
    - 예외 처리 및 실패 기록
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7_
  
  - [ ]* 5.5 ApiVerifier 속성 테스트
    - **Property 6: API verification completeness**
    - **Property 7: API status code acceptance**
    - **Property 8: Company domain prioritization**
    - **Validates: Requirements 5.1, 5.4, 5.6**
  
  - [ ] 5.6 DirectoryVerifier 구현
    - Verifier 인터페이스 구현
    - 디렉토리 존재 확인
    - Files.isReadable(), isWritable(), isExecutable()을 사용한 권한 확인
    - 누락된 권한 수집 및 보고
    - 성공 시 DEBUG 로깅, 실패 시 WARN 로깅
    - 예외 처리 및 실패 기록
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6_
  
  - [ ]* 5.7 DirectoryVerifier 속성 테스트
    - **Property 9: Directory verification completeness**
    - **Property 10: Permission verification**
    - **Validates: Requirements 6.1, 6.2, 6.3, 6.4**

- [ ] 6. Checkpoint - 검증기 테스트 확인
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 7. ApplicationContextInitializer 구현
  - [ ] 7.1 InfrastructureVerificationInitializer 클래스 구현
    - ApplicationContextInitializer<ConfigurableApplicationContext> 구현
    - initialize() 메서드에서 검증 수행
    - enabled 확인 및 비활성화 시 INFO 로깅
    - 활성 프로파일 추출 (getActiveProfile)
    - RequirementsLoader를 사용한 명세 로드
    - 명세 없을 경우 WARN 로깅 및 건너뛰기
    - determineStrictMode() 메서드 (failOnError 우선, 없으면 prod=strict, 나머지=lenient)
    - runVerifications() 메서드 (모든 Verifier 실행 및 결과 수집)
    - handleResults() 메서드 (strict/lenient 모드에 따른 처리)
    - _Requirements: 2.1, 2.3, 2.4, 2.5, 3.1, 7.1, 7.2, 7.3, 7.4, 7.5, 7.6, 7.7_
  
  - [ ]* 7.2 InfrastructureVerificationInitializer 속성 테스트
    - **Property 11: Strict mode behavior**
    - **Property 12: Lenient mode behavior**
    - **Property 13: Configuration-based mode selection**
    - **Property 15: Disabled state behavior**
    - **Property 17: Verification isolation**
    - **Property 18: Failure collection**
    - **Validates: Requirements 7.2, 7.3, 7.4, 2.3, 11.1, 11.2**

- [ ] 8. 로깅 구현
  - [ ] 8.1 로깅 통합
    - 모든 클래스에 SLF4J Logger 추가
    - 시작 시 INFO 로깅 (파일 경로, 프로파일)
    - 각 검증 항목 DEBUG 로깅
    - 실패 시 WARN/ERROR 로깅 (리소스 식별자, 오류 이유)
    - 완료 시 INFO 로깅 (요약: 통과/실패/전체 수, 실행 시간)
    - 민감 정보 로깅 방지 (Secret, 자격 증명)
    - _Requirements: 10.1, 10.2, 10.3, 10.4, 10.5, 10.6, 10.7_
  
  - [ ]* 8.2 로깅 속성 테스트
    - **Property 14: Summary logging**
    - **Property 19: Startup logging**
    - **Property 20: Per-item debug logging**
    - **Property 21: Failure detail logging**
    - **Property 23: Sensitive information protection**
    - **Validates: Requirements 10.1, 10.2, 10.3, 10.4, 10.5, 10.7**

- [ ] 9. Spring Boot Auto-Configuration 구현
  - [ ] 9.1 InfrastructureVerificationAutoConfiguration 클래스 구현
    - @Configuration 어노테이션
    - @ConditionalOnProperty(name = "infrastructure.verification.enabled", havingValue = "true", matchIfMissing = true)
    - @EnableConfigurationProperties(InfrastructureVerificationProperties.class)
    - infrastructureVerificationInitializer Bean 정의
    - requirementsLoader Bean 정의
    - fileVerifier Bean 정의
    - apiVerifier Bean 정의 (timeoutSeconds 주입)
    - directoryVerifier Bean 정의
    - _Requirements: 2.1, 2.2, 2.3_
  
  - [ ] 9.2 Spring Boot 2.x 지원 (spring.factories)
    - src/main/resources/META-INF/spring.factories 생성
    - org.springframework.boot.autoconfigure.EnableAutoConfiguration 키에 InfrastructureVerificationAutoConfiguration 등록
    - _Requirements: 2.2, 9.4, 12.7_
  
  - [ ] 9.3 Spring Boot 3.x 지원 (AutoConfiguration.imports)
    - src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports 생성
    - InfrastructureVerificationAutoConfiguration 전체 클래스명 등록
    - _Requirements: 2.2, 9.4, 12.7_
  
  - [ ]* 9.4 Auto-Configuration 단위 테스트
    - @ConditionalOnProperty 동작 확인
    - Bean 등록 확인
    - Spring Boot 2.x 및 3.x 호환성 확인
    - _Requirements: 2.1, 2.2_

- [ ] 10. Checkpoint - 통합 테스트 준비
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 11. 제로 코드 변경 및 의존성 활성화 검증
  - [ ] 11.1 통합 테스트 Spring Boot 애플리케이션 생성
    - 테스트용 Spring Boot 애플리케이션 프로젝트 생성
    - infrastructure-analyzer-spring-boot-starter 의존성 추가
    - 소스코드 변경 없이 애플리케이션 시작
    - _Requirements: 12.1, 12.2, 12.3, 12.4, 12.5, 13.1, 13.2, 13.3, 13.4_
  
  - [ ] 11.2 requirements.json 테스트 파일 생성
    - src/main/resources/infrastructure/requirements.json 생성
    - 테스트용 FileCheck, ApiCheck, DirectoryCheck 포함
    - _Requirements: 1.1, 1.3, 1.4_
  
  - [ ] 11.3 프로파일별 requirements 파일 생성
    - requirements-local.json, requirements-dev.json, requirements-stage.json, requirements-prod.json 생성
    - 각 프로파일별 다른 검증 항목 포함
    - _Requirements: 1.2_
  
  - [ ]* 11.4 제로 코드 변경 통합 테스트
    - 의존성만으로 자동 활성화 확인
    - 소스코드에 어노테이션, import, Bean 정의 없음 확인
    - ApplicationContextInitializer 자동 등록 확인
    - _Requirements: 12.1, 12.2, 12.3, 12.4, 12.5, 12.7, 12.8_
  
  - [ ]* 11.5 의존성 활성화 속성 테스트
    - **Property 25: Automatic verification with spec**
    - **Validates: Requirements 13.5**

- [ ] 12. 프로파일별 구성 및 비활성화
  - [ ] 12.1 프로파일별 application.yml 생성
    - application-local.yml, application-dev.yml, application-stage.yml, application-prod.yml
    - 각 프로파일별 infrastructure.verification 설정
    - _Requirements: 8.1, 8.2, 8.3, 8.4_
  
  - [ ] 12.2 선택적 비활성화 구현 테스트
    - application-local.yml에 infrastructure.verification.enabled: false 설정
    - local 프로파일에서만 비활성화 확인
    - 비활성화 시 INFO 로깅 확인
    - _Requirements: 14.1, 14.2, 14.3, 14.4, 14.5_
  
  - [ ]* 12.3 프로파일별 비활성화 속성 테스트
    - **Property 24: Profile-specific disabling**
    - **Validates: Requirements 14.2**

- [ ] 13. Gradle 및 Maven 호환성 검증
  - [ ] 13.1 Gradle 프로젝트 통합 테스트
    - Gradle 프로젝트에서 starter 의존성 추가
    - 빌드 및 실행 확인
    - _Requirements: 9.1, 9.2, 13.1_
  
  - [ ] 13.2 Maven 프로젝트 통합 테스트
    - Maven 프로젝트에서 starter 의존성 추가
    - 빌드 및 실행 확인
    - _Requirements: 9.1, 9.2, 13.2_
  
  - [ ]* 13.3 빌드 도구 호환성 단위 테스트
    - Requirements JSON 형식 동일성 확인
    - Gradle/Maven 전용 아티팩트 의존성 없음 확인
    - _Requirements: 9.2, 9.3_

- [ ] 14. 최종 통합 및 문서화
  - [ ] 14.1 README.md 작성
    - 설치 방법 (Gradle, Maven)
    - 사용 방법 (의존성 추가만)
    - 구성 속성 설명
    - 프로파일별 설정 예제
    - _Requirements: 13.3, 13.4_
  
  - [ ] 14.2 최종 통합 테스트 실행
    - 모든 프로파일 (local, dev, stage, prod) 테스트
    - strict/lenient 모드 동작 확인
    - 검증 실패 시나리오 테스트
    - 로깅 출력 확인
    - _Requirements: 7.1, 7.2, 7.3, 7.5, 7.6_

- [ ] 15. Final checkpoint - 모든 테스트 통과 확인
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties
- Unit tests validate specific examples and edge cases
- 제로 코드 변경 원칙: 소스코드 변경 없이 의존성 추가만으로 활성화
- Spring Boot 2.x 및 3.x 모두 지원 (spring.factories + AutoConfiguration.imports)
- 프로파일별 검증 지원: local, dev, stage, prod
