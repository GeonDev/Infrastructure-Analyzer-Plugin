# 소스코드 정적 분석을 통한 하드코딩 검출 실행계획

## 📋 개요

설정 파일(`application.yml`, `application.properties`)뿐만 아니라 Java 소스코드에 하드코딩된 파일 경로와 API URL도 자동으로 검출하여 검증 대상에 포함합니다.

## 🎯 목표

### 검출 대상

1. **하드코딩된 파일 경로**
   ```java
   String keyPath = "/nas2/was/key/cdn/signed.der";
   File certFile = new File("/home/app/cert/payment.pem");
   Path configPath = Paths.get("/opt/config/app.properties");
   ```

2. **하드코딩된 API URL**
   ```java
   String apiUrl = "https://api.abc.co.kr/v1/users";
   RestTemplate rest = new RestTemplate();
   rest.getForObject("https://payment.inicis.com/api", String.class);
   ```

3. **상수로 선언된 경로/URL**
   ```java
   private static final String CDN_KEY_PATH = "/nas2/was/key/cdn/signed.der";
   public static final String API_ENDPOINT = "https://api.abc.co.kr";
   ```

### 제외 대상

- 테스트 코드 (`src/test/`)
- 로컬 경로 (`localhost`, `127.0.0.1`, `file://`)
- 상대 경로 (`./`, `../`, `classpath:`)
- 빌드 관련 경로 (`build/`, `target/`)

## 🏗️ 구현 전략

### Phase 1: AST 기반 정적 분석

JavaParser 라이브러리를 사용하여 소스코드를 파싱하고 AST(Abstract Syntax Tree)를 분석합니다.

#### 의존성 추가

```gradle
dependencies {
    implementation 'com.google.code.gson:gson:2.10.1'
    implementation 'org.yaml:snakeyaml:2.2'
    implementation 'com.github.javaparser:javaparser-core:3.25.8'  // 추가
}
```

### Phase 2: 패턴 매칭 전략

#### 파일 경로 패턴

```java
// 1. 절대 경로 (Unix/Linux)
Pattern UNIX_PATH = Pattern.compile("\"(/[a-zA-Z0-9/_.-]+\\.(der|pem|p8|p12|cer|crt|key|json|jks|keystore|properties|xml|yml|yaml))\"");

// 2. 절대 경로 (디렉토리)
Pattern UNIX_DIR = Pattern.compile("\"(/(nas|mnt|home|var|opt|data)/[a-zA-Z0-9/_-]+)\"");

// 3. Windows 경로
Pattern WINDOWS_PATH = Pattern.compile("\"([A-Z]:\\\\[^\"]+\\.(der|pem|p8|p12|cer|crt|key|json|jks|keystore|properties|xml|yml|yaml))\"");
```

#### URL 패턴

```java
// HTTP/HTTPS URL
Pattern URL_PATTERN = Pattern.compile("\"(https?://[a-zA-Z0-9.-]+(:[0-9]+)?(/[^\"]*)?)\")");
```

## 📦 구현 상세

### 1. SourceCodeAnalyzer 클래스 생성

