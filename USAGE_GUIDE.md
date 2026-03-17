# Infrastructure Analyzer 사용 가이드

이 가이드는 `Infrastructure-Analyzer`를 프로젝트에 적용하는 두 가지 방법(플러그인 방식 vs 라이브러리 방식)과 저장소 설정법을 안내합니다.

---

## 1. 저장소 설정 (Repository Configuration)

이 도구는 GitHub Packages 또는 Nexus와 같은 사설 저장소를 통해 배포됩니다. Gradle 빌드 시 플러그인과 라이브러리를 정상적으로 가져오기 위해 아래 설정을 추가해야 합니다.

### 1.1 GitHub Packages 사용 시
- **URL:** `https://maven.pkg.github.com/GeonDev/Infrastructure-Analyzer-Plugin`
- **인증:** GitHub 계정명(Actor)과 Personal Access Token(PAT)이 필요합니다.

### 1.2 Nexus 사용 시
- **URL:** `http://your-nexus-server/repository/maven-public/` (팀 내 설정에 따름)
- **인증:** Nexus 계정 정보가 필요합니다.

---

## 2. 프로젝트 적용 방법

### 방법 A: 플러그인 방식 (권장 ⭐)
빌드 시 소스 코드를 자동으로 스캔하여 인프라 명세(`requirements.json`)를 생성하고, **런타임 검증 라이브러리(Starter)까지 자동으로 의존성에 포함**합니다.

**settings.gradle:** (플러그인 탐색을 위해 필요)
```gradle
pluginManagement {
    repositories {
        maven {
            url = uri("https://maven.pkg.github.com/GeonDev/Infrastructure-Analyzer-Plugin")
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: "YOUR_USERNAME"
                password = System.getenv("GITHUB_TOKEN") ?: "YOUR_TOKEN"
            }
        }
        gradlePluginPortal()
    }
}
```

**build.gradle:**
```gradle
plugins {
    // 이 한 줄만 추가하면 빌드 타임 스캔 + 런타임 자동 검증이 모두 활성화됩니다.
    id 'io.infracheck.infrastructure-analyzer' version '1.0.0-SNAPSHOT'
}

repositories {
    maven {
        url = uri("https://maven.pkg.github.com/GeonDev/Infrastructure-Analyzer-Plugin")
        credentials {
            username = System.getenv("GITHUB_ACTOR")
            password = System.getenv("GITHUB_TOKEN")
        }
    }
    mavenCentral()
}

// 💡 별도의 dependencies { implementation '...' } 선언이 필요 없습니다!
```

### 방법 B: 라이브러리 방식
빌드 시 자동 스캔이 필요 없거나, 인프라 명세서를 수동으로 관리하고 싶을 때 사용합니다. (런타임 검증 기능만 사용)

**build.gradle:**
```gradle
dependencies {
    implementation 'io.infracheck:infrastructure-analyzer:1.0.0-SNAPSHOT'
}
```

---

## 3. 애플리케이션 설정 (`application.yml`)

```yaml
infrastructure:
  validation:
    domain: "company.com"
    verification:
      enabled: true
      fail-on-error: true # 검증 실패 시 기동 중단 여부
```

---

## 4. 플러그인 vs 라이브러리 차이점

| 구분 | 플러그인 방식 (`plugins { id '...' }`) | 라이브러리 방식 (`implementation '...'`) |
| :--- | :--- | :--- |
| **자동 분석** | 지원 (빌드 시 소스 코드 스캔) | 미지원 (JSON 수동 작성 필요) |
| **설정 편의성** | 높음 (자동 의존성 주입) | 보통 (직접 의존성 관리) |
| **사용 사례** | 인프라 요구사항이 계속 변하는 신규 프로젝트 | 인프라가 고정되어 있고 가벼운 검증만 필요한 경우 |
