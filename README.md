# Infrastructure Analyzer Plugin

배포 전 인프라 검증을 자동화하는 플러그인입니다. **Gradle**과 **Maven** 프로젝트 모두 지원합니다.

## 개요

Spring Boot 프로젝트의 설정 파일(`application.yml` / `application.properties`)을 분석하여 배포 전에 필요한 인프라 항목(파일, API, K8s 리소스)을 자동으로 추출하고 검증 스크립트를 생성합니다.

## 주요 기능

- **자동 환경 감지**: VM/쿠버네티스 환경 자동 감지
- **다양한 설정 파일 지원**: application.yml 및 application.properties 모두 지원
- **하이브리드 추출**: 명시적 선언 우선 + 자동 추출 Fallback + 소스코드 분석
- **소스코드 정적 분석**: Java 소스코드에서 하드코딩된 파일 경로 및 API URL 자동 검출
- **프로파일별 생성**: dev, stage, prod 환경별 requirements.json 생성
- **검증 스크립트 자동 생성**: VM/K8s 환경에 맞는 검증 스크립트 자동 복사
- **회사 도메인 우선 검증**: 회사 도메인은 critical, 외부 도메인은 경고만
- **멀티모듈 지원**: 공유 설정 파일 경로 지정 가능 (Maven `configDir` 파라미터)

## 프로젝트 구조

```
infrastructure-analyzer-plugin/
├── infrastructure-analyzer-core/        # 공통 분석 로직 (Gradle/Maven 공유)
│   └── src/main/java/io/infracheck/core/
│       ├── analyzer/                    # DeploymentDetector, InfrastructureExtractor, SourceCodeAnalyzer
│       ├── model/                       # Requirements, FileCheck, ApiCheck 등
│       └── util/                        # ConfigParser, YamlParser, PatternMatcher
├── infrastructure-analyzer-maven/       # Maven 플러그인
│   └── src/main/java/io/infracheck/maven/
│       └── InfrastructureAnalyzerMojo.java
├── src/main/java/io/infracheck/gradle/  # Gradle 플러그인
│   ├── InfrastructureAnalyzerPlugin.java
│   ├── InfrastructureAnalyzerTask.java
│   └── analyzer/DeploymentDetector.java
└── src/main/resources/                  # 검증 쉘 스크립트
    ├── validate-infrastructure.sh
    └── validate-k8s-infrastructure.sh
```

---

## Gradle 플러그인

### 빌드 및 배포

```bash
# Maven Local에 배포 (로컬 테스트용)
./gradlew clean publishToMavenLocal

# Nexus에 배포 (운영)
export NEXUS_USERNAME="your-username"
export NEXUS_PASSWORD="your-password"
./gradlew publish
```

### 프로젝트에 플러그인 추가

**settings.gradle:**
```gradle
pluginManagement {
    repositories {
        mavenLocal()  // 로컬 테스트용 - 운영 배포 시 제거
        maven { url 'https://nexus.company.com/repository/maven-public/' }
        gradlePluginPortal()
    }
}
```

**build.gradle:**
```gradle
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.4.1'
    id 'io.infracheck.infrastructure-analyzer' version '1.0.1'
}
```

### 실행

```bash
./gradlew build
# 또는 단독 실행
./gradlew analyzeInfrastructure
```

**출력 예시:**
```
> Task :analyzeInfrastructure
✅ 감지된 배포 환경: VM
📄 설정 파일: application.yml
✅ 생성됨: requirements-dev.json
✅ 생성됨: requirements-stage.json
✅ 생성됨: requirements-prod.json
✅ 생성됨: build/infrastructure/validate-infrastructure.sh
```

### 멀티모듈 Gradle 프로젝트

배포 대상 서브모듈에만 플러그인을 적용합니다.

```bash
# 특정 서브모듈만 분석
./gradlew :api-server:analyzeInfrastructure

# 플러그인이 적용된 모든 서브모듈 한번에
./gradlew analyzeInfrastructure
```

---

## Maven 플러그인

### 빌드 및 배포

```bash
MVN=/Users/jnote/.m2/wrapper/dists/apache-maven-3.9.9-bin/.../bin/mvn

# core 먼저 설치
$MVN clean install -f infrastructure-analyzer-core/pom.xml

# Maven 플러그인 설치
$MVN clean install -f infrastructure-analyzer-maven/pom.xml

# Nexus에 배포 (운영)
$MVN deploy -f infrastructure-analyzer-core/pom.xml -DnexusUrl=https://nexus.company.com/...
$MVN deploy -f infrastructure-analyzer-maven/pom.xml -DnexusUrl=https://nexus.company.com/...
```

### 기본 설정 (단일 모듈)

**pom.xml:**
```xml
<plugin>
    <groupId>io.infracheck</groupId>
    <artifactId>infrastructure-analyzer-maven-plugin</artifactId>
    <version>1.0.1</version>
    <executions>
        <execution>
            <goals><goal>analyze</goal></goals>
        </execution>
    </executions>
</plugin>
```

### 멀티모듈 Maven 프로젝트

`web-api` 모듈이 `service-global`의 `application.yml`을 공유하는 경우처럼, 설정 파일이 다른 모듈에 있을 때 `configDir`을 사용합니다.

