# Infrastructure Analyzer Project (All-in-One)

인프라 요구사항 분석(Analyzer), 빌드 플러그인(Gradle/Maven), 그리고 런타임 검증(Starter) 기능이 하나로 통합된 인프라 검증 통합 솔루션입니다.

## 개요

이 프로젝트는 애플리케이션의 인프라 의존성을 자동으로 파악하고, 배포 시점에 이를 실제 환경에서 검증합니다. 모든 로직이 하나의 모듈에 통합되어 관리가 용이하며 버전 불일치 걱정 없이 사용할 수 있습니다.

## 주요 기능

- **통합 관리**: Core 로직, Gradle/Maven 플러그인, Spring Boot Starter가 하나의 JAR로 제공됩니다.
- **자동 의존성 주입**: Gradle 플러그인 적용 시, 런타임 검증을 위한 Starter 라이브러리가 프로젝트에 자동으로 포함됩니다.
- **파일명 표준화**: 어떤 환경(local, dev, stage, prod)에서도 `requirements-{profile}.json` 명세를 생성하여 일관된 검증을 제공합니다.
- **실행 환경 독립성**: 애플리케이션 자체가 VM에서 실행되든 Kubernetes에서 실행되든 상관없이, 외부 연결(API/DB), 파일 시스템 등 자신이 필요로 하는 런타임 인프라 요소만 독립적으로 검증합니다.
- **제로 코드 검증**: 소스 코드 수정 없이 설정만으로 애플리케이션 시작 시 자동 인프라 체크를 수행합니다.

## 프로젝트 구조

```
infrastructure-analyzer/
├── src/main/java/io/infracheck/
│   ├── core/        # 공통 분석 로직 및 모델 (Shared Logic)
│   ├── gradle/      # Gradle 분석 플러그인 및 태스크
│   ├── maven/       # Maven 분석 플러그인 (Mojo)
│   └── spring/      # 런타임 검증 스타터 (Initializer, Verifiers)
└── src/main/resources/
    └── META-INF/    # Gradle 플러그인 및 Spring Boot 자동 구성 설정
```

---

## 설치 및 배포

단 한 번의 빌드로 모든 컴포넌트가 설치됩니다.

```bash
# 로컬 Maven 저장소에 설치 (테스트용)
./gradlew clean publishToMavenLocal

# 원격 저장소(Nexus)에 배포
./gradlew publish
```

---

## 사용 방법 (Gradle)

**build.gradle:**
```gradle
plugins {
    // 플러그인 적용만으로 분석 + 런타임 검증 기능이 모두 활성화됩니다.
    id 'io.infracheck.infrastructure-analyzer' version '1.0.0-SNAPSHOT'
}
```

- **빌드 시**: 자동으로 인프라를 분석하여 `build/infrastructure/requirements-{profile}.json`을 생성합니다.
- **실행 시**: 생성된 명세를 읽어 애플리케이션 시작 전 인프라 상태(파일, API, 권한)를 자동으로 검증합니다.

---

## 사용 방법 (Maven)

**pom.xml:**
```xml
<plugin>
    <groupId>io.infracheck</groupId>
    <artifactId>infrastructure-analyzer</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <executions>
        <goal>analyze</goal>
    </executions>
</plugin>

<!-- 런타임 검증을 위해 의존성 추가 -->
<dependency>
    <groupId>io.infracheck</groupId>
    <artifactId>infrastructure-analyzer</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

---

## 구성 속성 (application.yml)

```yaml
infrastructure:
  validation:
    domain: "company.co.kr"  # 필수 설정
    verification:
      enabled: true                 # 검증 활성화 여부
      fail-on-error: true           # 실패 시 기동 중단 (prod, stage는 기본 true)
      timeout-seconds: 5            # API 응답 대기 시간
```

---

## 상세 가이드
- [사용자 가이드 (설정 및 배포)](USAGE_GUIDE.md)
- [로컬 테스트 가이드](LOCAL_TEST_GUIDE.md)
- [디자인 문서](.kiro/specs/runtime-verification-starter/design.md)