```java
package com.company.gradle.analyzer;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Java 소스코드를 정적 분석하여 하드코딩된 파일 경로와 URL을 추출합니다.
 */
public class SourceCodeAnalyzer {

    private static final Pattern FILE_PATH_PATTERN = 
        Pattern.compile("^(/[a-zA-Z0-9/_.-]+\\.(der|pem|p8|p12|cer|crt|key|json|jks|keystore|properties|xml|yml|yaml))$");
    
    private static final Pattern DIR_PATH_PATTERN = 
        Pattern.compile("^(/(nas|mnt|home|var|opt|data)/[a-zA-Z0-9/_-]+)$");
    
    private static final Pattern URL_PATTERN = 
        Pattern.compile("^(https?://[a-zA-Z0-9.-]+(:[0-9]+)?(/[^\\s]*)?)$");
    
    private static final Set<String> EXCLUDE_PATTERNS = Set.of(
        "localhost", "127.0.0.1", "0.0.0.0",
        "classpath:", "file://", "./", "../",
        "build/", "target/", ".gradle/",
        "example.com", "test.com", "mock"
    );

    private final File sourceDir;
    private final JavaParser javaParser;

    public SourceCodeAnalyzer(File sourceDir) {
        this.sourceDir = sourceDir;
        this.javaParser = new JavaParser();
    }

    /**
     * 소스코드에서 하드코딩된 파일 경로를 추출합니다.
     */
    public List<String> extractFilePaths() {
        Set<String> paths = new HashSet<>();
        
        try (Stream<Path> stream = Files.walk(sourceDir.toPath())) {
            stream.filter(path -> path.toString().endsWith(".java"))
                  .filter(path -> !path.toString().contains("/test/"))
                  .forEach(path -> {
                      try {
                          CompilationUnit cu = javaParser.parse(path).getResult().orElse(null);
                          if (cu != null) {
                              extractFilePathsFromCU(cu, paths);
                          }
                      } catch (IOException e) {
                          // 파싱 실패 시 무시
                      }
                  });
        } catch (IOException e) {
            System.err.println("⚠️  소스코드 분석 실패: " + e.getMessage());
        }
        
        return new ArrayList<>(paths);
    }

    /**
     * 소스코드에서 하드코딩된 URL을 추출합니다.
     */
    public List<String> extractUrls() {
        Set<String> urls = new HashSet<>();
        
        try (Stream<Path> stream = Files.walk(sourceDir.toPath())) {
            stream.filter(path -> path.toString().endsWith(".java"))
                  .filter(path -> !path.toString().contains("/test/"))
                  .forEach(path -> {
                      try {
                          CompilationUnit cu = javaParser.parse(path).getResult().orElse(null);
                          if (cu != null) {
                              extractUrlsFromCU(cu, urls);
                          }
                      } catch (IOException e) {
                          // 파싱 실패 시 무시
                      }
                  });
        } catch (IOException e) {
            System.err.println("⚠️  소스코드 분석 실패: " + e.getMessage());
        }
        
        return new ArrayList<>(urls);
    }

    /**
     * CompilationUnit에서 파일 경로 추출
     */
    private void extractFilePathsFromCU(CompilationUnit cu, Set<String> paths) {
        // 1. 문자열 리터럴 검사
        cu.findAll(StringLiteralExpr.class).forEach(expr -> {
            String value = expr.getValue();
            if (isValidFilePath(value) && !shouldExclude(value)) {
                paths.add(value);
            }
        });

        // 2. 상수 필드 검사
        cu.findAll(FieldDeclaration.class).forEach(field -> {
            if (field.isFinal() && field.isStatic()) {
                field.getVariables().forEach(var -> {
                    var.getInitializer().ifPresent(init -> {
                        if (init instanceof StringLiteralExpr) {
                            String value = ((StringLiteralExpr) init).getValue();
                            if (isValidFilePath(value) && !shouldExclude(value)) {
                                paths.add(value);
                            }
                        }
                    });
                });
            }
        });
    }

    /**
     * CompilationUnit에서 URL 추출
     */
    private void extractUrlsFromCU(CompilationUnit cu, Set<String> urls) {
        // 1. 문자열 리터럴 검사
        cu.findAll(StringLiteralExpr.class).forEach(expr -> {
            String value = expr.getValue();
            if (isValidUrl(value) && !shouldExclude(value)) {
                urls.add(value);
            }
        });

        // 2. 상수 필드 검사
        cu.findAll(FieldDeclaration.class).forEach(field -> {
            if (field.isFinal() && field.isStatic()) {
                field.getVariables().forEach(var -> {
                    var.getInitializer().ifPresent(init -> {
                        if (init instanceof StringLiteralExpr) {
                            String value = ((StringLiteralExpr) init).getValue();
                            if (isValidUrl(value) && !shouldExclude(value)) {
                                urls.add(value);
                            }
                        }
                    });
                });
            }
        });
    }

    /**
     * 유효한 파일 경로인지 확인
     */
    private boolean isValidFilePath(String value) {
        return FILE_PATH_PATTERN.matcher(value).matches() || 
               DIR_PATH_PATTERN.matcher(value).matches();
    }

    /**
     * 유효한 URL인지 확인
     */
    private boolean isValidUrl(String value) {
        return URL_PATTERN.matcher(value).matches();
    }

    /**
     * 제외 패턴에 해당하는지 확인
     */
    private boolean shouldExclude(String value) {
        String lowerValue = value.toLowerCase();
        return EXCLUDE_PATTERNS.stream().anyMatch(lowerValue::contains);
    }
}
```

