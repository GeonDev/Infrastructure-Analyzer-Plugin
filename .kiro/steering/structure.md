# 프로젝트 구조

## 루트 레이아웃

```
infrastructure-analyzer-plugin/
├── build.gradle              # 플러그인 빌드 설정
├── src/main/
│   ├── java/                 # Java 소스 코드
│   └── resources/            # 검증 쉘 스크립트
├── docs/                     # 문서
└── build/                    # 생성된 산출물
```

## 소스 코드 구조

### 패키지 구조

```
io.infracheck.gradle/
├── InfrastructureAnalyzerPlugin.java    # 메인 플러그인 진입점
├── InfrastructureAnalyzerTask.java      # 핵심 분석 태스크
├── DeploymentType.java                  # 환경 타입 enum
├── analyzer/
│   ├── DeploymentDetector.java          # VM/K8s 자동 감지
│   ├── InfrastructureExtractor.java     # 검증 항목 추출
│   └── SourceCodeAnalyzer.java          # 소스코드 정적 분석
├── model/
│   ├── Requirements.java                # 메인 요구사항 모델
│   ├── FileCheck.java                   # 파일 검증 스펙
│   ├── ApiCheck.java                    # API 검증 스펙
│   ├── DirectoryCheck.java              # 디렉토리 권한 스펙 (VM 전용)
│   ├── ConfigMapCheck.java              # K8s ConfigMap 스펙
│   ├── SecretCheck.java                 # K8s Secret 스펙
│   └── PvcCheck.java                    # K8s PVC 스펙
└── util/
    ├── ConfigParser.java                # 설정 파일 자동 감지 (YAML/Properties)
    ├── YamlParser.java                  # 프로파일 기반 YAML 파싱
    └── PatternMatcher.java              # 패턴 매칭 유틸리티
```

## 주요 컴포넌트

### 플러그인 진입점
- `InfrastructureAnalyzerPlugin`: `analyzeInfrastructure` 태스크를 등록하고 빌드 라이프사이클에 연결

### 태스크 실행
- `InfrastructureAnalyzerTask`: 환경 감지, YAML 파싱, 요구사항 생성을 조율하는 메인 태스크

### 환경 감지
- `DeploymentDetector`: 프로젝트를 분석하여 배포 대상이 VM인지 쿠버네티스인지 판단

### 추출 로직
- `InfrastructureExtractor`: 하이브리드 추출 전략 구현 (명시적 선언 + 자동 패턴 매칭)

### 데이터 모델
- `Requirements`: 모든 검증 스펙을 포함하는 루트 모델
- Check 모델들: 검증 항목의 타입 안전한 표현
  - `FileCheck`: 파일 존재 검증
  - `ApiCheck`: 외부 API 접근 검증
  - `DirectoryCheck`: 디렉토리 권한 검증 (VM 전용)
  - `ConfigMapCheck`, `SecretCheck`, `PvcCheck`: K8s 리소스 검증

### 유틸리티
- `ConfigParser`: YAML 및 Properties 파일 자동 감지 및 파싱
- `YamlParser`: Spring Boot 프로파일 기반 YAML 파싱 처리
- `PatternMatcher`: 파일 경로 및 URL 패턴 매칭

## 리소스

```
src/main/resources/
├── validate-infrastructure.sh       # VM 검증 스크립트
└── validate-k8s-infrastructure.sh   # 쿠버네티스 검증 스크립트
```

이 스크립트들은 빌드 시 `build/infrastructure/` 디렉토리로 복사됩니다.

## 생성되는 출력 구조

모든 산출물이 `build/infrastructure/` 디렉토리에 생성되어 Bamboo Artifact 설정이 간단합니다:

```
build/infrastructure/
├── requirements-dev.json                    # 개발 환경 스펙
├── requirements-stg.json                    # 스테이징 환경 스펙
├── requirements-prod.json                   # 운영 환경 스펙
└── validate-infrastructure.sh               # 검증 스크립트
```

쿠버네티스 프로젝트의 경우:

```
build/infrastructure/
├── requirements-k8s-dev.json
├── requirements-k8s-stg.json
├── requirements-k8s-prod.json
└── validate-k8s-infrastructure.sh
```

## 코드 컨벤션

- Java 17 언어 기능 사용
- 프로젝트 분석을 위한 Gradle API 활용
- Gradle 로거를 사용한 라이프사이클 로깅
- Fail-safe 접근: 가능한 경우 에러 대신 경고 사용
- 프로파일 기반 설정 지원 (dev, stg, prod)
- 소스코드 정적 분석을 통한 하드코딩 검출
