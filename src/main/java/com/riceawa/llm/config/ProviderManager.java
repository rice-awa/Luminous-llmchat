package com.riceawa.llm.config;

import com.riceawa.llm.service.ProviderHealthChecker;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Provider管理和自动切换服务
 * 负责Provider验证、故障切换等逻辑
 */
public class ProviderManager {

    private final List<Provider> providers;
    private final ProviderHealthChecker healthChecker;

    public ProviderManager(List<Provider> providers) {
        this.providers = providers;
        this.healthChecker = ProviderHealthChecker.getInstance();
    }
    
    /**
     * 检查Provider是否有有效的API密钥
     */
    public boolean hasValidApiKey(Provider provider) {
        if (provider == null || provider.getApiKey() == null) {
            return false;
        }
        
        return !ConfigDefaults.isPlaceholderApiKey(provider.getApiKey());
    }
    
    /**
     * 检查Provider是否完全有效（包括配置完整性和API密钥）
     */
    public boolean isProviderValid(Provider provider) {
        return provider != null && 
               provider.isValid() && 
               hasValidApiKey(provider);
    }
    
    /**
     * 获取所有有效的Provider
     */
    public List<Provider> getValidProviders() {
        return providers.stream()
                .filter(this::isProviderValid)
                .toList();
    }
    
    /**
     * 查找指定名称的Provider
     */
    public Optional<Provider> findProvider(String name) {
        if (name == null || name.trim().isEmpty()) {
            return Optional.empty();
        }
        
        return providers.stream()
                .filter(p -> name.equals(p.getName()))
                .findFirst();
    }
    
    /**
     * 检查指定的Provider和Model组合是否有效
     */
    public boolean isProviderModelValid(String providerName, String modelName) {
        return findProvider(providerName)
                .filter(this::isProviderValid)
                .map(provider -> provider.supportsModel(modelName))
                .orElse(false);
    }
    
    /**
     * 获取第一个有效的Provider
     */
    public Optional<Provider> getFirstValidProvider() {
        return providers.stream()
                .filter(this::isProviderValid)
                .findFirst();
    }
    
    /**
     * 获取指定Provider的第一个可用模型
     */
    public Optional<String> getFirstValidModel(String providerName) {
        return findProvider(providerName)
                .filter(this::isProviderValid)
                .flatMap(provider -> provider.getModels().stream().findFirst());
    }
    
    /**
     * 自动选择最佳的Provider和Model组合
     * 返回 [providerName, modelName] 数组，如果没有找到则返回空
     */
    public Optional<String[]> selectBestProviderModel() {
        return getFirstValidProvider()
                .flatMap(provider -> {
                    Optional<String> model = getFirstValidModel(provider.getName());
                    return model.map(m -> new String[]{provider.getName(), m});
                });
    }
    
    /**
     * 尝试修复当前的Provider和Model配置
     * 如果当前配置无效，自动切换到有效的组合
     */
    public ProviderModelResult fixCurrentConfiguration(String currentProvider, String currentModel) {
        // 检查当前配置是否有效
        if (isProviderModelValid(currentProvider, currentModel)) {
            return new ProviderModelResult(true, currentProvider, currentModel, "当前配置有效");
        }
        
        // 当前配置无效，尝试自动修复
        
        // 1. 如果Provider有效但Model无效，尝试使用该Provider的第一个模型
        Optional<Provider> providerOpt = findProvider(currentProvider);
        if (providerOpt.isPresent() && isProviderValid(providerOpt.get())) {
            Optional<String> firstModel = getFirstValidModel(currentProvider);
            if (firstModel.isPresent()) {
                return new ProviderModelResult(true, currentProvider, firstModel.get(), 
                    "Provider有效，已切换到第一个可用模型: " + firstModel.get());
            }
        }
        
        // 2. Provider无效或没有可用模型，切换到第一个有效的Provider和Model组合
        Optional<String[]> bestCombo = selectBestProviderModel();
        if (bestCombo.isPresent()) {
            String[] combo = bestCombo.get();
            return new ProviderModelResult(true, combo[0], combo[1], 
                "已自动切换到有效的Provider: " + combo[0] + ", Model: " + combo[1]);
        }
        
        // 3. 没有任何有效的配置
        return new ProviderModelResult(false, "", "", 
            "没有找到任何有效的Provider配置，请检查API密钥设置");
    }
    
    /**
     * 异步检测所有provider的健康状态
     */
    public CompletableFuture<Map<String, ProviderHealthChecker.HealthStatus>> checkAllProvidersHealth() {
        return healthChecker.checkAllProviders(providers);
    }

    /**
     * 异步检测单个provider的健康状态
     */
    public CompletableFuture<ProviderHealthChecker.HealthStatus> checkProviderHealth(String providerName) {
        Optional<Provider> provider = findProvider(providerName);
        if (provider.isPresent()) {
            return healthChecker.checkProviderHealth(provider.get());
        }
        return CompletableFuture.completedFuture(
            new ProviderHealthChecker.HealthStatus(false, "Provider不存在",
                ProviderHealthChecker.HealthStatus.ErrorType.CONFIG_ERROR,
                java.time.LocalDateTime.now())
        );
    }

    /**
     * 获取缓存的健康状态
     */
    public ProviderHealthChecker.HealthStatus getCachedProviderHealth(String providerName) {
        return healthChecker.getCachedHealth(providerName);
    }

    /**
     * 清除健康检查缓存
     */
    public void clearHealthCache() {
        healthChecker.clearAllCache();
    }

