package io.infracheck.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

/**
 * 배포 전 인프라 검증 Gradle 플러그인
 *
 * 기능:
 * - 배포 환경 자동 감지 (VM/K8s)
 * - application.yml 분석하여 검증 항목 추출
 * - requirements.json 생성 (dev, stg, prod)
 * - 검증 스크립트 자동 복사 (최초 1회)
 *
 * 사용법:
 *   plugins {
 *       id 'io.infracheck.infrastructure-analyzer' version '1.0.0'
 *   }
 */
public class InfrastructureAnalyzerPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        // analyzeInfrastructure 태스크 등록
        project.getTasks().register("analyzeInfrastructure", InfrastructureAnalyzerTask.class, task -> {
            task.setGroup("infrastructure");
            task.setDescription("application.yml을 분석하여 인프라 검증 항목(requirements.json)을 생성합니다.");
        });

        // build 태스크에 자동 연결
        project.afterEvaluate(p -> {
            p.getTasks().named("build", buildTask -> {
                buildTask.dependsOn("analyzeInfrastructure");
            });
        });
    }
}
