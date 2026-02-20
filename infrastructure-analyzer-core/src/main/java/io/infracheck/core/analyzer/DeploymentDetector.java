package io.infracheck.core.analyzer;

import io.infracheck.core.DeploymentType;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Set;

/**
 * 배포 환경(VM/K8s)을 자동 감지하는 로직
 * Gradle/Maven 플러그인 모두에서 사용 가능하도록 순수 Java로 구현
 */
public class DeploymentDetector {

    private static final String[] K8S_YAML_KEYWORDS = {
        "kubernetes.io", "k8s.", "mkube-proxy",
        "livenessstate", "readinessstate",
        "liveness-probe", "readiness-probe",
        "configmap", "config-map",
        "service-account", "serviceaccount"
    };

    // Gradle 플러그인 ID 기반 감지용 (Gradle 플러그인에서 호출 시 전달)
    private static final Set<String> K8S_PLUGIN_IDS = Set.of(
        "com.google.cloud.tools.jib",
        "org.springframework.boot.experimental.thin-launcher"
    );

    /**
     * 프로젝트 디렉토리와 적용된 플러그인 ID 목록으로 배포 환경을 감지합니다.
     *
     * @param projectDir 프로젝트 루트 디렉토리
     * @param appliedPluginIds 적용된 플러그인 ID 목록 (없으면 빈 Set)
     */
    public static DeploymentType detect(File projectDir, Set<String> appliedPluginIds) {
        // 1. K8s 플러그인 확인
        if (appliedPluginIds != null) {
            for (String pluginId : K8S_PLUGIN_IDS) {
                if (appliedPluginIds.contains(pluginId)) {
                    return DeploymentType.KUBERNETES;
                }
            }
        }

        // 2. application.yaml / application.yml 분석
        File appYaml = new File(projectDir, "src/main/resources/application.yaml");
        File appYml = new File(projectDir, "src/main/resources/application.yml");
        File configFile = appYaml.exists() ? appYaml : (appYml.exists() ? appYml : null);

        if (configFile != null) {
            try {
                String content = Files.readString(configFile.toPath());
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
        File k8sDir = new File(projectDir, "k8s");
        if (k8sDir.exists() && k8sDir.isDirectory()) {
            return DeploymentType.KUBERNETES;
        }

        // 4. 기본값: VM
        return DeploymentType.VM;
    }

    /**
     * 플러그인 정보 없이 디렉토리만으로 감지합니다. (Maven 플러그인 등에서 사용)
     */
    public static DeploymentType detect(File projectDir) {
        return detect(projectDir, null);
    }
}
