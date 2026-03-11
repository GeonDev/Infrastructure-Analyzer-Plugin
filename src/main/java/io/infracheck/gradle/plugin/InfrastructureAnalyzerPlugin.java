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
    private static final String STARTER_DEPENDENCY = "io.infracheck:infrastructure-analyzer:1.0.0-SNAPSHOT";

    @Override
    public void apply(Project project) {
        // analyzeInfrastructure 태스크 등록
        project.getTasks().register("analyzeInfrastructure", InfrastructureAnalyzerTask.class, task -> {
            task.setGroup("infrastructure");
            task.setDescription("application.yml을 분석하여 인프라 검증 항목(requirements.json)을 생성합니다.");
        });

        // Java 플러그인이 있을 때만 의존성 주입 및 리소스 연동
        project.getPlugins().withType(JavaPlugin.class, javaPlugin -> {
            project.getDependencies().add("implementation", STARTER_DEPENDENCY);

            org.gradle.api.tasks.SourceSetContainer sourceSets = project.getExtensions().getByType(org.gradle.api.tasks.SourceSetContainer.class);
            org.gradle.api.tasks.SourceSet main = sourceSets.getByName(org.gradle.api.tasks.SourceSet.MAIN_SOURCE_SET_NAME);
            
            // 생성된 requirements.json 폴더를 리소스 디렉토리로 추가 (war, jar 모두 자동 포함됨)
            main.getResources().srcDir(new java.io.File(project.getLayout().getBuildDirectory().get().getAsFile(), "generated/resources"));

            // 리소스 복사 전(또는 JAR 생성 전)에 반드시 명세 생성이 먼저 실행되도록 의존성 설정
            project.getTasks().named(main.getProcessResourcesTaskName(), processResources -> {
                processResources.dependsOn("analyzeInfrastructure");
            });
        });
    }
}
