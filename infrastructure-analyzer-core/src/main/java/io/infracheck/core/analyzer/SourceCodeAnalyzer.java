package io.infracheck.core.analyzer;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;

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

    public List<String> extractFilePaths() {
        if (!sourceDir.exists()) return Collections.emptyList();

        Set<String> paths = new HashSet<>();
        walkJavaFiles(path -> {
            try {
                CompilationUnit cu = javaParser.parse(path).getResult().orElse(null);
                if (cu != null) extractFilePathsFromCU(cu, paths);
            } catch (IOException e) { /* 파싱 실패 시 무시 */ }
        });

        return new ArrayList<>(paths);
    }

    public List<String> extractUrls() {
        if (!sourceDir.exists()) return Collections.emptyList();

        Set<String> urls = new HashSet<>();
        walkJavaFiles(path -> {
            try {
                CompilationUnit cu = javaParser.parse(path).getResult().orElse(null);
                if (cu != null) extractUrlsFromCU(cu, urls);
            } catch (IOException e) { /* 파싱 실패 시 무시 */ }
        });

        return new ArrayList<>(urls);
    }

    private void walkJavaFiles(java.util.function.Consumer<Path> action) {
        try (Stream<Path> stream = Files.walk(sourceDir.toPath())) {
            stream.filter(p -> p.toString().endsWith(".java"))
                  .filter(p -> !p.toString().contains("/test/"))
                  .filter(p -> !p.toString().contains("\\test\\"))
                  .forEach(action);
        } catch (IOException e) {
            System.err.println("⚠️  소스코드 분석 실패: " + e.getMessage());
        }
    }

    /**
     * 어노테이션(@Schema example 등) 내부 문자열은 제외합니다.
     */
    private void extractFilePathsFromCU(CompilationUnit cu, Set<String> paths) {
        Set<StringLiteralExpr> annotationStrings = new HashSet<>();
        cu.findAll(AnnotationExpr.class).forEach(annotation ->
            annotationStrings.addAll(annotation.findAll(StringLiteralExpr.class))
        );

        cu.findAll(StringLiteralExpr.class).forEach(expr -> {
            if (annotationStrings.contains(expr)) return;
            String value = expr.getValue();
            if (isValidFilePath(value) && !shouldExclude(value)) paths.add(value);
        });

        cu.findAll(FieldDeclaration.class).forEach(field -> {
            if (field.isFinal() && field.isStatic()) {
                field.getVariables().forEach(var ->
                    var.getInitializer().ifPresent(init -> {
                        if (init instanceof StringLiteralExpr) {
                            String value = ((StringLiteralExpr) init).getValue();
                            if (isValidFilePath(value) && !shouldExclude(value)) paths.add(value);
                        }
                    })
                );
            }
        });
    }

    /**
     * 어노테이션(@Schema example 등) 내부 문자열은 제외합니다.
     */
    private void extractUrlsFromCU(CompilationUnit cu, Set<String> urls) {
        Set<StringLiteralExpr> annotationStrings = new HashSet<>();
        cu.findAll(AnnotationExpr.class).forEach(annotation ->
            annotationStrings.addAll(annotation.findAll(StringLiteralExpr.class))
        );

        cu.findAll(StringLiteralExpr.class).forEach(expr -> {
            if (annotationStrings.contains(expr)) return;
            String value = expr.getValue();
            if (isValidUrl(value) && !shouldExclude(value)) urls.add(value);
        });

        cu.findAll(FieldDeclaration.class).forEach(field -> {
            if (field.isFinal() && field.isStatic()) {
                field.getVariables().forEach(var ->
                    var.getInitializer().ifPresent(init -> {
                        if (init instanceof StringLiteralExpr) {
                            String value = ((StringLiteralExpr) init).getValue();
                            if (isValidUrl(value) && !shouldExclude(value)) urls.add(value);
                        }
                    })
                );
            }
        });
    }

    private boolean isValidFilePath(String value) {
        return FILE_PATH_PATTERN.matcher(value).matches() ||
               DIR_PATH_PATTERN.matcher(value).matches();
    }

    private boolean isValidUrl(String value) {
        return URL_PATTERN.matcher(value).matches();
    }

    private boolean shouldExclude(String value) {
        String lowerValue = value.toLowerCase();
        return EXCLUDE_PATTERNS.stream().anyMatch(lowerValue::contains);
    }
}