**web-api/pom.xml:**
```xml
<plugin>
    <groupId>io.infracheck</groupId>
    <artifactId>infrastructure-analyzer-maven-plugin</artifactId>
    <version>1.0.1</version>
    <configuration>
        <!-- 설정 파일이 있는 모듈 경로 지정 -->
        <configDir>${project.basedir}/../service-global</configDir>
        <!-- 커스텀 프로파일 목록 (기본값: dev, stage, prod) -->
        <profiles>
            <profile>dev</profile>
            <profile>stage</profile>
            <profile>prod</profile>
        </profiles>
    </configuration>
    <executions>
        <execution>
            <goals><goal>analyze</goal></goals>
        </execution>
    </executions>
</plugin>
```

> `configDir`은 `src/main/resources/application.yml`을 포함하는 모듈 루트 경로를 지정합니다.
> 이미 resources 디렉토리 경로를 직접 지정해도 동작합니다.

### 단독 실행

```bash
mvn io.infracheck:infrastructure-analyzer-maven-plugin:analyze
```

### 산출물 위치

Maven 프로젝트는 `target/infrastructure/`에 생성됩니다:

```
target/infrastructure/
├── requirements-dev.json
├── requirements-stage.json
├── requirements-prod.json
└── validate-infrastructure.sh
```

---

## application.yml 설정

명시적으로 검증 항목을 선언하거나, 자동 추출에 맡길 수 있습니다.

```yaml
infrastructure:
  validation:
    company-domain: "company.co.kr"  # 회사 도메인 (필수)

    # 소스코드 정적 분석 (기본값: true)
    source-code-analysis:
      enabled: false  # 분석이 느릴 경우 비활성화

    # 제외 패턴 (선택)
    exclude-patterns:
      - "localhost"
      - "127.0.0.1"
      - "sandbox."

    # 명시적 파일 선언 (선택 - 없으면 자동 추출)
    files:
      - path: "/nas/key/signed.der"
        critical: true
        description: "CDN 서명 키"

    # 명시적 API 선언 (선택 - 없으면 자동 추출)
    apis:
      - url: "https://api.company.co.kr/v1/health"
        critical: true
        description: "메인 API"

    # 디렉토리 권한 (선택, VM 전용)
    directories:
      - path: "/var/log/myapp"
        permissions: "rwx"
        critical: true
        description: "로그 디렉토리"
```

---

## 생성되는 파일

### VM 환경

```
build/infrastructure/          (Gradle)
target/infrastructure/         (Maven)
├── requirements-dev.json
├── requirements-stage.json
├── requirements-prod.json
└── validate-infrastructure.sh
```

### 쿠버네티스 환경

```
build/infrastructure/
├── requirements-k8s-dev.json
├── requirements-k8s-stage.json
├── requirements-k8s-prod.json
└── validate-k8s-infrastructure.sh
```

---

## 환경 감지 로직

**쿠버네티스 감지 조건 (하나라도 해당하면 K8s):**
- build.gradle에 jib, thin-launcher 플러그인 존재
- application.yml에 `kubernetes.io`, `k8s.`, `livenessstate`, `readinessstate` 키워드
- k8s 디렉토리 존재

**기본값:** VM/물리 서버

---

## 추출 전략

1. **명시적 선언 우선** - `infrastructure.validation.files/apis` 섹션에 선언된 항목
2. **설정 파일 자동 추출** - 명시적 선언이 없으면 패턴 기반 자동 추출
3. **소스코드 정적 분석** - JavaParser AST 기반으로 하드코딩된 경로/URL 검출

---

## 검증 모드

- **dev/stage**: 경고만 표시 (`STRICT_MODE=false`)
- **prod**: 검증 실패 시 배포 차단 (`STRICT_MODE=true`)

---

## Bamboo 파이프라인 설정

### Build Plan

```yaml
Tasks:
  - Source Code Checkout
  - Build: ./gradlew clean build  (또는 mvn clean package)
  - Artifact Definition:
      Name: infrastructure
      Location: build/infrastructure   (또는 target/infrastructure)
      Copy Pattern: **
```

### Deployment Plan (VM)

```bash
bash validate-infrastructure.sh prod
```

### Deployment Plan (K8s)

```bash
bash validate-k8s-infrastructure.sh prod production
```

---

## mavenLocal() 관리

플러그인이 빌드 시 자동으로 `settings.gradle`의 `mavenLocal()` 사용을 감지합니다:

- **로컬 환경**: INFO 경고 (개발 중에는 정상)
- **CI/CD 환경**: ERROR 경고 (Bamboo, Jenkins, GitLab CI, GitHub Actions 등 자동 감지)

---

## 트러블슈팅

### 플러그인을 찾을 수 없음

```
Plugin [id: 'io.infracheck.infrastructure-analyzer'] was not found
```

`publishToMavenLocal` 또는 `publish` 실행 후 `settings.gradle` 저장소 설정 확인

### 이전 버전이 캐시됨

```bash
rm -rf ~/.gradle/caches/modules-2/files-2.1/io.infracheck/
./gradlew clean build --refresh-dependencies
```

### application.yml을 찾을 수 없음 (Maven 멀티모듈)

`configDir`이 올바른 모듈 루트를 가리키는지 확인합니다. 플러그인은 `{configDir}/src/main/resources/application.yml` 순서로 탐색합니다.

---

## 버전

- **현재 버전**: 1.0.1
- **Java**: 17
- **Gradle**: 8.x
- **의존성**: gson 2.10.1, snakeyaml 2.2, javaparser-core 3.25.8

## 참고 문서

- [Local Test Guide](LOCAL_TEST_GUIDE.md)
- [AI Implementation Plan](docs/AI-Implementation-Plan.md)
- [Source Code Analysis Plan](docs/Source-Code-Analysis-Plan.md)