### 2. InfrastructureExtractor 수정

기존 설정 파일 기반 추출에 소스코드 분석 결과를 병합합니다.

```java
public class InfrastructureExtractor {
    
    private final Map<String, Object> config;
    private final SourceCodeAnalyzer sourceAnalyzer;  // 추가

    public InfrastructureExtractor(Map<String, Object> config, File projectDir) {
        this.config = config;
        this.sourceAnalyzer = new SourceCodeAnalyzer(
            new File(projectDir, "src/main/java")
        );
    }

    /**
     * 파일 경로 추출 (설정 파일 + 소스코드)
     */
    public List<FileCheck> extractFiles() {
        List<FileCheck> files = new ArrayList<>();
        
        // 1. 설정 파일에서 명시적 선언 추출
        List<FileCheck> explicitFiles = extractExplicitFiles();
        if (!explicitFiles.isEmpty()) {
            files.addAll(explicitFiles);
        }
        
        // 2. 설정 파일에서 자동 추출
        List<FileCheck> configFiles = extractFilesFromConfig();
        files.addAll(configFiles);
        
        // 3. 소스코드에서 하드코딩 추출
        List<FileCheck> sourceFiles = extractFilesFromSource();
        files.addAll(sourceFiles);
        
        // 중복 제거
        return deduplicateFiles(files);
    }

    /**
     * API URL 추출 (설정 파일 + 소스코드)
     */
    public List<ApiCheck> extractApis() {
        List<ApiCheck> apis = new ArrayList<>();
        
        // 1. 설정 파일에서 명시적 선언 추출
        List<ApiCheck> explicitApis = extractExplicitApis();
        if (!explicitApis.isEmpty()) {
            apis.addAll(explicitApis);
        }
        
        // 2. 설정 파일에서 자동 추출
        List<ApiCheck> configApis = extractApisFromConfig();
        apis.addAll(configApis);
        
        // 3. 소스코드에서 하드코딩 추출
        List<ApiCheck> sourceApis = extractApisFromSource();
        apis.addAll(sourceApis);
        
        // 중복 제거
        return deduplicateApis(apis);
    }

    /**
     * 소스코드에서 파일 경로 추출
     */
    private List<FileCheck> extractFilesFromSource() {
        List<FileCheck> files = new ArrayList<>();
        List<String> paths = sourceAnalyzer.extractFilePaths();
        
        for (String path : paths) {
            String location = detectLocation(path);
            files.add(new FileCheck(
                path,
                location,
                true,  // 소스코드 하드코딩은 기본적으로 critical
                "소스코드에서 검출됨"
            ));
        }
        
        return files;
    }

    /**
     * 소스코드에서 API URL 추출
     */
    private List<ApiCheck> extractApisFromSource() {
        List<ApiCheck> apis = new ArrayList<>();
        List<String> urls = sourceAnalyzer.extractUrls();
        String companyDomain = getCompanyDomain();
        
        for (String url : urls) {
            boolean isCompanyDomain = url.contains(companyDomain);
            apis.add(new ApiCheck(
                url,
                "HEAD",
                isCompanyDomain,
                "소스코드에서 검출됨"
            ));
        }
        
        return apis;
    }

    /**
     * 파일 중복 제거 (경로 기준)
     */
    private List<FileCheck> deduplicateFiles(List<FileCheck> files) {
        Map<String, FileCheck> uniqueFiles = new LinkedHashMap<>();
        for (FileCheck file : files) {
            uniqueFiles.putIfAbsent(file.getPath(), file);
        }
        return new ArrayList<>(uniqueFiles.values());
    }

    /**
     * API 중복 제거 (URL 기준)
     */
    private List<ApiCheck> deduplicateApis(List<ApiCheck> apis) {
        Map<String, ApiCheck> uniqueApis = new LinkedHashMap<>();
        for (ApiCheck api : apis) {
            uniqueApis.putIfAbsent(api.getUrl(), api);
        }
        return new ArrayList<>(uniqueApis.values());
    }
}
```

