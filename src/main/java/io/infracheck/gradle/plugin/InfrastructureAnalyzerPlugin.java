package io.infracheck.gradle.plugin;

import io.infracheck.gradle.task.InfrastructureAnalyzerTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;

/**
 * 배포 전 인프라 검증 및 런타임 검증을 위한 Gradle 플러그인
 * 
 * 기능:
 * - Runtime Verification Starter 의존성 자동 주입
 * - 배포 환경 자동 감지 (VM/K8s)
 * - application.yml 분석하여 인프라 검증 항목(requirements.json) 생성
 */
public class InfrastructureAnalyzerPlugin implements Plugin<Project> {

    // 자기 자신(통합된 JAR)을 의존성으로 주입
    private static final String STARTER_DEPENDENCY = "io.infracheck:infrastructure-analyzer-plugin:1.0.2";

    @Override
    public void apply(Project project) {
        // 1. Java 플러그인이 있을 때만 Starter 의존성 자동 주입
        project.getPlugins().withType(JavaPlugin.class, javaPlugin -> {
            project.getDependencies().add("implementation", STARTER_DEPENDENCY);
        });

        // 2. analyzeInfrastructure 태스크 등록
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
