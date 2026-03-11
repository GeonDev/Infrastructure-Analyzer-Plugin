# 배포 전 인프라 검증 시스템 - AI 실행계획

## 📋 프로젝트 개요

VM/물리 서버와 쿠버네티스 환경을 모두 지원하는 배포 전 인프라 자동 검증 시스템을 구축합니다.

### 핵심 목표
- 배포 전에 각 환경(dev, stg, prod) 서버가 애플리케이션 실행 준비가 되었는지 자동 검증
- 검증 실패 시 배포 차단하여 운영 장애 사전 방지
- 단일 Gradle 플러그인으로 VM과 쿠버네티스 환경 모두 지원

### 검증 대상
1. **NAS 파일**: 인증서, 키 파일 등 존재 여부
2. **외부 API**: 방화벽 설정으로 접근 가능 여부
3. **쿠버네티스 리소스**: ConfigMap, Secret, PVC 존재 여부 (K8s 환경만)

### 환경별 검증 모드
- **dev/stg**: 경고만 표시 (STRICT_MODE=false)
- **prod**: 검증 실패 시 배포 차단 (STRICT_MODE=true)

### 회사 도메인 우선 검증
- **회사 도메인** (*.abc.co.kr): critical=true (배포 차단)
- **외부 도메인** (inicis.com 등): critical=false (경고만)

---

## 🏗️ 시스템 아키텍처

```
[개발자 PC]
  ↓ git push
[Bitbucket]
  ↓ webhook
[Bamboo - Build Plan]
  ├─ Checkout
  ├─ Gradle Build
  │   └─ 플러그인이 환경 자동 감지
  │       ├─ VM → requirements-{profile}.json + validate-infrastructure.sh
  │       └─ K8s → requirements-k8s-{profile}.json + validate-k8s-infrastructure.sh
  └─ Artifact Definition
[Bamboo - Deployment Plan]
  ├─ Artifact Download
  ├─ Infrastructure Validation
  │   └─ 환경에 맞는 스크립트 실행
  │       ├─ VM: SSH로 서버 검증
  │       └─ K8s: kubectl로 클러스터 검증
  └─ Deploy (검증 통과 시에만)
```

---

## 📦 Phase 1: Gradle 플러그인 개발

### 1.1 프로젝트 구조


```
infrastructure-analyzer/
├── build.gradle
├── settings.gradle
└── src/main/
    ├── java/com/company/gradle/
    │   ├── InfrastructureAnalyzerPlugin.java
    │   ├── InfrastructureAnalyzerTask.java
    │   ├── DeploymentType.java
    │   └── validators/
    │       ├── VmValidator.java
    │       └── K8sValidator.java
    └── resources/
        ├── validate-infrastructure.sh       # VM용
        └── validate-k8s-infrastructure.sh   # K8s용
```

### 1.2 환경 자동 감지 로직

플러그인은 다음 기준으로 배포 환경을 자동 감지합니다:

**쿠버네티스 감지 조건 (하나라도 해당하면 K8s):**
1. build.gradle에 쿠버네티스 플러그인 존재 (jib, thin-launcher)
2. application.yml에 쿠버네티스 관련 키워드
   - `kubernetes.io`, `k8s.`, `mkube-proxy`
   - `livenessstate`, `readinessstate`
3. k8s 디렉토리 존재

**기본값:** VM/물리 서버

### 1.3 핵심 코드 구조

#### InfrastructureAnalyzerPlugin.java

```java
public class InfrastructureAnalyzerPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getTasks().named("build", task -> {
            task.doFirst(t -> {
                // 1. 환경 감지
                DeploymentType type = detectDeploymentType(project);
                
                // 2. requirements.json 생성 (dev, stg, prod)
                generateRequirementsJson(project, type);
                
                // 3. 검증 스크립트 복사 (최초 1회만)
                copyValidationScript(project, type);
            });
        });
    }
    
    private DeploymentType detectDeploymentType(Project project) {
        // 쿠버네티스 플러그인 확인
        if (hasKubernetesPlugin(project)) return KUBERNETES;
        
        // application.yml 분석
        File appYml = new File(project.getProjectDir(), 
            "src/main/resources/application.yml");
        if (appYml.exists()) {
            String content = readFile(appYml);
            if (content.contains("kubernetes") || 
                content.contains("mkube-proxy") ||
                content.contains("livenessstate")) {
                return KUBERNETES;
            }
        }
        
        // k8s 디렉토리 확인
        if (new File(project.getProjectDir(), "k8s").exists()) {
            return KUBERNETES;
        }
        
        return VM;
    }
}
```

