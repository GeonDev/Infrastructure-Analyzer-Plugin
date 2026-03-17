# 로컬 테스트 가이드 (통합 버전)

이 가이드는 로컬 개발 환경에서 패키지를 빌드하고, 원격 저장소(GitHub/Nexus)에 배포하여 최종적으로 테스트 프로젝트에 적용하는 전 과정을 안내합니다.

## 1단계: 로컬 테스트 (Maven Local)

가장 빠르게 수정 사항을 확인하는 방법입니다.

```bash
# 1. 로컬 저장소(~/.m2/)에 설치
./gradlew clean publishToMavenLocal

# 2. 테스트 프로젝트의 settings.gradle 설정
pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
    }
}
```

---

## 2단계: 원격 저장소 배포 (Remote Publish)

로컬 테스트가 끝났다면 실제 환경(GitHub Packages 또는 Nexus)에 배포합니다.

### 2.1 GitHub Packages에 배포
- **배포:** `main` 브랜치에 푸시하면 GitHub Actions가 자동으로 수행합니다.
- **수동 배포:**
```bash
export GITHUB_ACTOR="GeonDev"
export GITHUB_TOKEN="your_personal_access_token"
./gradlew publish
```

### 2.2 Nexus에 배포 (필요 시 build.gradle 수정)
Nexus 주소와 계정 정보를 설정하고 배포합니다.
```bash
export NEXUS_USERNAME="admin"
export NEXUS_PASSWORD="password"
./gradlew publish -Pgpr.user=$NEXUS_USERNAME -Pgpr.key=$NEXUS_PASSWORD
```

---

## 3단계: 원격 저장소 활용 테스트 (Remote Repo Test)

배포된 패키지를 실제 서비스 프로젝트에서 가져오는지 테스트합니다.

### 3.1 GitHub Packages 기반 테스트
**settings.gradle:**
```gradle
pluginManagement {
    repositories {
        maven {
            url = uri("https://maven.pkg.github.com/GeonDev/Infrastructure-Analyzer-Plugin")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
        gradlePluginPortal()
    }
}
```

### 3.2 Nexus 기반 테스트
**settings.gradle:**
```gradle
pluginManagement {
    repositories {
        maven {
            url = uri("http://your-nexus-server/repository/maven-public/")
            credentials {
                username = "nexus_user"
                password = "nexus_password"
            }
        }
        gradlePluginPortal()
    }
}
```

---

## 💡 주요 팁

- **버전 관리:** 테스트 중에는 `1.0.0-SNAPSHOT` 버전을 사용하여 캐시 문제를 피하세요.
- **인증 오류:** GitHub Packages는 `read:packages` 권한이 있는 PAT가 필요하며, Nexus는 해당 레포지토리에 대한 읽기 권한이 필요합니다.
- **플러그인 적용:** `plugins { id 'io.infracheck.infrastructure-analyzer' version '1.0.0-SNAPSHOT' }` 선언만으로 모든 인프라 체크가 준비됩니다.
