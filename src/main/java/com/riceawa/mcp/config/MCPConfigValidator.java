package com.riceawa.mcp.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * MCP配置验证和诊断工具
 * 提供配置有效性检查、错误诊断和修复建议
 */
public class MCPConfigValidator {
    
    /**
     * 配置验证结果
     */
    public static class ValidationResult {
        private final boolean valid;
        private final List<ValidationIssue> issues;
        private final List<String> suggestions;
        
        public ValidationResult(boolean valid, List<ValidationIssue> issues, List<String> suggestions) {
            this.valid = valid;
            this.issues = issues != null ? new ArrayList<>(issues) : new ArrayList<>();
            this.suggestions = suggestions != null ? new ArrayList<>(suggestions) : new ArrayList<>();
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public List<ValidationIssue> getIssues() {
            return Collections.unmodifiableList(new ArrayList<>(issues));
        }
        
        public List<String> getSuggestions() {
            return Collections.unmodifiableList(new ArrayList<>(suggestions));
        }
        
        public boolean hasErrors() {
            return issues.stream().anyMatch(issue -> issue.getSeverity() == ValidationSeverity.ERROR);
        }
        
        public boolean hasWarnings() {
            return issues.stream().anyMatch(issue -> issue.getSeverity() == ValidationSeverity.WARNING);
        }
        
        public List<ValidationIssue> getErrors() {
            return Collections.unmodifiableList(issues.stream()
                    .filter(issue -> issue.getSeverity() == ValidationSeverity.ERROR)
                    .collect(Collectors.toList()));
        }
        
        public List<ValidationIssue> getWarnings() {
            return Collections.unmodifiableList(issues.stream()
                    .filter(issue -> issue.getSeverity() == ValidationSeverity.WARNING)
                    .collect(Collectors.toList()));
        }
    }
    
    /**
     * 验证问题
     */
    public static class ValidationIssue {
        private final ValidationSeverity severity;
        private final String component;
        private final String message;
        private final String suggestion;
        
        public ValidationIssue(ValidationSeverity severity, String component, String message, String suggestion) {
            this.severity = severity;
            this.component = component;
            this.message = message;
            this.suggestion = suggestion;
        }
        
        public ValidationSeverity getSeverity() {
            return severity;
        }
        
        public String getComponent() {
            return component;
        }
        
        public String getMessage() {
            return message;
        }
        
        public String getSuggestion() {
            return suggestion;
        }
        
        @Override
        public String toString() {
            return String.format("[%s] %s: %s", severity, component, message);
        }
    }
    
    /**
     * 验证严重程度
     */
    public enum ValidationSeverity {
        ERROR,   // 阻止功能正常工作的错误
        WARNING, // 可能影响功能的警告
        INFO     // 信息性提示
    }
    
    /**
     * 验证MCP配置
     */
    public static ValidationResult validateConfig(MCPConfig config) {
        List<ValidationIssue> issues = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();
        
        if (config == null) {
            issues.add(new ValidationIssue(
                ValidationSeverity.ERROR,
                "MCPConfig",
                "MCP配置为null",
                "请创建有效的MCP配置对象"
            ));
            return new ValidationResult(false, issues, suggestions);
        }
        
        // 验证基础配置
        validateBasicConfig(config, issues, suggestions);
        
        // 验证服务器配置
        validateServerConfigs(config, issues, suggestions);
        
        // 验证超时和重试配置
        validateTimeoutAndRetryConfig(config, issues, suggestions);
        
        // 验证缓存配置
        validateCacheConfig(config, issues, suggestions);
        
        // 验证权限配置
        validatePermissionConfig(config, issues, suggestions);
        
        // 生成总体建议
        generateOverallSuggestions(config, suggestions);
        
        boolean isValid = issues.stream().noneMatch(issue -> issue.getSeverity() == ValidationSeverity.ERROR);
        
        return new ValidationResult(isValid, issues, suggestions);
    }
    
