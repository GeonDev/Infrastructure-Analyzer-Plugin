# 로컬 테스트 가이드

플러그인을 Nexus에 배포하기 전에 로컬에서 테스트하는 방법입니다.

## 1단계: 플러그인을 Maven Local에 배포

```bash
cd infrastructure-analyzer-plugin
../gradlew publishToMavenLocal
```

이 명령은 플러그인을 `~/.m2/repository/`에 설치합니다.

**확인:**
```bash
ls -la ~/.m2/repository/com/company/gradle/infrastructure-analyzer-plugin/1.0.0/
```

다음 파일들이 있어야 합니다:
- `infrastructure-analyzer-plugin-1.0.0.jar`
- `infrastructure-analyzer-plugin-1.0.0.pom`
- `infrastructure-analyzer-plugin-1.0.0.module`

---

## 2단계: 테스트 프로젝트 설정

### 현재 프로젝트(qa-agent-server)에서 테스트

#### settings.gradle 수정

```gradle
pluginManagement {
    repositories {
        mavenLocal()  // 로컬 Maven 저장소 추가
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = 'qa-agent-server'
```

#### build.gradle 수정

```gradle
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.4.1'
    id 'io.spring.dependency-management' version '1.1.7'
    id 'com.company.infrastructure-analyzer' version '1.0.0'  // ← 추가
}

// ... 나머지 설정
```

#### application.yml에 테스트 설정 추가 (선택)

```yaml
# src/main/resources/application.yml
infrastructure:
  validation:
    company-domain: "abc.co.kr"  # 회사 도메인 설정
    
    # 명시적 파일 선언 (선택)
    files:
      - path: "/nas2/was/key/test.pem"
        critical: true
        description: "테스트 인증서"
    
    # 명시적 API 선언 (선택)
    apis:
      - url: "https://api.abc.co.kr"
        critical: true
        description: "메인 API"
      - url: "https://www.google.com"
        critical: false
        description: "외부 API (경고만)"
    
    # 제외 패턴 (선택)
    exclude-patterns:
      - "localhost"
      - "127.0.0.1"
      - "*.local"

# 기존 설정...
spring:
  application:
    name: qa-agent-server
```

---

## 3단계: 빌드 실행

```bash
cd ..  # 루트 프로젝트로 이동
./gradlew clean build
```

**예상 출력 (로컬 환경):**
```
> Task :analyzeInfrastructure
⚠️  INFO: settings.gradle에 mavenLocal()이 설정되어 있습니다.
⚠️  로컬 테스트 중이라면 정상입니다.
⚠️  운영 배포 전에는 반드시 제거하세요.
✅ 감지된 배포 환경: VM
📄 설정 파일: application.yml
🔍 소스코드 분석 활성화
✅ 생성됨: requirements-dev.json
✅ 생성됨: requirements-stg.json
✅ 생성됨: requirements-prod.json
✅ 생성됨: build/infrastructure/validate-infrastructure.sh

BUILD SUCCESSFUL
```

**참고:** `mavenLocal()` 경고는 로컬 개발 중에는 정상입니다. CI/CD 환경(Bamboo 등)에서는 더 강력한 ERROR 경고가 표시됩니다.

---

## 4단계: 생성된 파일 확인

### requirements.json 파일 확인

```bash
# 빌드 디렉토리에 생성됨
cat build/infrastructure/requirements-dev.json
cat build/infrastructure/requirements-stg.json
cat build/infrastructure/requirements-prod.json
```

**예시 출력 (requirements-prod.json):**
```json
{
  "version": "1.0",
  "project": "qa-agent-server",
  "environment": "prod",
  "platform": "vm",
  "infrastructure": {
    "company_domain": "abc.co.kr",
    "files": [
      {
        "path": "/nas2/was/key/test.pem",
        "location": "nas",
        "critical": true,
        "description": "테스트 인증서"
      }
    ],
    "external_apis": [
      {
        "url": "https://api.abc.co.kr",
        "method": "HEAD",
        "expectedStatus": [200, 301, 302, 401, 403, 404],
        "critical": true,
        "description": "메인 API"
      },
      {
        "url": "https://www.google.com",
        "method": "HEAD",
        "expectedStatus": [200, 301, 302, 401, 403, 404],
        "critical": false,
        "description": "외부 API (경고만)"
      }
    ]
  }
}
```

### 검증 스크립트 확인

```bash
ls -la bamboo-scripts/
cat bamboo-scripts/validate-infrastructure.sh
```

---

## 5단계: 검증 스크립트 테스트 (선택)

검증 스크립트를 로컬에서 실행해볼 수 있습니다:

```bash
# requirements.json을 루트로 복사
cp build/infrastructure/requirements-prod.json .

# 스크립트 실행 (SSH 접속 정보 필요)
export PROD_SERVER_HOST="your-server.com"
export PROD_SERVER_USER="deploy"

bash bamboo-scripts/validate-infrastructure.sh prod
```

**주의:** SSH 접속이 설정되어 있지 않으면 경고만 표시되고 종료됩니다.

---

## 6단계: 플러그인 수정 후 재테스트

플러그인 코드를 수정한 경우:

```bash
# 1. 플러그인 재빌드 및 재배포
cd infrastructure-analyzer-plugin
../gradlew clean publishToMavenLocal

# 2. 테스트 프로젝트 재빌드
cd ..
./gradlew clean build
```

---

## 트러블슈팅

### 문제 1: 플러그인을 찾을 수 없음

**에러:**
```
Plugin [id: 'com.company.infrastructure-analyzer', version: '1.0.0'] was not found
```

**해결:**
1. `publishToMavenLocal`이 성공했는지 확인
2. `~/.m2/repository/com/company/gradle/infrastructure-analyzer-plugin/1.0.0/` 디렉토리 존재 확인
3. `settings.gradle`에 `mavenLocal()` 추가 확인

### 문제 2: 이전 버전이 캐시됨

**해결:**
```bash
# Gradle 캐시 삭제
rm -rf ~/.gradle/caches/modules-2/files-2.1/com.company.gradle/infrastructure-analyzer-plugin

# 재빌드
./gradlew clean build --refresh-dependencies
```

### 문제 3: application.yml을 찾을 수 없음

**에러:**
```
⚠️  application.yml을 찾을 수 없습니다
```

**해결:**
- `src/main/resources/application.yml` 파일이 존재하는지 확인
- Spring Boot 프로젝트가 아닌 경우 플러그인이 동작하지 않을 수 있음

---

## 다른 프로젝트에서 테스트

다른 Spring Boot 프로젝트가 있다면:

1. 해당 프로젝트의 `settings.gradle`에 `mavenLocal()` 추가
2. `build.gradle`에 플러그인 추가
3. `./gradlew build` 실행

---

## 정리

로컬 테스트가 완료되면:

```bash
# 생성된 파일 정리 (선택)
rm -rf build/infrastructure/
rm -rf bamboo-scripts/
rm requirements-*.json

# Git에서 제외 (.gitignore에 추가)
echo "build/infrastructure/" >> .gitignore
echo "requirements-*.json" >> .gitignore
```

---

## 다음 단계

로컬 테스트가 성공하면 Nexus에 배포:

```bash
cd infrastructure-analyzer-plugin
export NEXUS_USERNAME="your-username"
export NEXUS_PASSWORD="your-password"
../gradlew publish
```
