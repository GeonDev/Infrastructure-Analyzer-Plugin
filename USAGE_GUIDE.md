# Infrastructure Analyzer 사용 가이드

`Infrastructure-Analyzer`는 소스 코드와 설정 파일에서 인프라 의존성(파일, API, 디렉토리 등)을 자동으로 추출하고, 애플리케이션 시작 시 해당 자원들이 실제로 존재하는지 검증하는 도구입니다.

---

## 1. 라이브러리 배포 (GitHub Packages)

현재 프로젝트는 GitHub Actions를 통해 `main` 브랜치에 푸시될 때 자동으로 GitHub Packages에 배포되도록 설정되어 있습니다.

- **저장소 위치:** `https://maven.pkg.github.com/GeonDev/Infrastructure-Analyzer-Plugin`
- **배포 버전:** `1.0.0-SNAPSHOT`

---

## 2. 클라이언트 프로젝트 설정 (Gradle)

이 라이브러리를 사용하려는 프로젝트의 `build.gradle`에 다음 설정을 추가합니다.

### 2.1 저장소 설정 (Repositories)
GitHub Packages는 인증이 필요하므로, `repositories` 블록에 GitHub 인증 정보를 포함해야 합니다.

```gradle
repositories {
    mavenCentral()
    maven {
        url = uri("https://maven.pkg.github.com/GeonDev/Infrastructure-Analyzer-Plugin")
        credentials {
            // GitHub Action 실행 시에는 자동으로 주입되지만, 
            // 로컬 빌드 시에는 환경 변수(GITHUB_ACTOR, GITHUB_TOKEN) 설정이 필요합니다.
            username = System.getenv("GITHUB_ACTOR") ?: "YOUR_GITHUB_USERNAME"
            password = System.getenv("GITHUB_TOKEN") ?: "YOUR_GITHUB_TOKEN(PAT)"
        }
    }
}
```

### 2.2 플러그인 및 의존성 추가
```gradle
plugins {
    id 'io.infracheck.infrastructure-analyzer' version '1.0.0-SNAPSHOT'
}

dependencies {
    implementation 'io.infracheck:infrastructure-analyzer:1.0.0-SNAPSHOT'
}
```

---

## 3. 애플리케이션 설정 (`application.yml`)

Spring Boot 애플리케이션의 설정 파일에 검증 옵션을 정의합니다.

```yaml
infrastructure:
  validation:
    domain: "company.com" # API 도메인 자동 보정용 (선택)
    verification:
      enabled: true        # 검증 활성화 여부
      fail-on-error: true  # 검증 실패 시 애플리케이션 기동 중단 여부
      timeout-seconds: 5   # API 응답 대기 시간
```

---

## 4. 주요 기능 및 동작 방식

### 4.1 빌드 타임: 의존성 추출
프로젝트 빌드 시(`./gradlew build`), 플러그인이 소스 코드를 스캔하여 인프라 요구사항 명세서를 생성합니다.
- **생성 경로:** `build/infrastructure/requirements-{profile}.json`
- **스캔 대상:**
    - `src/main/java` 내의 하드코딩된 파일 경로 및 URL
    - `application.yml` 내의 특정 패턴(파일 경로, API)

### 4.2 런타임: 인프라 검증
애플리케이션이 시작될 때, 생성된 명세서를 바탕으로 실제 환경을 점검합니다.
- **FileVerifier:** 지정된 경로에 파일이 실제로 존재하는지 확인.
- **DirectoryVerifier:** 디렉토리 존재 여부 확인.
- **ApiVerifier:** 외부 API 서버에 핑을 날려 응답이 오는지 확인.

---

## 5. 트러블슈팅

### 패키지를 찾을 수 없는 경우 (401 Unauthorized)
GitHub Packages는 비공개 패키지일 경우 반드시 유효한 **Personal Access Token(PAT)**이 필요합니다. 
1. GitHub Settings > Developer Settings > Personal Access Tokens (classic)에서 `read:packages` 권한이 있는 토큰을 생성하세요.
2. 환경 변수 `GITHUB_TOKEN`에 해당 값을 설정한 후 빌드하세요.

### 검증 실패로 앱이 뜨지 않는 경우
`fail-on-error: false`로 설정하면 검증에 실패하더라도 경고 로그만 남기고 애플리케이션은 정상적으로 기동됩니다. (개발 환경 권장)