#### InfrastructureAnalyzerTask.java

```java
public class InfrastructureAnalyzerTask {
    
    public void generateRequirementsJson(Project project, DeploymentType type) {
        String[] profiles = {"dev", "stg", "prod"};
        
        for (String profile : profiles) {
            Map<String, Object> config = parseYamlWithProfile(profile);
            
            if (type == KUBERNETES) {
                generateK8sRequirements(project, profile, config);
            } else {
                generateVmRequirements(project, profile, config);
            }
        }
    }
    
    private void generateVmRequirements(Project project, String profile, 
                                        Map<String, Object> config) {
        Requirements req = new Requirements();
        req.setVersion("1.0");
        req.setProject(project.getName());
        req.setEnvironment(profile);
        req.setPlatform("vm");
        
        // 1. NAS 파일 추출
        List<FileCheck> files = extractNasFiles(config);
        req.setFiles(files);
        
        // 2. 외부 API 추출
        List<ApiCheck> apis = extractExternalApis(config, profile);
        req.setExternalApis(apis);
        
        // 3. JSON 파일 생성
        writeJson(req, "requirements-" + profile + ".json");
    }
    
    private void generateK8sRequirements(Project project, String profile,
                                         Map<String, Object> config) {
        Requirements req = new Requirements();
        req.setVersion("1.0");
        req.setProject(project.getName());
        req.setEnvironment(profile);
        req.setPlatform("kubernetes");
        req.setNamespace(determineNamespace(profile));
        
        // 1. ConfigMap 추출
        req.setConfigMaps(extractConfigMaps(config));
        
        // 2. Secret 추출
        req.setSecrets(extractSecrets(config));
        
        // 3. PVC 추출
        req.setPvcs(extractPvcs(config));
        
        // 4. 외부 API 추출
        req.setExternalApis(extractExternalApis(config, profile));
        
        // 5. JSON 파일 생성
        writeJson(req, "requirements-k8s-" + profile + ".json");
    }
}
```

### 1.4 YAML 파싱 및 항목 추출 전략

#### 추출 전략: 하이브리드 방식 (자동 추출 + 명시적 선언)

**Phase 1: 명시적 선언 우선** (정확성)
- 개발자가 `infrastructure.validation` 섹션에 명시적으로 선언한 항목 사용
- 가장 정확하고 프로젝트별 커스터마이징 가능

**Phase 2: 자동 추출 (Fallback)** (범용성)
- 명시적 선언이 없으면 패턴 기반 자동 추출
- 모든 프로젝트에서 동작하도록 범용적인 패턴 사용

#### application.yml 명시적 선언 예시

```yaml
# application.yml
infrastructure:
  validation:
    domain: "abc.co.kr"  # 회사 도메인 설정
    
    # 명시적으로 검증할 파일 선언 (선택)
    files:
      - path: "${cdn.key.path}"
        critical: true
        description: "CDN 서명 키"
      - path: "/nas2/was/cert/payment.pem"
        critical: true
        description: "결제 인증서"
    
    # 명시적으로 검증할 API 선언 (선택)
    apis:
      - url: "${external.api.url}"
        critical: true
        description: "외부 API"
      - url: "https://payment.inicis.com"
        critical: false
        description: "결제 시스템 (외부)"
    
    # 자동 추출 제외 패턴 (선택)
    exclude-patterns:
      - "localhost"
      - "127.0.0.1"
      - "*.local"

# 실제 프로젝트 설정
cdn:
  key:
    path: "/nas2/was/key/cdn/signed.der"

external:
  api:
    url: "https://api.company.com"
```

#### NAS 파일 추출 (하이브리드)

