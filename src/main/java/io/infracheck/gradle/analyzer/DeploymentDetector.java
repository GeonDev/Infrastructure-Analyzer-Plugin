package io.infracheck.gradle.analyzer;

import io.infracheck.core.DeploymentType;
import org.gradle.api.Project;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Gradle 플러그인용 DeploymentDetector 래퍼
 * core 모듈의 DeploymentDetector에 Gradle Project 정보를 전달합니다.
 */
public class DeploymentDetector {

    public static DeploymentType detect(Project project) {
        // 적용된 플러그인 ID 목록 수집
        Set<String> appliedPluginIds = project.getPlugins().stream()
            .map(plugin -> plugin.getClass().getName())
            .collect(Collectors.toSet());

        // Gradle 플러그인은 ID로 확인
        Set<String> pluginIds = Set.of(
            "com.google.cloud.tools.jib",
            "org.springframework.boot.experimental.thin-launcher"
        );

        for (String pluginId : pluginIds) {
            if (project.getPlugins().hasPlugin(pluginId)) {
                appliedPluginIds = Set.of(pluginId);
                break;
            }
        }

        return io.infracheck.core.analyzer.DeploymentDetector.detect(
            project.getProjectDir(), appliedPluginIds
        );
    }
}
