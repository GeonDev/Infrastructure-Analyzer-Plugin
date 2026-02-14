package io.infracheck.gradle.analyzer;

import io.infracheck.gradle.DeploymentType;
import org.gradle.api.Project;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * 배포 환경(VM/K8s)을 자동 감지하는 로직
 */
public class DeploymentDetector {

    // 쿠버네티스 관련 키워드
    private static final String[] K8S_YAML_KEYWORDS = {
        "kubernetes.io",
        "k8s.",
        "mkube-proxy",
        "livenessstate",
        "readinessstate",
        "liveness-probe",
        "readiness-probe"
    };

    // 쿠버네티스 관련 Gradle 플러그인
    private static final String[] K8S_PLUGINS = {
        "com.google.cloud.tools.jib",
        "org.springframework.boot.experimental.thin-launcher"
    };

    /**
     * 프로젝트의 배포 환경을 자동 감지합니다.
     *
     * 감지 순서:
     * 1. build.gradle에 K8s 관련 플러그인 확인
     * 2. application.yml에 K8s 관련 키워드 확인
     * 3. k8s 디렉토리 존재 확인
     * 4. 기본값: VM
     */
    public static DeploymentType detect(Project project) {
        // 1. K8s 플러그인 확인
        for (String pluginId : K8S_PLUGINS) {
            if (project.getPlugins().hasPlugin(pluginId)) {
                return DeploymentType.KUBERNETES;
            }
        }

        // 2. application.yml 분석
        File appYml = new File(project.getProjectDir(),
            "src/main/resources/application.yml");
        if (appYml.exists()) {
            try {
                String content = Files.readString(appYml.toPath());
                String lowerContent = content.toLowerCase();

                for (String keyword : K8S_YAML_KEYWORDS) {
                    if (lowerContent.contains(keyword.toLowerCase())) {
                        return DeploymentType.KUBERNETES;
                    }
                }
            } catch (IOException e) {
                // 파일 읽기 실패 시 무시
            }
        }

        // 3. k8s 디렉토리 확인
        File k8sDir = new File(project.getProjectDir(), "k8s");
        if (k8sDir.exists() && k8sDir.isDirectory()) {
            return DeploymentType.KUBERNETES;
        }

        // 4. 기본값: VM
        return DeploymentType.VM;
    }
}