```java
private List<FileCheck> extractNasFiles(Map<String, Object> config) {
    List<FileCheck> files = new ArrayList<>();
    
    // 1. 명시적 선언 우선
    List<Map<String, Object>> explicitFiles = 
        getNestedValue(config, "infrastructure.validation.files");
    
    if (explicitFiles != null && !explicitFiles.isEmpty()) {
        for (Map<String, Object> item : explicitFiles) {
            String path = resolveValue((String) item.get("path"), config);
            Boolean critical = (Boolean) item.getOrDefault("critical", true);
            String description = (String) item.get("description");
            
            files.add(new FileCheck(path, detectLocation(path), critical, description));
        }
        return files;
    }
    
    // 2. 자동 추출 (Fallback)
    List<String> excludePatterns = getNestedValue(config, 
        "infrastructure.validation.exclude-patterns");
    
    findAllValues(config, "", (key, value) -> {
        if (value instanceof String) {
            String path = (String) value;
            
            // 제외 패턴 확인
            if (shouldExclude(path, excludePatterns)) {
                return;
            }
            
            // 파일 경로 패턴 매칭
            if (isFilePath(path)) {
                files.add(new FileCheck(path, detectLocation(path), true, key));
            }
        }
    });
    
    return files;
}

// 파일 경로 패턴 감지
private boolean isFilePath(String value) {
    // 1. 확장자 기반 감지 (인증서, 키 파일 등)
    if (value.matches("^/[a-zA-Z0-9/_.-]+\\.(der|pem|p8|p12|cer|crt|key|json|jks|keystore)$")) {
        return true;
    }
    
    // 2. 경로 기반 감지 (NAS, 마운트 포인트)
    if (value.matches("^/(nas|mnt|home|var|opt)/[a-zA-Z0-9/_-]+$")) {
        return true;
    }
    
    return false;
}

// 파일 위치 감지
private String detectLocation(String path) {
    if (path.startsWith("/nas") || path.startsWith("/mnt/nas")) {
        return "nas";
    } else if (path.startsWith("/home") || path.startsWith("/opt")) {
        return "local";
    }
    return "unknown";
}

// ${...} 변수 해석
private String resolveValue(String value, Map<String, Object> config) {
    if (value == null || !value.contains("${")) {
        return value;
    }
    
    Pattern pattern = Pattern.compile("\\$\\{([^}]+)\\}");
    Matcher matcher = pattern.matcher(value);
    
    StringBuffer result = new StringBuffer();
    while (matcher.find()) {
        String key = matcher.group(1);
        Object resolved = getNestedValue(config, key);
        if (resolved != null) {
            matcher.appendReplacement(result, resolved.toString());
        }
    }
    matcher.appendTail(result);
    
    return result.toString();
}
```

#### 외부 API 추출 (하이브리드)

```java
private List<ApiCheck> extractExternalApis(Map<String, Object> config, 
                                            String profile) {
    List<ApiCheck> apis = new ArrayList<>();
    
    // 회사 도메인 설정
    String companyDomain = getNestedValue(config, 
        "infrastructure.validation.domain");
    if (companyDomain == null) {
        companyDomain = "company.com";  // 기본값
    }
    
    // 1. 명시적 선언 우선
    List<Map<String, Object>> explicitApis = 
        getNestedValue(config, "infrastructure.validation.apis");
    
    if (explicitApis != null && !explicitApis.isEmpty()) {
        for (Map<String, Object> item : explicitApis) {
            String url = resolveValue((String) item.get("url"), config);
            Boolean critical = (Boolean) item.getOrDefault("critical", true);
            String description = (String) item.get("description");
            String method = (String) item.getOrDefault("method", "HEAD");
            
            apis.add(new ApiCheck(url, method, critical, description));
        }
        return apis;
    }
    
    // 2. 자동 추출 (Fallback)
    List<String> excludePatterns = getNestedValue(config, 
        "infrastructure.validation.exclude-patterns");
    
    findAllValues(config, "", (key, value) -> {
        if (value instanceof String) {
            String str = (String) value;
            
            // 제외 패턴 확인
            if (shouldExclude(str, excludePatterns)) {
                return;
            }
            
            // URL 패턴 매칭
            if (isUrl(str)) {
                boolean isCompanyDomain = str.contains(companyDomain);
                apis.add(new ApiCheck(str, "HEAD", isCompanyDomain, key));
            }
        }
    });
    
    return apis;
}

// URL 패턴 감지
private boolean isUrl(String value) {
    return value.matches("^https?://[a-zA-Z0-9.-]+(:[0-9]+)?(/.*)?$");
}

// 제외 패턴 확인
private boolean shouldExclude(String value, List<String> excludePatterns) {
    if (excludePatterns == null || excludePatterns.isEmpty()) {
        return false;
    }
    
    for (String pattern : excludePatterns) {
        // 와일드카드 패턴 지원
        String regex = pattern.replace(".", "\\.").replace("*", ".*");
        if (value.matches(regex) || value.contains(pattern)) {
            return true;
        }
    }
    
    return false;
}

// 모든 값 순회 (재귀)
private void findAllValues(Map<String, Object> map, String prefix, 
                           BiConsumer<String, Object> consumer) {
    for (Map.Entry<String, Object> entry : map.entrySet()) {
        String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
        Object value = entry.getValue();
        
        if (value instanceof Map) {
            findAllValues((Map<String, Object>) value, key, consumer);
        } else if (value instanceof List) {
            // List는 건너뜀 (복잡도 증가)
            continue;
        } else {
            consumer.accept(key, value);
        }
    }
}
```