    /**
     * 验证基础配置
     */
    private static void validateBasicConfig(MCPConfig config, List<ValidationIssue> issues, List<String> suggestions) {
        List<MCPServerConfig> allServers = new ArrayList<>(config.getMcpServers().values());
        
        // 检查是否启用但没有服务器配置
        if (config.isEnabled() && allServers.isEmpty()) {
            issues.add(new ValidationIssue(
                ValidationSeverity.ERROR,
                "基础配置",
                "MCP功能已启用但没有配置任何服务器",
                "请添加至少一个MCP服务器配置，或禁用MCP功能"
            ));
        }
        
        // 检查是否有服务器配置但功能未启用
        if (!config.isEnabled() && !allServers.isEmpty()) {
            issues.add(new ValidationIssue(
                ValidationSeverity.WARNING,
                "基础配置",
                "已配置MCP服务器但功能未启用",
                "考虑启用MCP功能以使用已配置的服务器"
            ));
        }
        
        // 检查是否有启用的服务器
        if (config.isEnabled() && !config.hasEnabledServers()) {
            issues.add(new ValidationIssue(
                ValidationSeverity.WARNING,
                "基础配置",
                "MCP功能已启用但没有启用的服务器",
                "请启用至少一个MCP服务器配置"
            ));
        }
    }
    
    /**
     * 验证服务器配置
     */
    private static void validateServerConfigs(MCPConfig config, List<ValidationIssue> issues, List<String> suggestions) {
        List<MCPServerConfig> allServers = new ArrayList<>(config.getMcpServers().values());
        
        Set<String> serverNames = allServers.stream()
                .map(MCPServerConfig::getName)
                .collect(Collectors.toSet());
        
        // 检查服务器名称重复
        if (serverNames.size() != allServers.size()) {
            issues.add(new ValidationIssue(
                ValidationSeverity.ERROR,
                "服务器配置",
                "存在重复的服务器名称",
                "请确保每个MCP服务器都有唯一的名称"
            ));
        }
        
        // 验证每个服务器配置
        for (MCPServerConfig serverConfig : allServers) {
            validateServerConfig(serverConfig, issues, suggestions);
        }
        
        // 检查是否有推荐的服务器类型分布
        long stdioCount = allServers.stream()
                .filter(MCPServerConfig::isStdioType)
                .count();
        long sseCount = allServers.stream()
                .filter(MCPServerConfig::isSseType)
                .count();
        
        if (stdioCount > 0 && sseCount == 0) {
            suggestions.add("考虑添加SSE类型的MCP服务器以获得更好的实时通信能力");
        }
    }
    
    /**
     * 验证单个服务器配置
     */
    private static void validateServerConfig(MCPServerConfig serverConfig, List<ValidationIssue> issues, List<String> suggestions) {
        String serverName = serverConfig.getName() != null ? serverConfig.getName() : "未命名服务器";
        
        // 使用服务器自身的验证方法
        if (!serverConfig.isValid()) {
            issues.add(new ValidationIssue(
                ValidationSeverity.ERROR,
                "服务器配置 - " + serverName,
                "服务器配置无效",
                "请检查服务器名称、类型和连接参数"
            ));
            return;
        }
        
        // 验证STDIO类型特定配置
        if (serverConfig.isStdioType()) {
            validateStdioConfig(serverConfig, issues, suggestions);
        }
        
        // 验证SSE类型特定配置
        if (serverConfig.isSseType()) {
            validateSseConfig(serverConfig, issues, suggestions);
        }
        
        // 验证权限配置
        validateServerPermissions(serverConfig, issues, suggestions);
    }
    
    /**
     * 验证STDIO配置
     */
    private static void validateStdioConfig(MCPServerConfig serverConfig, List<ValidationIssue> issues, List<String> suggestions) {
        String serverName = serverConfig.getName();
        
        // 检查命令是否为空
        if (serverConfig.getCommand() == null || serverConfig.getCommand().trim().isEmpty()) {
            issues.add(new ValidationIssue(
                ValidationSeverity.ERROR,
                "STDIO配置 - " + serverName,
                "STDIO服务器命令为空",
                "请指定有效的启动命令"
            ));
        }
        
        // 检查是否使用了常见的MCP工具
        String command = serverConfig.getCommand();
        if (command != null && !command.contains("uvx") && !command.contains("npx") && !command.contains("python")) {
            issues.add(new ValidationIssue(
                ValidationSeverity.INFO,
                "STDIO配置 - " + serverName,
                "使用了非标准的MCP服务器启动命令",
                "确保命令能够正确启动MCP服务器"
            ));
        }
        
        // 检查环境变量配置
        if (serverConfig.getEnv().isEmpty()) {
            suggestions.add("考虑为服务器 " + serverName + " 配置必要的环境变量");
        }
    }
    
