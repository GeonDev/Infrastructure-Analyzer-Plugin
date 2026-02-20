package io.infracheck.maven;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.infracheck.core.DeploymentType;
import io.infracheck.core.analyzer.DeploymentDetector;
import io.infracheck.core.analyzer.InfrastructureExtractor;
import io.infracheck.core.model.Requirements;
import io.infracheck.core.util.ConfigParser;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;

/**
 * Maven 프로젝트용 인프라 검증 항목 분석 Mojo
 * mvn io.infracheck:infrastructure-analyzer-maven-plugin:analyze
 */
@Mojo(name = "analyze", defaultPhase = LifecyclePhase.PACKAGE)
public class InfrastructureAnalyzerMojo extends AbstractMojo {

    private static final String[] DEFAULT_PROFILES = {"dev", "stage", "prod"};
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** 프로젝트 루트 디렉토리 */
    @Parameter(defaultValue = "${project.basedir}", readonly = true)
    private File projectDir;

    /** 프로젝트 이름 */
    @Parameter(defaultValue = "${project.artifactId}", readonly = true)
    private String projectName;

    /** 빌드 출력 디렉토리 */
    @Parameter(defaultValue = "${project.build.directory}", readonly = true)
    private File buildDir;

    /**
     * application.yml이 위치한 디렉토리 (기본값: projectDir/src/main/resources)
     * 멀티모듈에서 공유 설정 파일이 다른 모듈에 있을 때 사용
     * 예: <configDir>${project.basedir}/../service-global/src/main/resources</configDir>
     */
    @Parameter
    private File configDir;

    /**
     * 분석할 프로파일 목록 (기본값: dev, stage, prod)
     * 예: <profiles><profile>dev</profile><profile>stage</profile><profile>prod</profile></profiles>
     */
    @Parameter
    private String[] profiles;

    @Override
    public void execute() throws MojoExecutionException {
        // 실제 사용할 프로파일 목록 결정
        String[] activeProfiles = (profiles != null && profiles.length > 0) ? profiles : DEFAULT_PROFILES;

        // 설정 파일 디렉토리 결정 (configDir 우선, 없으면 projectDir 기준)
        File effectiveConfigDir = (configDir != null) ? configDir : projectDir;

        // 1. 환경 감지
        DeploymentType deploymentType = DeploymentDetector.detect(projectDir);
        getLog().info("✅ 감지된 배포 환경: " + deploymentType);

        // 2. 설정 파일 확인
        File yamlFile = new File(effectiveConfigDir, "src/main/resources/application.yaml");
        File ymlFile = new File(effectiveConfigDir, "src/main/resources/application.yml");
        File propsFile = new File(effectiveConfigDir, "src/main/resources/application.properties");

        // configDir이 이미 resources 디렉토리인 경우도 처리
        if (!yamlFile.exists() && !ymlFile.exists() && !propsFile.exists()) {
            yamlFile = new File(effectiveConfigDir, "application.yaml");
            ymlFile = new File(effectiveConfigDir, "application.yml");
            propsFile = new File(effectiveConfigDir, "application.properties");
        }

        if (!yamlFile.exists() && !ymlFile.exists() && !propsFile.exists()) {
            getLog().warn("⚠️  application.yaml, application.yml 또는 application.properties를 찾을 수 없습니다");
            getLog().warn("⚠️  탐색 경로: " + effectiveConfigDir.getAbsolutePath());
            return;
        }

        String configType = yamlFile.exists() ? yamlFile.getAbsolutePath()
                          : ymlFile.exists() ? ymlFile.getAbsolutePath()
                          : propsFile.getAbsolutePath();
        getLog().info("📄 설정 파일: " + configType);

        // 3. 출력 디렉토리 생성
        File outputDir = new File(buildDir, "infrastructure");
        outputDir.mkdirs();

        // 4. 프로파일별 requirements.json 생성
        for (String profile : activeProfiles) {
            Map<String, Object> config = ConfigParser.parseWithProfile(effectiveConfigDir, profile);

            if (config.isEmpty()) {
                getLog().info("ℹ️  " + profile + " 프로파일 설정이 없습니다. 기본 설정만 사용합니다.");
                config = ConfigParser.parseWithProfile(effectiveConfigDir, null);
            }

            Requirements requirements;
            if (deploymentType == DeploymentType.KUBERNETES) {
                requirements = generateK8sRequirements(profile, config);
            } else {
                requirements = generateVmRequirements(profile, config);
            }

            String filename = (deploymentType == DeploymentType.KUBERNETES)
                ? "requirements-k8s-" + profile + ".json"
                : "requirements-" + profile + ".json";

            File outputFile = new File(outputDir, filename);
            writeJson(requirements, outputFile);
            getLog().info("✅ 생성됨: " + filename);
        }

        // 5. 검증 스크립트 복사
        copyValidationScript(deploymentType, outputDir);
    }

    private Requirements generateVmRequirements(String profile, Map<String, Object> config) {
        InfrastructureExtractor extractor = new InfrastructureExtractor(config, projectDir);

        Requirements req = new Requirements();
        req.setProject(projectName);
        req.setEnvironment(profile);
        req.setPlatform("vm");

        Requirements.Infrastructure infra = req.getInfrastructure();
        infra.setCompany_domain(extractor.getCompanyDomain());
        infra.setFiles(extractor.extractFiles());
        infra.setExternal_apis(extractor.extractApis());
        infra.setDirectories(extractor.extractDirectories());

        return req;
    }

    private Requirements generateK8sRequirements(String profile, Map<String, Object> config) {
        InfrastructureExtractor extractor = new InfrastructureExtractor(config, projectDir);

        Requirements req = new Requirements();
        req.setProject(projectName);
        req.setEnvironment(profile);
        req.setPlatform("kubernetes");

        Requirements.Infrastructure infra = req.getInfrastructure();
        infra.setCompany_domain(extractor.getCompanyDomain());
        infra.setNamespace(InfrastructureExtractor.determineNamespace(profile));
        infra.setFiles(extractor.extractFiles());
        infra.setExternal_apis(extractor.extractApis());
        infra.setConfigmaps(extractor.extractConfigMaps());
        infra.setSecrets(extractor.extractSecrets());
        infra.setPvcs(extractor.extractPvcs());

        return req;
    }

    private void writeJson(Requirements requirements, File outputFile) throws MojoExecutionException {
        try (FileWriter writer = new FileWriter(outputFile)) {
            GSON.toJson(requirements, writer);
        } catch (IOException e) {
            throw new MojoExecutionException("❌ JSON 파일 생성 실패: " + outputFile.getPath(), e);
        }
    }

    private void copyValidationScript(DeploymentType deploymentType, File outputDir) {
        String scriptName = (deploymentType == DeploymentType.KUBERNETES)
            ? "validate-k8s-infrastructure.sh"
            : "validate-infrastructure.sh";

        File targetFile = new File(outputDir, scriptName);

        try (InputStream is = getClass().getResourceAsStream("/" + scriptName)) {
            if (is == null) {
                getLog().warn("⚠️  리소스에서 스크립트를 찾을 수 없습니다: " + scriptName);
                return;
            }
            Files.copy(is, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            targetFile.setExecutable(true);
            getLog().info("✅ 생성됨: target/infrastructure/" + scriptName);
        } catch (IOException e) {
            getLog().warn("⚠️  스크립트 복사 실패: " + scriptName);
        }
    }
}