#### 쿠버네티스 리소스 추출

```java
private List<SecretCheck> extractSecrets(Map<String, Object> config) {
    List<SecretCheck> secrets = new ArrayList<>();
    
    // Vault 토큰
    if (getNestedValue(config, "spring.cloud.vault.uri") != null) {
        secrets.add(new SecretCheck("vault-token", true, "Vault 인증 토큰"));
    }
    
    // Redis 인증
    if (getNestedValue(config, "redis.host") != null) {
        secrets.add(new SecretCheck("redis-credentials", true, "Redis 인증"));
    }
    
    // CDN 키
    String cdnKey = getNestedValue(config, "abc.cdn.key.live");
    if (cdnKey != null && cdnKey.startsWith("/nas")) {
        secrets.add(new SecretCheck("cdn-keys", true, "CDN 서명 키"));
    }
    
    return secrets;
}

private List<PvcCheck> extractPvcs(Map<String, Object> config) {
    List<PvcCheck> pvcs = new ArrayList<>();
    
    // NAS 파일 경로가 있으면 PVC 필요
    String cdnKey = getNestedValue(config, "abc.cdn.key.live");
    if (cdnKey != null && cdnKey.startsWith("/nas")) {
        pvcs.add(new PvcCheck("nas-cdn-keys", true, "CDN 키 저장소", 
            "/nas2/was/key/cdn"));
    }
    
    return pvcs;
}
```

### 1.5 검증 스크립트 자동 복사

```java
private void copyValidationScript(Project project, DeploymentType type) {
    File targetDir = new File(project.getProjectDir(), "bamboo-scripts");
    targetDir.mkdirs();
    
    String scriptName = (type == KUBERNETES) 
        ? "validate-k8s-infrastructure.sh"
        : "validate-infrastructure.sh";
    
    File targetFile = new File(targetDir, scriptName);
    
    // 이미 있으면 건너뜀
    if (targetFile.exists()) {
        return;
    }
    
    // 리소스에서 복사
    try (InputStream is = getClass().getResourceAsStream("/" + scriptName)) {
        Files.copy(is, targetFile.toPath(), REPLACE_EXISTING);
        targetFile.setExecutable(true);
        
        System.out.println("✅ Created " + scriptName);
        System.out.println("⚠️  Please commit to Git:");
        System.out.println("   git add bamboo-scripts/");
    }
}
```

### 1.6 build.gradle (플러그인)

```gradle
plugins {
    id 'java-gradle-plugin'
    id 'maven-publish'
}

group = 'com.company.gradle'
version = '1.0.0'

java {
    sourceCompatibility = JavaVersion.VERSION_21
}

dependencies {
    implementation 'com.google.code.gson:gson:2.10.1'
    implementation 'org.yaml:snakeyaml:2.0'
}

gradlePlugin {
    plugins {
        infrastructureAnalyzer {
            id = 'com.company.infrastructure-analyzer'
            implementationClass = 'com.company.gradle.InfrastructureAnalyzerPlugin'
        }
    }
}

publishing {
    repositories {
        maven {
            url = "https://nexus.company.com/repository/maven-releases/"
            credentials {
                username = System.getenv("NEXUS_USERNAME")
                password = System.getenv("NEXUS_PASSWORD")
            }
        }
    }
}
```

---

## 📝 Phase 2: 검증 스크립트 작성

### 2.1 VM 환경 검증 스크립트

파일: `src/main/resources/validate-infrastructure.sh`