    /**
     * 获取配置状态报告
     */
    public ConfigurationReport getConfigurationReport() {
        int totalProviders = providers.size();
        List<Provider> validProviders = getValidProviders();
        int validCount = validProviders.size();

        StringBuilder report = new StringBuilder();
        report.append(String.format("Provider配置状态: %d/%d 有效\n", validCount, totalProviders));

        if (validCount == 0) {
            report.append("⚠️ 没有有效的Provider配置，请设置API密钥\n");
            report.append("无效的Provider列表:\n");
            for (Provider provider : providers) {
                String reason = getInvalidReason(provider);
                report.append(String.format("  - %s: %s\n", provider.getName(), reason));
            }
        } else {
            report.append("✅ 有效的Provider列表:\n");
            for (Provider provider : validProviders) {
                ProviderHealthChecker.HealthStatus health = getCachedProviderHealth(provider.getName());
                String healthStatus = "";
                if (health != null) {
                    healthStatus = health.isHealthy() ? " (在线)" : " (离线: " + health.getMessage() + ")";
                }
                report.append(String.format("  - %s: %d个模型可用%s\n",
                    provider.getName(), provider.getModels().size(), healthStatus));
            }

            if (validCount < totalProviders) {
                report.append("⚠️ 无效的Provider列表:\n");
                for (Provider provider : providers) {
                    if (!isProviderValid(provider)) {
                        String reason = getInvalidReason(provider);
                        report.append(String.format("  - %s: %s\n", provider.getName(), reason));
                    }
                }
            }
        }

        return new ConfigurationReport(validCount > 0, report.toString(), validProviders);
    }

    /**
     * 获取包含健康状态的详细配置报告
     */
    public CompletableFuture<ConfigurationReport> getDetailedConfigurationReport() {
        return checkAllProvidersHealth().thenApply(healthMap -> {
            int totalProviders = providers.size();
            List<Provider> validProviders = getValidProviders();
            int validCount = validProviders.size();

            StringBuilder report = new StringBuilder();
            report.append(String.format("Provider配置状态: %d/%d 有效\n", validCount, totalProviders));

            if (validCount == 0) {
                report.append("⚠️ 没有有效的Provider配置，请设置API密钥\n");
                report.append("无效的Provider列表:\n");
                for (Provider provider : providers) {
                    String reason = getInvalidReason(provider);
                    report.append(String.format("  - %s: %s\n", provider.getName(), reason));
                }
            } else {
                report.append("✅ Provider状态列表:\n");
                for (Provider provider : providers) {
                    if (isProviderValid(provider)) {
                        ProviderHealthChecker.HealthStatus health = healthMap.get(provider.getName());
                        String status = "未检测";
                        String lastCheck = "";

                        if (health != null) {
                            status = health.isHealthy() ? "🟢 在线" : "🔴 离线";
                            lastCheck = " (检测时间: " + health.getFormattedCheckTime() + ")";
                            if (!health.isHealthy()) {
                                lastCheck += " - " + health.getMessage();
                            }
                        }

                        report.append(String.format("  - %s: %s%s\n",
                            provider.getName(), status, lastCheck));
                    } else {
                        String reason = getInvalidReason(provider);
                        report.append(String.format("  - %s: ⚠️ 配置无效 - %s\n", provider.getName(), reason));
                    }
                }
            }

            return new ConfigurationReport(validCount > 0, report.toString(), validProviders);
        });
    }
    
    /**
     * 获取Provider无效的原因
     */
    private String getInvalidReason(Provider provider) {
        if (provider == null) {
            return "Provider为空";
        }
        
        if (provider.getName() == null || provider.getName().trim().isEmpty()) {
            return "名称为空";
        }
        
        if (provider.getApiBaseUrl() == null || provider.getApiBaseUrl().trim().isEmpty()) {
            return "API基础URL为空";
        }
        
        if (provider.getApiKey() == null || provider.getApiKey().trim().isEmpty()) {
            return "API密钥为空";
        }
        
        if (ConfigDefaults.isPlaceholderApiKey(provider.getApiKey())) {
            return "API密钥为占位符，需要设置真实密钥";
        }
        
        if (provider.getModels() == null || provider.getModels().isEmpty()) {
            return "模型列表为空";
        }
        
        return "未知原因";
    }
    
    /**
     * Provider和Model修复结果
     */
    public static class ProviderModelResult {
        private final boolean success;
        private final String providerName;
        private final String modelName;
        private final String message;
        
        public ProviderModelResult(boolean success, String providerName, String modelName, String message) {
            this.success = success;
            this.providerName = providerName;
            this.modelName = modelName;
            this.message = message;
        }
        
        public boolean isSuccess() { return success; }
        public String getProviderName() { return providerName; }
        public String getModelName() { return modelName; }
        public String getMessage() { return message; }
    }
    
    /**
     * 配置报告
     */
    public static class ConfigurationReport {
        private final boolean hasValidProviders;
        private final String reportText;
        private final List<Provider> validProviders;
        
        public ConfigurationReport(boolean hasValidProviders, String reportText, List<Provider> validProviders) {
            this.hasValidProviders = hasValidProviders;
            this.reportText = reportText;
            this.validProviders = validProviders;
        }
        
        public boolean hasValidProviders() { return hasValidProviders; }
        public String getReportText() { return reportText; }
        public List<Provider> getValidProviders() { return validProviders; }
    }
}
