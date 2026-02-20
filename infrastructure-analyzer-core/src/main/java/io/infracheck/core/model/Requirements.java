package io.infracheck.core.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 인프라 검증 요구사항 (requirements.json 루트 객체)
 */
public class Requirements {
    private String version = "1.0";
    private String project;
    private String environment;
    private String platform; // "vm" 또는 "kubernetes"
    private Infrastructure infrastructure = new Infrastructure();

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getProject() { return project; }
    public void setProject(String project) { this.project = project; }
    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }
    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }
    public Infrastructure getInfrastructure() { return infrastructure; }
    public void setInfrastructure(Infrastructure infrastructure) { this.infrastructure = infrastructure; }

    /**
     * 인프라 검증 항목 컨테이너
     */
    public static class Infrastructure {
        private String company_domain;
        private String namespace; // K8s 전용
        private List<FileCheck> files = new ArrayList<>();
        private List<ApiCheck> external_apis = new ArrayList<>();
        private List<DirectoryCheck> directories = new ArrayList<>(); // VM 전용
        private List<ConfigMapCheck> configmaps = new ArrayList<>(); // K8s 전용
        private List<SecretCheck> secrets = new ArrayList<>();       // K8s 전용
        private List<PvcCheck> pvcs = new ArrayList<>();             // K8s 전용

        public String getCompany_domain() { return company_domain; }
        public void setCompany_domain(String company_domain) { this.company_domain = company_domain; }
        public String getNamespace() { return namespace; }
        public void setNamespace(String namespace) { this.namespace = namespace; }
        public List<FileCheck> getFiles() { return files; }
        public void setFiles(List<FileCheck> files) { this.files = files; }
        public List<ApiCheck> getExternal_apis() { return external_apis; }
        public void setExternal_apis(List<ApiCheck> apis) { this.external_apis = apis; }
        public List<DirectoryCheck> getDirectories() { return directories; }
        public void setDirectories(List<DirectoryCheck> directories) { this.directories = directories; }
        public List<ConfigMapCheck> getConfigmaps() { return configmaps; }
        public void setConfigmaps(List<ConfigMapCheck> configmaps) { this.configmaps = configmaps; }
        public List<SecretCheck> getSecrets() { return secrets; }
        public void setSecrets(List<SecretCheck> secrets) { this.secrets = secrets; }
        public List<PvcCheck> getPvcs() { return pvcs; }
        public void setPvcs(List<PvcCheck> pvcs) { this.pvcs = pvcs; }
    }
}
