# Infrastructure Analyzer Gradle Plugin

배포 전 인프라 검증을 자동화하는 Gradle 플러그인입니다.

## 개요

이 플러그인은 Spring Boot 프로젝트의 설정 파일(`application.yml` 또는 `application.properties`)을 분석하여 배포 전에 필요한 인프라 항목(파일, API, K8s 리소스)을 자동으로 추출하고 검증 스크립트를 생성합니다.

## 주요 기능

- **자동 환경 감지**: VM/쿠버네티스 환경 자동 감지
- **다양한 설정 파일 지원**: application.yml 및 application.properties 모두 지원
- **하이브리드 추출**: 명시적 선언 우선 + 자동 추출 Fallback + 소스코드 분석
- **소스코드 정적 분석**: Java 소스코드에서 하드코딩된 파일 경로 및 API URL 자동 검출
- **프로파일별 생성**: dev, stg, prod 환경별 requirements.json 생성
- **검증 스크립트 자동 생성**: VM/K8s 환경에 맞는 검증 스크립트 자동 복사
- **회사 도메인 우선 검증**: 회사 도메인은 critical, 외부 도메인은 경고만
- **디렉토리 권한 검증**: VM 환경에서 디렉토리 읽기/쓰기/실행 권한 자동 검증

## 설치

### 1. Maven Local에 배포 (로컬 테스트용)

```bash
cd infrastructure-analyzer-plugin

# 클린 빌드
./gradlew clean build

# Maven Local에 배포
./gradlew publishToMavenLocal
```

### 2. Nexus에 배포 (운영 배포용)

```bash
cd infrastructure-analyzer-plugin
export NEXUS_USERNAME="your-username"
export NEXUS_PASSWORD="your-password"
./gradlew publish
```

## 사용법

### 개발 흐름

1. **로컬 테스트 단계**
   - `settings.gradle`에 `mavenLocal()` 포함
   - `./gradlew publishToMavenLocal`로 로컬 배포
   - 테스트 프로젝트에서 플러그인 동작 확인

2. **Nexus 배포 단계**
   - `./gradlew publish`로 Nexus에 배포
   - 다른 팀원들이 사용 가능

3. **운영 사용 단계**
   - `settings.gradle`에서 `mavenLocal()` 제거
   - Nexus에서만 플러그인 다운로드

> **💡 Tip**: 플러그인이 자동으로 `mavenLocal()` 사용을 감지하고 경고를 출력합니다. 
> 빌드 로그에서 경고 메시지를 확인하세요!

### mavenLocal() 제거 방지 대책

플러그인은 다층 방어 전략으로 실수를 방지합니다:

#### 1. 자동 경고 (플러그인 내장)

플러그인이 빌드 시 자동으로 `mavenLocal()` 사용을 감지합니다:

- **로컬 환경**: 부드러운 INFO 경고 (개발 중에는 정상)
- **CI/CD 환경**: 강력한 ERROR 경고 (Bamboo, Jenkins 등 자동 감지)

```
# 로컬 개발 시
⚠️  INFO: settings.gradle에 mavenLocal()이 설정되어 있습니다.
⚠️  로컬 테스트 중이라면 정상입니다.

# CI/CD 환경 시
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
❌ CRITICAL: settings.gradle에 mavenLocal()이 설정되어 있습니다!
❌ CI/CD 환경에서는 mavenLocal() 사용이 금지됩니다.
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

감지되는 CI 환경:
- Bamboo (`BAMBOO_BUILD_NUMBER`)
- Jenkins (`JENKINS_HOME`)
- GitLab CI (`GITLAB_CI`)
- GitHub Actions (`GITHUB_ACTIONS`)
- CircleCI, Travis CI 등

#### 2. Git Hook (선택 사항)

로컬에서 커밋 전 검사:

**.git/hooks/pre-commit:**
```bash
#!/bin/bash
if grep -q "mavenLocal()" settings.gradle* 2>/dev/null; then
    echo "⚠️  WARNING: settings.gradle에 mavenLocal()이 포함되어 있습니다."
    echo "운영 배포 시 제거가 필요합니다. 계속하시겠습니까? (y/n)"
    read -r response
    if [[ ! "$response" =~ ^[Yy]$ ]]; then
        exit 1
    fi
fi
```

#### 3. CI/CD 검증 (권장)

Bamboo Script Task 추가:

```bash
# settings.gradle 검증
if grep -q "mavenLocal()" settings.gradle* 2>/dev/null; then
    echo "❌ ERROR: mavenLocal()이 settings.gradle에 포함되어 있습니다!"
    echo "운영 배포에서는 Nexus만 사용해야 합니다."
    exit 1