    /**
     * 验证SSE配置
     */
    private static void validateSseConfig(MCPServerConfig serverConfig, List<ValidationIssue> issues, List<String> suggestions) {
        String serverName = serverConfig.getName();
        String url = serverConfig.getUrl();
        
        // 检查URL格式
        if (url == null || url.trim().isEmpty()) {
            issues.add(new ValidationIssue(
                ValidationSeverity.ERROR,
                "SSE配置 - " + serverName,
                "SSE服务器URL为空",
                "请指定有效的SSE服务器URL"
            ));
        } else if (!url.startsWith("http://") && !url.startsWith("https://") && !url.startsWith("localhost")) {
            issues.add(new ValidationIssue(
                ValidationSeverity.ERROR,
                "SSE配置 - " + serverName,
                "SSE服务器URL格式无效",
                "URL必须以http://、https://开头，或使用localhost"
            ));
        } else if (url.startsWith("http://")) {
            issues.add(new ValidationIssue(
                ValidationSeverity.WARNING,
                "SSE配置 - " + serverName,
                "使用了不安全的HTTP连接",
                "建议使用HTTPS连接以确保安全性"
            ));
        }
    }
    
    /**
     * 验证服务器权限配置
     */
    private static void validateServerPermissions(MCPServerConfig serverConfig, List<ValidationIssue> issues, List<String> suggestions) {
        String serverName = serverConfig.getName();
        
        // 检查权限策略
        String policy = serverConfig.getToolPermissionPolicy();
        if (policy == null || policy.trim().isEmpty()) {
            issues.add(new ValidationIssue(
                ValidationSeverity.WARNING,
                "权限配置 - " + serverName,
                "工具权限策略未设置",
                "建议明确设置工具权限策略"
            ));
        }
        
        // 验证autoApprove配置
        if (!serverConfig.getAutoApprove().isEmpty()) {
            suggestions.add("服务器 " + serverName + " 配置了自动批准工具，请确保这些工具是安全的");
        }
        
        // 检查是否配置了工具和资源限制
        if (serverConfig.getAllowedTools().isEmpty() && serverConfig.getAllowedResources().isEmpty()) {
            suggestions.add("考虑为服务器 " + serverName + " 配置工具和资源访问限制以提高安全性");
        }
    }
    
    /**
     * 验证超时和重试配置
     */
    private static void validateTimeoutAndRetryConfig(MCPConfig config, List<ValidationIssue> issues, List<String> suggestions) {
        // 检查连接超时
        if (config.getConnectionTimeoutMs() < 5000) {
            issues.add(new ValidationIssue(
                ValidationSeverity.WARNING,
                "超时配置",
                "连接超时时间过短（" + config.getConnectionTimeoutMs() + "ms）",
                "建议设置至少5秒的连接超时时间"
            ));
        } else if (config.getConnectionTimeoutMs() > 60000) {
            issues.add(new ValidationIssue(
                ValidationSeverity.WARNING,
                "超时配置",
                "连接超时时间过长（" + config.getConnectionTimeoutMs() + "ms）",
                "过长的超时时间可能影响用户体验"
            ));
        }
        
        // 检查请求超时
        if (config.getRequestTimeoutMs() < 3000) {
            issues.add(new ValidationIssue(
                ValidationSeverity.WARNING,
                "超时配置",
                "请求超时时间过短（" + config.getRequestTimeoutMs() + "ms）",
                "建议设置至少3秒的请求超时时间"
            ));
        }
        
        // 检查重试次数
        if (config.getMaxRetries() > 5) {
            issues.add(new ValidationIssue(
                ValidationSeverity.WARNING,
                "重试配置",
                "最大重试次数过多（" + config.getMaxRetries() + "）",
                "过多的重试可能导致长时间等待"
            ));
        }
    }
    
