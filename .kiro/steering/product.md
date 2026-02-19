# 제품 개요

Infrastructure Analyzer는 Spring Boot 프로젝트의 배포 전 인프라 검증을 자동화하는 Gradle 플러그인입니다.

## 목적

배포 전에 필요한 인프라 리소스(파일, API, 쿠버네티스 리소스)가 준비되었는지 자동으로 검증하여 배포 실패를 사전에 방지합니다.

## 주요 기능

- 배포 환경 자동 감지 (VM/쿠버네티스)
- 다양한 설정 파일 지원 (application.yml, application.properties)
- 하이브리드 추출 전략 (명시적 선언 + 자동 패턴 매칭 + 소스코드 분석)
- 프로파일별 검증 (dev, stg, prod)
- 회사 도메인 우선 검증
- 검증 스크립트 자동 생성
- 소스코드 정적 분석을 통한 하드코딩 검출

## 대상 사용자

Bamboo CI/CD 파이프라인을 사용하여 VM 또는 쿠버네티스 환경에 Spring Boot 애플리케이션을 배포하는 개발팀 및 DevOps 팀

## 검증 대상

### VM 환경
- NAS 파일 (인증서, 키 파일)
- 외부 API (방화벽 접근 가능 여부)
- 디렉토리 권한 (읽기/쓰기/실행)

### 쿠버네티스 환경
- NAS 파일 (PVC 마운트)
- 외부 API (방화벽 접근 가능 여부)
- K8s 리소스 (ConfigMap, Secret, PVC)

## 검증 모드

- dev/stg: 경고만 표시 (STRICT_MODE=false)
- prod: 검증 실패 시 배포 차단 (STRICT_MODE=true)