fi
```

#### 4. 코드 리뷰

PR 체크리스트:
- [ ] settings.gradle에 `mavenLocal()` 없음
- [ ] Nexus 저장소만 사용

### 1. 프로젝트에 플러그인 추가

**settings.gradle (로컬 테스트 시):**
```gradle
pluginManagement {
    repositories {
        mavenLocal()  // 로컬 테스트용 - 운영 배포 시 제거
        maven { url 'https://nexus.company.com/repository/maven-public/' }
        gradlePluginPortal()
    }
}
```

**settings.gradle (운영 배포 후):**
```gradle
pluginManagement {
    repositories {
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
    id 'io.infracheck.infrastructure-analyzer' version '1.0.0'
}
}
```

### 2. application.yml 또는 application.properties 설정 (선택)

명시적으로 검증 항목을 선언하거나, 자동 추출에 맡길 수 있습니다.

**application.yml 예시:**
```yaml
infrastructure:
  validation:
    company-domain: "abc.co.kr"  # 회사 도메인 설정
    
    # 명시적 파일 선언 (선택)
    files:
      - path: "key/signed.der"
        critical: true
        description: "CDN 서명 키"
      - path: "/home/app/config/app.properties"
        critical: false
        description: "설정 파일"
    
    # 명시적 API 선언 (선택)
    apis:
      - url: "https://api.abc.co.kr/v1/health"
        critical: true
        description: "메인 API"
      - url: "https://payment.inicis.com"
        critical: false
        description: "결제 시스템 (외부)"
    
    # 명시적 디렉토리 권한 선언 (선택, VM 전용)
    directories:
      - path: "/var/log/myapp"
        permissions: "rwx"  # 읽기/쓰기/실행
        critical: true
        description: "애플리케이션 로그 디렉토리"
      - path: "/data/uploads"
        permissions: "rw"   # 읽기/쓰기만
        critical: true
        description: "파일 업로드 디렉토리"
    
    # 제외 패턴 (선택)
    exclude-patterns:
      - "localhost"
      - "127.0.0.1"
      - "*.local"
```

**application.properties 예시:**
```properties
# 회사 도메인 설정
infrastructure.validation.company-domain=abc.co.kr

# 명시적 파일 선언 (선택)
infrastructure.validation.files[0].path=/key/signed.der
infrastructure.validation.files[0].critical=true
infrastructure.validation.files[0].description=CDN 서명 키

# 명시적 API 선언 (선택)
infrastructure.validation.apis[0].url=https://api.abc.co.kr/v1/health
infrastructure.validation.apis[0].critical=true
infrastructure.validation.apis[0].description=메인 API

# 제외 패턴 (선택)
infrastructure.validation.exclude-patterns[0]=localhost
infrastructure.validation.exclude-patterns[1]=127.0.0.1
```

### 3. 빌드 실행

```bash
./gradlew build
```

**출력 예시:**
```
> Task :analyzeInfrastructure
✅ 감지된 배포 환경: VM
📄 설정 파일: application.yml
🔍 소스코드 분석 활성화
✅ 생성됨: requirements-dev.json
✅ 생성됨: requirements-stg.json
✅ 생성됨: requirements-prod.json
✅ 생성됨: build/infrastructure/validate-infrastructure.sh
```

### 4. 생성된 파일 확인

```bash
# 모든 산출물이 build/infrastructure/에 생성됨
ls -la build/infrastructure/

# requirements.json 파일 확인
cat build/infrastructure/requirements-prod.json
```

### 5. Bamboo에서 사용

Bamboo Build Plan에서 Artifact 정의:

```yaml
Artifact Definition:
  - Name: infrastructure
    Location: build/infrastructure
    Copy Pattern: **