```bash
#!/bin/bash
set -e

ENVIRONMENT=${1:-prod}
STRICT_MODE=${2:-false}

echo "🔍 Starting infrastructure validation for ${ENVIRONMENT}..."

REQUIREMENTS_FILE="requirements-${ENVIRONMENT}.json"

if [ ! -f "${REQUIREMENTS_FILE}" ]; then
    echo "⚠️  ${REQUIREMENTS_FILE} not found"
    exit 0
fi

# 환경별 서버 설정
case ${ENVIRONMENT} in
    dev)
        SSH_HOST=${DEV_SERVER_HOST}
        SSH_USER=${DEV_SERVER_USER}
        STRICT_MODE=false
        ;;
    stg)
        SSH_HOST=${STG_SERVER_HOST}
        SSH_USER=${STG_SERVER_USER}
        STRICT_MODE=false
        ;;
    prod)
        SSH_HOST=${PROD_SERVER_HOST}
        SSH_USER=${PROD_SERVER_USER}
        STRICT_MODE=true
        ;;
esac

ERRORS=()
WARNINGS=()

# 1. NAS 파일 검증
echo ""
echo "📁 Validating NAS files on ${ENVIRONMENT} server..."

if command -v jq &> /dev/null; then
    while IFS= read -r path; do
        if ssh ${SSH_USER}@${SSH_HOST} "test -f ${path}"; then
            echo "  ✅ ${path}"
        else
            echo "  ❌ ${path}"
            ERRORS+=("Missing file: ${path}")
        fi
    done < <(jq -r '.infrastructure.files[]?.path // empty' ${REQUIREMENTS_FILE})
fi

# 2. 외부 API 검증 (회사 도메인 우선)
echo ""
echo "🌐 Validating external APIs from ${ENVIRONMENT} server..."

if command -v jq &> /dev/null; then
    jq -c '.infrastructure.external_apis[]? // empty' ${REQUIREMENTS_FILE} | while read -r api; do
        url=$(echo ${api} | jq -r '.url')
        method=$(echo ${api} | jq -r '.method // "HEAD"')
        critical=$(echo ${api} | jq -r '.critical // true')
        description=$(echo ${api} | jq -r '.description // ""')
        
        # HEAD 요청으로 접근 가능 여부만 확인
        status=$(ssh ${SSH_USER}@${SSH_HOST} "curl -s -I -o /dev/null -w '%{http_code}' --connect-timeout 10 ${url}" 2>/dev/null || echo "000")
        
        # 타임아웃(000)이나 5xx만 실패로 처리
        if [ "$status" = "000" ] || [ "$status" -ge 500 ] 2>/dev/null; then
            if [ "${critical}" = "true" ]; then
                echo "  ❌ ${url} (${status}) - ${description}"
                ERRORS+=("Cannot reach: ${url}")
            else
                echo "  ⚠️  ${url} (${status}) - ${description} [WARNING ONLY]"
                WARNINGS+=("Cannot reach: ${url}")
            fi
        else
            echo "  ✅ ${url} (${status}) - ${description}"
        fi
    done
fi

# 결과 출력
echo ""
echo "============================================================"
if [ ${#ERRORS[@]} -gt 0 ]; then
    echo "❌ ${#ERRORS[@]} Critical Error(s) found:"
    for error in "${ERRORS[@]}"; do
        echo "  - ${error}"
    done
fi

if [ ${#WARNINGS[@]} -gt 0 ]; then
    echo "⚠️  ${#WARNINGS[@]} Warning(s) found:"
    for warning in "${WARNINGS[@]}"; do
        echo "  - ${warning}"
    done
fi

if [ ${#ERRORS[@]} -gt 0 ] && [ "${STRICT_MODE}" = "true" ]; then
    echo "❌ [${ENVIRONMENT}] Infrastructure validation FAILED - BLOCKING DEPLOYMENT"
    exit 1
else
    echo "✅ Infrastructure validation completed"
fi
```

### 2.2 쿠버네티스 환경 검증 스크립트

파일: `src/main/resources/validate-k8s-infrastructure.sh`