### 3. InfrastructureAnalyzerTask 수정

```java
private Requirements generateVmRequirements(String profile, Map<String, Object> config) {
    // projectDir 전달
    InfrastructureExtractor extractor = new InfrastructureExtractor(
        config, 
        getProject().getProjectDir()
    );

    Requirements req = new Requirements();
    req.setProject(getProject().getName());
    req.setEnvironment(profile);
    req.setPlatform("vm");

    Requirements.Infrastructure infra = req.getInfrastructure();
    infra.setCompany_domain(extractor.getCompanyDomain());
    infra.setFiles(extractor.extractFiles());
    infra.setExternal_apis(extractor.extractApis());

    return req;
}
```

## 📊 출력 예시

### requirements-prod.json

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
        "description": "설정 파일에서 검출됨"
      },
      {
        "path": "/home/app/cert/payment.pem",
        "location": "local",
        "critical": true,
        "description": "소스코드에서 검출됨"
      }
    ],
    "external_apis": [
      {
        "url": "https://api.abc.co.kr/v1/users",
        "method": "HEAD",
        "critical": true,
        "description": "소스코드에서 검출됨"
      },
      {
        "url": "https://stdpay.inicis.com/api",
        "method": "HEAD",
        "critical": false,
        "description": "소스코드에서 검출됨"
      }
    ]
  }
}
```

## 🎯 실행 로그 예시

```
> Task :analyzeInfrastructure
✅ 감지된 배포 환경: VM
📄 설정 파일: application.yml
🔍 소스코드 분석 중...
   - 검출된 파일 경로: 3개
   - 검출된 API URL: 5개
✅ 생성됨: requirements-dev.json
✅ 생성됨: requirements-stg.json
✅ 생성됨: requirements-prod.json
✅ 생성됨: build/infrastructure/validate-infrastructure.sh
```

## ⚠️ 주의사항

### 1. 성능 고려

- 대규모 프로젝트에서는 소스코드 분석에 시간이 걸릴 수 있음
- 캐싱 메커니즘 고려 (파일 변경 시에만 재분석)

### 2. False Positive 최소화

- 테스트 코드 제외
- 예제/샘플 코드 제외
- 주석 내 문자열 제외

### 3. 설정 옵션 제공

```yaml
infrastructure:
  validation:
    source-code-analysis:
      enabled: true  # 소스코드 분석 활성화 여부
      exclude-packages:
        - "com.example.test"
        - "com.example.sample"
```

## 📅 구현 단계

### Phase 1: 기본 구현 (1-2일)
- [ ] JavaParser 의존성 추가
- [ ] SourceCodeAnalyzer 클래스 구현
- [ ] 기본 패턴 매칭 로직 구현

### Phase 2: 통합 (1일)
- [ ] InfrastructureExtractor 수정
- [ ] 중복 제거 로직 구현
- [ ] 로깅 추가

### Phase 3: 테스트 (1일)
- [ ] 실제 프로젝트에서 테스트
- [ ] False Positive 패턴 조정
- [ ] 성능 최적화

### Phase 4: 문서화 (0.5일)
- [ ] README 업데이트
- [ ] 사용 예시 추가

**총 예상 기간: 3-4일**

## 🚀 기대 효과

1. **검출률 향상**: 설정 파일에 없는 하드코딩된 경로/URL도 검출
2. **코드 품질 개선**: 하드코딩 발견 시 리팩토링 유도
3. **배포 안정성 향상**: 숨겨진 의존성까지 사전 검증

## 📝 향후 개선 방향

1. **Kotlin 지원**: Kotlin 소스코드 분석 추가
2. **어노테이션 기반 제외**: `@IgnoreInfraCheck` 어노테이션으로 특정 코드 제외
3. **IDE 플러그인**: IntelliJ/Eclipse 플러그인으로 실시간 경고
4. **AI 기반 분석**: 패턴 매칭 외에 ML 기반 검출
