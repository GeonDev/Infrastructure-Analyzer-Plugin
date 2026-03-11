# 로컬 테스트 가이드 (통합 버전)

이 가이드는 통합된 **Infrastructure Analyzer**를 로컬 환경에서 테스트하는 방법을 안내합니다.

## 1단계: 모듈 설치 (Maven Local)

루트 프로젝트에서 단 한 번의 명령으로 모든 기능이 포함된 JAR를 로컬 저장소(`~/.m2/repository/`)에 설치합니다.

```bash
# 프로젝트 루트에서 실행
./gradlew clean publishToMavenLocal
```

**설치 확인:**
```bash
ls -la ~/.m2/repository/io/infracheck/infrastructure-analyzer/1.0.0-SNAPSHOT/
```

---

## 2단계: 테스트 프로젝트 설정

테스트할 Spring Boot 프로젝트의 `settings.gradle`과 `build.gradle`을 다음과 같이 설정합니다.

#### settings.gradle (테스트 프로젝트)
```gradle
pluginManagement {
    repositories {
        mavenLocal()  // 로컬 저장소 우선 탐색
        gradlePluginPortal()
        mavenCentral()
    }
}
```

#### build.gradle (테스트 프로젝트)
```gradle
plugins {
    // 플러그인만 추가하면 분석(Build-time)과 검증(Runtime) 기능이 모두 활성화됩니다.
    id 'io.infracheck.infrastructure-analyzer' version '1.0.0-SNAPSHOT'
}

// dependencies { implementation '...' } 섹션에 스타터를 직접 추가할 필요가 없습니다.
```

---

## 3단계: 인프라 명세 생성 및 검증 테스트

### 1. 명세 생성 (Build-time)
빌드를 수행하여 `requirements-{profile}.json` 파일들이 생성되는지 확인합니다.

```bash
./gradlew analyzeInfrastructure
```

### 2. 자동 검증 (Runtime)
애플리케이션을 실행하여 실제 인프라 검증 로그를 확인합니다. `local` 및 `dev` 프로파일은 경고만 출력하며, `stage` 및 `prod` 프로파일은 검증 실패 시 기동을 중단하는 엄격 모드(Strict Mode)로 동작합니다.

```bash
# 기본(local) 프로파일로 실행하여 검증 경고 로그 확인
./gradlew bootRun --args='--spring.profiles.active=local'

# prod 또는 stage 프로파일로 실행하여 엄격 모드(Strict Mode) 테스트
./gradlew bootRun --args='--spring.profiles.active=stage'
```

**예상 로그:**
```
INFO: Starting infrastructure verification (profile: prod, path: requirements-prod.json)
DEBUG: Verifying API: https://api.company.co.kr
INFO: Infrastructure verification completed successfully. Passed: 5/5 (120ms)
```

---

### 💡 참고 사항 (로컬 bootRun 테스트 시)

`bootRun`을 사용해 로컬 환경에서 기동 시점에 검증을 테스트하려면, 로컬 빌드 구조의 특성상 생성된 `requirements-*.json` 파일을 직접 가리키도록 설정해야 합니다. 컨테이너/서버 환경의 완성된 JAR 내부에서는 기본값으로 잘 동작하지만, 로컬 코딩 중에는 아래 설정을 추가해 주세요.

`src/main/resources/application.yml`:
```yaml
infrastructure:
  validation:
    verification:
      enabled: true
      fail-on-error: true
      requirements-path: "file:build/infrastructure/requirements-{profile}.json"  # 로컬 bootRun 용도
```

---

## 트러블슈팅

### 캐시 문제
수정 사항이 반영되지 않을 경우 로컬 캐시를 삭제하고 재빌드하세요.
```bash
rm -rf ~/.gradle/caches/modules-2/files-2.1/io.infracheck/
./gradlew clean build --refresh-dependencies
```

---

## 원격 배포 (Nexus)
테스트가 완료되면 Nexus 저장소에 배포합니다.
```bash
export NEXUS_USERNAME="user"
export NEXUS_PASSWORD="password"
./gradlew publish
```