    /**
     * 验证缓存配置
     */
    private static void validateCacheConfig(MCPConfig config, List<ValidationIssue> issues, List<String> suggestions) {
        if (config.isEnableResourceCaching()) {
            // 检查缓存大小
            if (config.getResourceCacheSize() < 10) {
                issues.add(new ValidationIssue(
                    ValidationSeverity.WARNING,
                    "缓存配置",
                    "资源缓存大小过小（" + config.getResourceCacheSize() + "）",
                    "建议设置至少10个条目的缓存大小"
                ));
            } else if (config.getResourceCacheSize() > 500) {
                issues.add(new ValidationIssue(
                    ValidationSeverity.WARNING,
                    "缓存配置",
                    "资源缓存大小过大（" + config.getResourceCacheSize() + "）",
                    "过大的缓存可能占用过多内存"
                ));
            }
            
            // 检查TTL设置
            if (config.getResourceCacheTtlMinutes() < 5) {
                issues.add(new ValidationIssue(
                    ValidationSeverity.WARNING,
                    "缓存配置",
                    "资源缓存TTL过短（" + config.getResourceCacheTtlMinutes() + "分钟）",
                    "过短的TTL可能导致频繁的缓存失效"
                ));
            }
        }
    }
    
    /**
     * 验证权限配置
     */
    private static void validatePermissionConfig(MCPConfig config, List<ValidationIssue> issues, List<String> suggestions) {
        String policy = config.getDefaultPermissionPolicy();
        
        if (policy == null || policy.trim().isEmpty()) {
            issues.add(new ValidationIssue(
                ValidationSeverity.WARNING,
                "权限配置",
                "默认权限策略未设置",
                "建议设置明确的默认权限策略"
            ));
        } else if ("ALLOW_ALL".equals(policy)) {
            issues.add(new ValidationIssue(
                ValidationSeverity.WARNING,
                "权限配置",
                "使用了宽松的默认权限策略（ALLOW_ALL）",
                "考虑使用更严格的权限策略以提高安全性"
            ));
        }
    }
    
    /**
     * 生成总体建议
     */
    private static void generateOverallSuggestions(MCPConfig config, List<String> suggestions) {
        if (config.isEnabled()) {
            suggestions.add("定期检查MCP服务器的连接状态和性能");
            suggestions.add("监控MCP工具的使用情况和错误日志");
            
            if (config.getEnabledServers().size() > 3) {
                suggestions.add("考虑优化服务器数量以避免过多的连接开销");
            }
        }
        
        if (!config.isEnableToolChangeNotifications() && !config.isEnableResourceChangeNotifications()) {
            suggestions.add("考虑启用工具和资源变化通知以获得更好的动态更新支持");
        }
    }
    