```bash
#!/bin/bash
set -e

ENVIRONMENT=${1:-prod}
NAMESPACE=${2:-production}

echo "🔍 Starting Kubernetes infrastructure validation for ${ENVIRONMENT}..."

REQUIREMENTS_FILE="requirements-k8s-${ENVIRONMENT}.json"

if [ ! -f "${REQUIREMENTS_FILE}" ]; then
    echo "⚠️  ${REQUIREMENTS_FILE} not found"
    exit 0
fi

ERRORS=()
WARNINGS=()

# 1. Namespace 확인
echo ""
echo "📦 Validating namespace..."
if kubectl get namespace ${NAMESPACE} &> /dev/null; then
    echo "  ✅ Namespace: ${NAMESPACE}"
else
    echo "  ❌ Namespace: ${NAMESPACE}"
    ERRORS+=("Namespace not found: ${NAMESPACE}")
    exit 1
fi

# 2. ConfigMap 확인
echo ""
echo "⚙️  Validating ConfigMaps..."
if command -v jq &> /dev/null; then
    jq -c '.infrastructure.configmaps[]? // empty' ${REQUIREMENTS_FILE} | while read -r cm; do
        name=$(echo ${cm} | jq -r '.name')
        critical=$(echo ${cm} | jq -r '.critical // true')
        
        if kubectl get configmap ${name} -n ${NAMESPACE} &> /dev/null; then
            echo "  ✅ ConfigMap: ${name}"
        else
            if [ "${critical}" = "true" ]; then
                echo "  ❌ ConfigMap: ${name}"
                ERRORS+=("ConfigMap not found: ${name}")
            else
                echo "  ⚠️  ConfigMap: ${name} [WARNING ONLY]"
                WARNINGS+=("ConfigMap not found: ${name}")
            fi
        fi
    done
fi

# 3. Secret 확인
echo ""
echo "🔐 Validating Secrets..."
if command -v jq &> /dev/null; then
    jq -c '.infrastructure.secrets[]? // empty' ${REQUIREMENTS_FILE} | while read -r secret; do
        name=$(echo ${secret} | jq -r '.name')
        critical=$(echo ${secret} | jq -r '.critical // true')
        
        if kubectl get secret ${name} -n ${NAMESPACE} &> /dev/null; then
            echo "  ✅ Secret: ${name}"
        else
            if [ "${critical}" = "true" ]; then
                echo "  ❌ Secret: ${name}"
                ERRORS+=("Secret not found: ${name}")
            else
                echo "  ⚠️  Secret: ${name} [WARNING ONLY]"
                WARNINGS+=("Secret not found: ${name}")
            fi
        fi
    done
fi

# 4. PVC 확인
echo ""
echo "💾 Validating PersistentVolumeClaims..."
if command -v jq &> /dev/null; then
    jq -c '.infrastructure.pvcs[]? // empty' ${REQUIREMENTS_FILE} | while read -r pvc; do
        name=$(echo ${pvc} | jq -r '.name')
        critical=$(echo ${pvc} | jq -r '.critical // true')
        
        status=$(kubectl get pvc ${name} -n ${NAMESPACE} -o jsonpath='{.status.phase}' 2>/dev/null || echo "NotFound")
        
        if [ "${status}" = "Bound" ]; then
            echo "  ✅ PVC: ${name} (Bound)"
        else
            if [ "${critical}" = "true" ]; then
                echo "  ❌ PVC: ${name} (${status})"
                ERRORS+=("PVC issue: ${name} (${status})")
            else
                echo "  ⚠️  PVC: ${name} (${status}) [WARNING ONLY]"
                WARNINGS+=("PVC issue: ${name} (${status})")
            fi
        fi
    done
fi

# 5. 외부 API 접근 확인
echo ""
echo "🌐 Validating external API access from cluster..."
if command -v jq &> /dev/null; then
    jq -c '.infrastructure.external_apis[]? // empty' ${REQUIREMENTS_FILE} | while read -r api; do
        url=$(echo ${api} | jq -r '.url')
        critical=$(echo ${api} | jq -r '.critical // true')
        description=$(echo ${api} | jq -r '.description // ""')
        
        # 임시 Pod로 curl 테스트
        status=$(kubectl run test-curl-$RANDOM --rm -i --restart=Never \
            --image=curlimages/curl:latest \
            -n ${NAMESPACE} \
            --command -- curl -s -o /dev/null -w '%{http_code}' --connect-timeout 10 ${url} 2>/dev/null || echo "000")
        
        if [ "$status" = "000" ] || [ "$status" -ge 500 ] 2>/dev/null; then
            if [ "${critical}" = "true" ]; then
                echo "  ❌ ${url} (${status}) - ${description}"
                ERRORS+=("Cannot reach: ${url}")
            else
                echo "  ⚠️  ${url} (${status}) - ${description} [WARNING ONLY]"
                WARNINGS+=("Cannot reach: ${url}")
            fi
        else
            echo "  ✅ ${url} (${status}) - ${description}"
        fi
    done
fi

# 결과 출력
echo ""
echo "============================================================"
if [ ${#ERRORS[@]} -gt 0 ]; then
    echo "❌ ${#ERRORS[@]} Critical Error(s) found:"
    for error in "${ERRORS[@]}"; do
        echo "  - ${error}"
    done
fi

if [ ${#WARNINGS[@]} -gt 0 ]; then
    echo "⚠️  ${#WARNINGS[@]} Warning(s) found:"
    for warning in "${WARNINGS[@]}"; do
        echo "  - ${warning}"
    done
fi

if [ ${#ERRORS[@]} -gt 0 ] && [ "${ENVIRONMENT}" = "prod" ]; then
    echo "❌ [${ENVIRONMENT}] Kubernetes infrastructure validation FAILED - BLOCKING DEPLOYMENT"
    exit 1
else
    echo "✅ Kubernetes infrastructure validation completed"
fi
```

---

## 🚀 Phase 3: 프로젝트 적용 방법

### 3.1 플러그인 배포

```bash
cd infrastructure-analyzer
export NEXUS_USERNAME="your-username"
export NEXUS_PASSWORD="your-password"
./gradlew publish
```

### 3.2 프로젝트에 플러그인 추가

