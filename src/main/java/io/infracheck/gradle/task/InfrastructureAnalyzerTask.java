package io.infracheck.gradle.task;

import io.infracheck.core.analyzer.InfrastructureExtractor;
import io.infracheck.core.model.Requirements;
import io.infracheck.core.util.ConfigParser;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;

import java.io.*;
import java.util.Map;

/**
 * 인프라 검증 항목을 분석하고 requirements.json을 생성하는 Gradle Task
 */
public class InfrastructureAnalyzerTask extends DefaultTask {

    private static final String[] PROFILES = {"local", "dev", "stage", "prod"};
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @TaskAction
    public void analyze() {
        // 0. mavenLocal 사용 경고
        checkMavenLocalUsage();

        // 1. (삭제됨) 환경 감지 불필요

        // 2. 설정 파일 확인
        File projectDir = getProject().getProjectDir();
        File yamlFile = new File(projectDir, "src/main/resources/application.yaml");
        File ymlFile = new File(projectDir, "src/main/resources/application.yml");
        File propsFile = new File(projectDir, "src/main/resources/application.properties");

        if (!yamlFile.exists() && !ymlFile.exists() && !propsFile.exists()) {
            getLogger().warn("⚠️  application.yaml, application.yml 또는 application.properties를 찾을 수 없습니다");
            return;
        }

        String configType = yamlFile.exists() ? "application.yaml"
                          : ymlFile.exists() ? "application.yml"
                          : "application.properties";
        getLogger().lifecycle("📄 설정 파일: {}", configType);

        File javaSourceDir = new File(projectDir, "src/main/java");
        if (javaSourceDir.exists()) {
            getLogger().lifecycle("🔍 소스코드 분석 활성화");
        }

        // 3. 프로파일별 requirements.json 생성
        File outputDir = new File(getProject().getLayout().getBuildDirectory().get().getAsFile(), "generated/resources/infrastructure");
        outputDir.mkdirs();

        for (String profile : PROFILES) {
            Map<String, Object> config = ConfigParser.parseWithProfile(projectDir, profile);

            if (config.isEmpty()) {
                getLogger().info("ℹ️  {} 프로파일 설정이 없습니다. 기본 설정만 사용합니다.", profile);
                config = ConfigParser.parseWithProfile(projectDir, null);
            }

            Requirements requirements = generateRequirements(profile, config, projectDir);

            // 파일명을 requirements-{profile}.json으로 통일하여 Starter와 호환성 확보
            String filename = "requirements-" + profile + ".json";

            File outputFile = new File(outputDir, filename);
            writeJson(requirements, outputFile);
            getLogger().lifecycle("✅ 생성됨: {}", filename);
        }
    }

    private Requirements generateRequirements(String profile, Map<String, Object> config, File projectDir) {
        InfrastructureExtractor extractor = new InfrastructureExtractor(config, projectDir);

        Requirements req = new Requirements();
        req.setProject(getProject().getName());
        req.setEnvironment(profile);

        Requirements.Infrastructure infra = req.getInfrastructure();
        infra.setCompany_domain(extractor.getCompanyDomain());
        infra.setFiles(extractor.extractFiles());
        infra.setExternal_apis(extractor.extractApis());
        infra.setDirectories(extractor.extractDirectories());

        return req;
    }

    private void writeJson(Requirements requirements, File outputFile) {
        try (FileWriter writer = new FileWriter(outputFile)) {
            GSON.toJson(requirements, writer);
        } catch (IOException e) {
            getLogger().error("❌ JSON 파일 생성 실패: {}", outputFile.getPath(), e);
        }
    }

    private void checkMavenLocalUsage() {
        File settingsFile = new File(getProject().getRootDir(), "settings.gradle");
        if (!settingsFile.exists()) {
            settingsFile = new File(getProject().getRootDir(), "settings.gradle.kts");
        }

        if (settingsFile.exists()) {
            try {
                String content = new String(java.nio.file.Files.readAllBytes(settingsFile.toPath()));
                if (content.contains("mavenLocal()")) {
                    boolean isCI = isCIEnvironment();
                    if (isCI) {
                        getLogger().error("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                        getLogger().error("❌ CRITICAL: settings.gradle에 mavenLocal()이 설정되어 있습니다!");
                        getLogger().error("❌ CI/CD 환경에서는 mavenLocal() 사용이 금지됩니다.");
                        getLogger().error("❌ 운영 배포 시 Nexus 저장소만 사용해야 합니다.");
                        getLogger().error("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                    } else {
                        getLogger().warn("⚠️  INFO: settings.gradle에 mavenLocal()이 설정되어 있습니다.");
                        getLogger().warn("⚠️  로컬 테스트 중이라면 정상입니다. 운영 배포 전에는 반드시 제거하세요.");
                    }
                }
            } catch (Exception e) {
                // 파일 읽기 실패 시 무시
            }
        }
    }

    private boolean isCIEnvironment() {
        String[] ciEnvVars = {
            "CI", "BAMBOO_BUILD_NUMBER", "JENKINS_HOME",
            "GITLAB_CI", "GITHUB_ACTIONS", "CIRCLECI", "TRAVIS"
        };
        for (String envVar : ciEnvVars) {
            if (System.getenv(envVar) != null) return true;
        }
        return false;
    }
}