    /**
     * 生成配置状态报告
     */
    public static String generateStatusReport(MCPConfig config) {
        if (config == null) {
            return "MCP配置状态报告\n==================\n状态: 配置未初始化\n";
        }
        
        StringBuilder report = new StringBuilder();
        report.append("MCP配置状态报告\n");
        report.append("==================\n");
        
        // 基础状态
        report.append("功能状态: ").append(config.isEnabled() ? "已启用" : "已禁用").append("\n");
        
        List<MCPServerConfig> allServers = new ArrayList<>(config.getMcpServers().values());
        
        report.append("服务器总数: ").append(allServers.size()).append("\n");
        report.append("启用的服务器: ").append(config.getEnabledServers().size()).append("\n");
        
        // 服务器详情
        if (!allServers.isEmpty()) {
            report.append("\n服务器配置:\n");
            for (MCPServerConfig server : allServers) {
                report.append("  - ").append(server.getName())
                      .append(" (").append(server.getType().toUpperCase()).append(")")
                      .append(" - ").append(server.isEnabled() ? "启用" : "禁用")
                      .append(" - ").append(server.isValid() ? "有效" : "无效");
                
                // 显示autoApprove信息
                if (!server.getAutoApprove().isEmpty()) {
                    report.append(" - 自动批准: ").append(server.getAutoApprove().size()).append("个工具");
                }
                report.append("\n");
            }
        }
        
        // 配置参数
        report.append("\n配置参数:\n");
        report.append("  连接超时: ").append(config.getConnectionTimeoutMs()).append("ms\n");
        report.append("  请求超时: ").append(config.getRequestTimeoutMs()).append("ms\n");
        report.append("  最大重试: ").append(config.getMaxRetries()).append("次\n");
        report.append("  资源缓存: ").append(config.isEnableResourceCaching() ? "启用" : "禁用").append("\n");
        
        if (config.isEnableResourceCaching()) {
            report.append("  缓存大小: ").append(config.getResourceCacheSize()).append("\n");
            report.append("  缓存TTL: ").append(config.getResourceCacheTtlMinutes()).append("分钟\n");
        }
        
        // 权限配置
        report.append("  默认权限策略: ").append(config.getDefaultPermissionPolicy()).append("\n");
        report.append("  工具变化通知: ").append(config.isEnableToolChangeNotifications() ? "启用" : "禁用").append("\n");
        report.append("  资源变化通知: ").append(config.isEnableResourceChangeNotifications() ? "启用" : "禁用").append("\n");
        
        // 验证结果
        ValidationResult validation = validateConfig(config);
        report.append("\n配置验证:\n");
        report.append("  整体状态: ").append(validation.isValid() ? "有效" : "无效").append("\n");
        report.append("  错误数量: ").append(validation.getErrors().size()).append("\n");
        report.append("  警告数量: ").append(validation.getWarnings().size()).append("\n");
        
        if (!validation.getErrors().isEmpty()) {
            report.append("\n错误详情:\n");
            for (ValidationIssue error : validation.getErrors()) {
                report.append("  - ").append(error.getMessage()).append("\n");
            }
        }
        
        if (!validation.getWarnings().isEmpty()) {
            report.append("\n警告详情:\n");
            for (ValidationIssue warning : validation.getWarnings()) {
                report.append("  - ").append(warning.getMessage()).append("\n");
            }
        }
        
        if (!validation.getSuggestions().isEmpty()) {
            report.append("\n建议:\n");
            for (String suggestion : validation.getSuggestions()) {
                report.append("  - ").append(suggestion).append("\n");
            }
        }
        
        return report.toString();
    }
    
    /**
     * 自动修复配置问题
     */
    public static MCPConfig autoFixConfig(MCPConfig config) {
        if (config == null) {
            return MCPConfig.createDefault();
        }
        
        // 创建配置副本进行修复
        MCPConfig fixedConfig = MCPConfig.createDefault();
        
        // 复制基础设置
        fixedConfig.setEnabled(config.isEnabled());
        
        // 修复超时设置
        fixedConfig.setConnectionTimeoutMs(Math.max(5000, Math.min(60000, config.getConnectionTimeoutMs())));
        fixedConfig.setRequestTimeoutMs(Math.max(3000, Math.min(30000, config.getRequestTimeoutMs())));
        fixedConfig.setMaxRetries(Math.max(0, Math.min(5, config.getMaxRetries())));
        
        // 修复缓存设置
        fixedConfig.setEnableResourceCaching(config.isEnableResourceCaching());
        fixedConfig.setResourceCacheSize(Math.max(10, Math.min(500, config.getResourceCacheSize())));
        fixedConfig.setResourceCacheTtlMinutes(Math.max(5, config.getResourceCacheTtlMinutes()));
        
        // 修复权限设置
        String policy = config.getDefaultPermissionPolicy();
        if (policy == null || policy.trim().isEmpty()) {
            fixedConfig.setDefaultPermissionPolicy("OP_ONLY");
        } else {
            fixedConfig.setDefaultPermissionPolicy(policy);
        }
        
        // 复制通知设置
        fixedConfig.setEnableToolChangeNotifications(config.isEnableToolChangeNotifications());
        fixedConfig.setEnableResourceChangeNotifications(config.isEnableResourceChangeNotifications());
        
        // 修复服务器配置
        for (MCPServerConfig serverConfig : config.getMcpServers().values()) {
            if (serverConfig.isValid()) {
                fixedConfig.addServer(serverConfig);
            }
        }
        
        return fixedConfig;
    }
}