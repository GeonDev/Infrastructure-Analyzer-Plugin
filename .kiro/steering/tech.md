# 기술 스택

## 빌드 시스템

- Gradle 8.x
- Java 17 (소스 및 타겟 호환성)
- Gradle Plugin Development Plugin

## 핵심 의존성

- gson 2.10.1 - JSON 직렬화
- snakeyaml 2.2 - YAML 파싱
- javaparser-core 3.25.8 - 소스코드 정적 분석

## 플러그인 설정

- Plugin ID: `io.infracheck.infrastructure-analyzer`
- Group: `io.infracheck`
- Version: 1.0.0
- Implementation Class: `io.infracheck.gradle.InfrastructureAnalyzerPlugin`

## 주요 명령어

### 로컬 개발

```bash
# 로컬 테스트를 위해 Maven Local에 빌드 및 배포
./gradlew clean publishToMavenLocal

# 클린 빌드
./gradlew clean build

# 캐시된 플러그인 버전 삭제
rm -rf ~/.gradle/caches/modules-2/files-2.1/io.infracheck/infrastructure-analyzer-plugin
./gradlew clean build --refresh-dependencies
```

### 배포

```bash
# Nexus에 배포 (인증 정보 필요)
export NEXUS_USERNAME="your-username"
export NEXUS_PASSWORD="your-password"
./gradlew publish
```

### 소비자 프로젝트에서 테스트

```bash
# 플러그인 변경 후 재빌드 및 테스트
cd infrastructure-analyzer-plugin
./gradlew clean publishToMavenLocal
cd ../test-project
./gradlew clean build
```

## mavenLocal() 사용 관리

### 자동 감지 및 경고

플러그인이 빌드 시 자동으로 `settings.gradle`의 `mavenLocal()` 사용을 감지합니다:

- **로컬 환경**: 부드러운 INFO 경고 (개발 중에는 정상)
- **CI/CD 환경**: 강력한 ERROR 경고 (Bamboo, Jenkins, GitLab CI, GitHub Actions 등 자동 감지)

### 감지되는 CI 환경

- Bamboo (`BAMBOO_BUILD_NUMBER`)
- Jenkins (`JENKINS_HOME`)
- GitLab CI (`GITLAB_CI`)
- GitHub Actions (`GITHUB_ACTIONS`)
- CircleCI (`CIRCLECI`)
- Travis CI (`TRAVIS`)

### 사용 시나리오

1. **로컬 테스트**: `mavenLocal()` 포함 → INFO 경고만 표시
2. **CI/CD 빌드**: `mavenLocal()` 포함 → ERROR 경고 표시 (배포 차단 권장)
3. **운영 배포**: `mavenLocal()` 제거 → 경고 없음

## 배포 설정

- 로컬: 테스트용 `mavenLocal()`
- 원격: Nexus 저장소 `https://nexus.company.com/repository/maven-releases/`
- 인증: 환경 변수 `NEXUS_USERNAME` 및 `NEXUS_PASSWORD`

## 생성되는 산출물

모든 산출물이 `build/infrastructure/` 디렉토리에 생성됩니다:

- `build/infrastructure/requirements-{profile}.json` - VM 검증 스펙
- `build/infrastructure/requirements-k8s-{profile}.json` - 쿠버네티스 검증 스펙
- `build/infrastructure/validate-infrastructure.sh` - VM 검증 스크립트
- `build/infrastructure/validate-k8s-infrastructure.sh` - 쿠버네티스 검증 스크립트

Bamboo Artifact 패턴: `build/infrastructure/**`