**settings.gradle:**
```gradle
pluginManagement {
    repositories {
        maven {
            url 'https://nexus.company.com/repository/maven-public/'
        }
        gradlePluginPortal()
    }
}
```

**build.gradle:**
```gradle
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.4.1'
    id 'com.company.infrastructure-analyzer' version '1.0.0'  // ← 추가
}
```

**application.yml (선택):**
```yaml
infrastructure:
  validation:
    domain: "abc.co.kr"  # 회사 도메인 설정
```

### 3.3 빌드 및 커밋

```bash
# 빌드 실행
./gradlew build

# 출력 예시:
# ✅ Detected deployment type: VM (또는 KUBERNETES)
# ✅ Created requirements-dev.json
# ✅ Created requirements-stg.json
# ✅ Created requirements-prod.json
# ✅ Created bamboo-scripts/validate-infrastructure.sh
# ⚠️  Please commit to Git

# Git 커밋 (최초 1회만)
git add build.gradle settings.gradle bamboo-scripts/
git commit -m "chore: add infrastructure validation"
git push
```

---

## 📦 Phase 4: Bamboo 파이프라인 설정

### 4.1 Build Plan

```
Build Plan
├─ Task 1: Source Code Checkout
├─ Task 2: Gradle Build
│   Script: ./gradlew clean build
│   → requirements*.json 자동 생성
└─ Task 3: Artifact Definition
    Copy Pattern:
      - build/libs/*.jar
      - requirements*.json
      - bamboo-scripts/**
```

### 4.2 Deployment Plan (VM 환경)

```
Deployment Plan (prod)
├─ Task 1: Artifact Download
├─ Task 2: Infrastructure Validation
│   Script: bash bamboo-scripts/validate-infrastructure.sh prod
│   Environment Variables:
│     - PROD_SERVER_HOST=${bamboo.prod.server.host}
│     - PROD_SERVER_USER=${bamboo.prod.server.user}
└─ Task 3: Deploy via SSH
    Script: scp build/libs/*.jar ${PROD_SERVER_USER}@${PROD_SERVER_HOST}:/app/
```

### 4.3 Deployment Plan (쿠버네티스 환경)

```
Deployment Plan (prod)
├─ Task 1: Artifact Download
├─ Task 2: K8s Infrastructure Validation
│   Script: bash bamboo-scripts/validate-k8s-infrastructure.sh prod production
│   Environment Variables:
│     - KUBECONFIG=${bamboo.k8s.prod.kubeconfig}
└─ Task 3: Deploy to Kubernetes
    Script: kubectl apply -f k8s/prod/
```

---

## 📊 생성되는 파일 예시

### VM 환경: requirements-prod.json

```json
{
  "version": "1.0",
  "project": "abc-sports-api",
  "environment": "prod",
  "platform": "vm",
  "infrastructure": {
    "company_domain": "abc.co.kr",
    "files": [
      {
        "path": "/nas2/was/key/cdn/signed_abcspots_cdn.der",
        "location": "nas",
        "critical": true,
        "description": "CDN 서명 키"
      }
    ],
    "external_apis": [
      {
        "url": "https://api.abc.co.kr",
        "method": "HEAD",
        "expected_status": [200, 301, 302, 404],
        "critical": true,
        "description": "메인 API (회사 도메인)"
      },
      {
        "url": "https://stdpay.inicis.com",
        "method": "HEAD",
        "expected_status": [200, 301, 302, 404],
        "critical": false,
        "description": "이니시스 결제 (외부 - 경고만)"
      }
    ]
  }
}
```

### 쿠버네티스 환경: requirements-k8s-prod.json

```json
{
  "version": "1.0",
  "project": "abc-sports-api",
  "environment": "prod",
  "platform": "kubernetes",
  "infrastructure": {
    "namespace": "production",
    "company_domain": "abc.co.kr",
    "configmaps": [
      {"name": "app-config", "critical": true}
    ],
    "secrets": [
      {"name": "cdn-keys", "critical": true},
      {"name": "redis-credentials", "critical": true}
    ],
    "pvcs": [
      {"name": "nas-storage", "critical": true}
    ],
    "external_apis": [
      {
        "url": "https://api.abc.co.kr",
        "critical": true,
        "description": "메인 API (회사 도메인)"
      },
      {
        "url": "https://stdpay.inicis.com",
        "critical": false,
        "description": "이니시스 결제 (외부 - 경고만)"
      }
    ]
  }
}
```

---

## ✅ 구현 체크리스트