```

이제 Git 커밋 없이 빌드만 하면 모든 검증 파일이 자동 생성됩니다.

## 생성되는 파일

모든 산출물이 `build/infrastructure/` 디렉토리에 생성됩니다:

### VM 환경

- `build/infrastructure/requirements-{profile}.json` - 검증 항목 정의
- `build/infrastructure/validate-infrastructure.sh` - VM 검증 스크립트

### 쿠버네티스 환경

- `build/infrastructure/requirements-k8s-{profile}.json` - 검증 항목 정의
- `build/infrastructure/validate-k8s-infrastructure.sh` - K8s 검증 스크립트

## 환경 감지 로직

플러그인은 다음 기준으로 배포 환경을 자동 감지합니다:

**쿠버네티스 감지 조건 (하나라도 해당하면 K8s):**
1. build.gradle에 쿠버네티스 플러그인 존재 (jib, thin-launcher)
2. application.yml에 쿠버네티스 관련 키워드
   - `kubernetes.io`, `k8s.`, `mkube-proxy`
   - `livenessstate`, `readinessstate`
3. k8s 디렉토리 존재

**기본값:** VM/물리 서버

## 추출 전략

### 하이브리드 방식

1. **명시적 선언 우선** (정확성)
   - `infrastructure.validation.files/apis` 섹션에 선언된 항목 사용
   - 가장 정확하고 프로젝트별 커스터마이징 가능

2. **설정 파일 자동 추출** (범용성)
   - 명시적 선언이 없으면 패턴 기반 자동 추출
   - 모든 프로젝트에서 동작하도록 범용적인 패턴 사용

3. **소스코드 정적 분석** (완전성)
   - Java 소스코드에서 하드코딩된 파일 경로 및 API URL 검출
   - JavaParser를 사용한 AST 기반 분석
   - 테스트 코드 및 로컬 경로 자동 제외

### 소스코드 분석 제어

```yaml
infrastructure:
  validation:
    source-code-analysis:
      enabled: true  # 기본값: true, false로 설정하면 비활성화
```

### 파일 경로 패턴

- 확장자 기반: `.der`, `.pem`, `.p8`, `.p12`, `.cer`, `.crt`, `.key`, `.jks`, `.keystore`
- 경로 기반: `/nas*`, `/mnt`, `/home`, `/var`, `/opt`

### URL 패턴

- `https?://[domain]` 형식의 URL 자동 감지
- 회사 도메인 포함 시 `critical: true`
- 외부 도메인은 `critical: false` (경고만)

### 디렉토리 권한 (VM 전용)

- 명시적 선언만 지원 (자동 추출 없음)
- 권한: `r` (읽기), `w` (쓰기), `x` (실행) 조합
- 예: `rwx`, `rw`, `rx`

## Bamboo 파이프라인 설정

### Build Plan

```yaml
Tasks:
  - Source Code Checkout
  - Gradle Build: ./gradlew clean build
  - Artifact Definition:
      Name: infrastructure
      Location: build/infrastructure
      Copy Pattern: **
```

### Deployment Plan (VM)

```yaml
Tasks:
  - Artifact Download
  - Infrastructure Validation:
      Script: bash validate-infrastructure.sh prod
      Working Directory: build/infrastructure
      Environment Variables:
        - PROD_SERVER_HOST=${bamboo.prod.server.host}
        - PROD_SERVER_USER=${bamboo.prod.server.user}
  - Deploy via SSH
```

### Deployment Plan (K8s)

```yaml
Tasks:
  - Artifact Download
  - K8s Infrastructure Validation:
      Script: bash validate-k8s-infrastructure.sh prod production
      Working Directory: build/infrastructure
      Environment Variables:
        - KUBECONFIG=${bamboo.k8s.prod.kubeconfig}
  - Deploy to Kubernetes
```

## 검증 모드

- **dev/stg**: 경고만 표시 (STRICT_MODE=false)
- **prod**: 검증 실패 시 배포 차단 (STRICT_MODE=true)

## 트러블슈팅

### 플러그인을 찾을 수 없음

```
Plugin [id: 'io.infracheck.infrastructure-analyzer'] was not found
```

**해결:**
1. `publishToMavenLocal` 또는 `publish` 실행 확인
2. `settings.gradle`에 저장소 추가 확인

### 이전 버전이 캐시됨

```bash
rm -rf ~/.gradle/caches/modules-2/files-2.1/io.infracheck/infrastructure-analyzer-plugin
./gradlew clean build --refresh-dependencies
```

### application.yml 또는 application.properties를 찾을 수 없음

```
⚠️  application.yml 또는 application.properties를 찾을 수 없습니다
```

**해결:**
- `src/main/resources/application.yml` 또는 `application.properties` 파일 존재 확인
- Spring Boot 프로젝트인지 확인

## 개발

### 플러그인 수정 후 재테스트

```bash
# 1. 플러그인 재빌드 및 재배포
cd infrastructure-analyzer-plugin
../gradlew clean publishToMavenLocal

# 2. 테스트 프로젝트 재빌드
cd ..
./gradlew clean build
```

## 버전

- **현재 버전**: 1.0.0
- **Java**: 17
- **Gradle**: 8.x
- **의존성**:
  - gson: 2.10.1
  - snakeyaml: 2.2
  - javaparser-core: 3.25.8

## 참고 문서

- [AI Implementation Plan](docs/AI-Implementation-Plan.md)
- [Source Code Analysis Plan](docs/Source-Code-Analysis-Plan.md)
- [Local Test Guide](LOCAL_TEST_GUIDE.md)