### Phase 1: Gradle 플러그인 개발
- [ ] 프로젝트 구조 생성
- [ ] DeploymentType enum 작성
- [ ] 환경 감지 로직 구현 (detectDeploymentType)
- [ ] YAML 파싱 로직 구현
- [ ] NAS 파일 추출 로직 (extractNasFiles)
- [ ] 외부 API 추출 로직 (extractExternalApis)
  - [ ] 회사 도메인 우선 처리
  - [ ] HEAD 요청 방식 적용
- [ ] 쿠버네티스 리소스 추출 로직
  - [ ] ConfigMap 추출
  - [ ] Secret 추출
  - [ ] PVC 추출
- [ ] requirements.json 생성 로직 (VM/K8s 분리)
- [ ] 검증 스크립트 자동 복사 로직
- [ ] build.gradle 작성
- [ ] Nexus 배포

### Phase 2: 검증 스크립트 작성
- [ ] validate-infrastructure.sh 작성 (VM용)
  - [ ] 환경별 서버 설정
  - [ ] NAS 파일 검증
  - [ ] 외부 API 검증 (critical 처리)
  - [ ] 결과 출력 및 exit code
- [ ] validate-k8s-infrastructure.sh 작성 (K8s용)
  - [ ] Namespace 확인
  - [ ] ConfigMap 확인
  - [ ] Secret 확인
  - [ ] PVC 확인
  - [ ] 외부 API 확인 (임시 Pod 사용)
  - [ ] 결과 출력 및 exit code

### Phase 3: 테스트
- [ ] VM 환경 프로젝트 1개 테스트
- [ ] 쿠버네티스 환경 프로젝트 1개 테스트
- [ ] 환경 자동 감지 검증
- [ ] 회사 도메인 우선 검증 확인
- [ ] dev/stg 경고 모드 확인
- [ ] prod 차단 모드 확인

### Phase 4: Bamboo 설정
- [ ] SSH 키 설정 (환경별)
- [ ] Bamboo 전역 변수 설정
- [ ] Build Plan 설정
- [ ] Deployment Plan 설정 (VM)
- [ ] Deployment Plan 설정 (K8s)

### Phase 5: 확산
- [ ] 5개 프로젝트 적용
- [ ] 문서화
- [ ] 팀 교육

---

## 🎯 핵심 포인트

### 1. 자동화된 것
- ✅ 환경 자동 감지 (VM or K8s)
- ✅ requirements.json 자동 생성 (dev, stg, prod)
- ✅ 검증 스크립트 자동 생성 (최초 1회)
- ✅ 회사 도메인 우선 검증
- ✅ 환경별 검증 모드 (dev/stg: 경고, prod: 차단)

### 2. 개발자가 할 일
1. build.gradle에 플러그인 추가 (1줄)
2. ./gradlew build 실행
3. bamboo-scripts/ 폴더 Git 커밋 (최초 1회)
4. Git Push

### 3. 검증 전략
- **회사 도메인** (*.abc.co.kr): critical=true → 실패 시 배포 차단
- **외부 도메인** (inicis.com 등): critical=false → 경고만 표시
- **헬스체크 없는 레거시**: HEAD 요청으로 접근 가능 여부만 확인
- **허용 가능한 상태 코드**: 200, 301, 302, 401, 403, 404
- **실패로 처리**: 000 (타임아웃), 5xx (서버 에러)

### 4. 환경별 처리
- **dev/stg**: STRICT_MODE=false, 경고만 표시
- **prod**: STRICT_MODE=true, 검증 실패 시 배포 차단

---

## 📅 예상 일정

### Week 1: 플러그인 개발
- Day 1-2: 환경 감지 및 YAML 파싱 로직
- Day 3-4: VM/K8s 검증 로직 구현
- Day 5: 테스트 및 Nexus 배포

### Week 2: 확산
- Day 1-2: 5개 프로젝트 적용
- Day 3-4: Bamboo Plan 설정
- Day 5: 문서화 및 팀 교육

**총 예상 기간: 2주**

---

## 🚨 주의사항

1. **JNDI 데이터소스**: WAS 설정이므로 검증하지 않음
2. **CORS 설정**: 애플리케이션 레벨이므로 인프라 검증에서 제외
3. **회사 도메인 설정**: application.yml에 `infrastructure.validation.domain` 설정
4. **헬스체크 엔드포인트**: 없는 경우 HEAD 요청으로 접근 가능 여부만 확인
5. **환경 감지**: 쿠버네티스 관련 키워드가 있으면 K8s로 감지
6. **검증 스크립트**: Git에 커밋해야 Bamboo에서 사용 가능

---

**작성일**: 2026-02-11  
**버전**: 1.0 (통합 실행계획)  
**목적**: AI가 작업할 수 있도록 중복 제거 및 단계별 가이드 제공

