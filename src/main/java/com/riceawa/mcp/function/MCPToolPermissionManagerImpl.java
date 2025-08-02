package com.riceawa.mcp.function;

import com.riceawa.mcp.model.MCPTool;
import net.minecraft.entity.player.PlayerEntity;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;
import java.util.regex.Pattern;

/**
 * MCP工具权限管理器实现
 * 负责管理MCP工具的访问权限，支持多种权限策略和自定义规则
 */
public class MCPToolPermissionManagerImpl implements MCPToolPermissionManager {
    
    // 工具权限策略映射
    private final Map<String, PermissionPolicy> toolPolicies = new ConcurrentHashMap<>();
    
    // 自定义权限规则
    private final List<CustomPermissionRule> customRules = new ArrayList<>();
    
    // 客户端权限策略
    private final Map<String, PermissionPolicy> clientPolicies = new ConcurrentHashMap<>();
    
    // 默认权限策略
    private PermissionPolicy defaultPolicy = PermissionPolicy.ALLOW_ALL;
    
    // 是否启用严格模式（严格模式下，未明确配置的工具默认拒绝访问）
    private boolean strictMode = false;
    
    // 权限检查缓存（玩家UUID + 工具名称 -> 权限结果）
    private final Map<String, Boolean> permissionCache = new ConcurrentHashMap<>();
    
    // 缓存过期时间（毫秒）
    private static final long CACHE_EXPIRE_TIME = 60000; // 1分钟
    
    // 缓存时间戳
    private final Map<String, Long> cacheTimestamps = new ConcurrentHashMap<>();
    
    @Override
    public boolean checkPermission(PlayerEntity player, MCPTool tool) {
        if (player == null || tool == null) {
            return false;
        }
        
        String cacheKey = player.getUuidAsString() + ":" + tool.getFullName();
        
        // 检查缓存
        Boolean cachedResult = getCachedPermission(cacheKey);
        if (cachedResult != null) {
            return cachedResult;
        }
        
        // 执行权限检查
        boolean hasPermission = performPermissionCheck(player, tool);
        
        // 缓存结果
        cachePermission(cacheKey, hasPermission);
        
        return hasPermission;
    }
    
    @Override
    public void setPermissionPolicy(String toolName, PermissionPolicy policy) {
        if (toolName != null && policy != null) {
            toolPolicies.put(toolName, policy);
            // 清除相关缓存
            clearToolCache(toolName);
        }
    }
    
    @Override
    public PermissionPolicy getPermissionPolicy(String toolName) {
        return toolPolicies.getOrDefault(toolName, defaultPolicy);
    }
    
    /**
     * 设置客户端权限策略
     */
    public void setClientPermissionPolicy(String clientName, PermissionPolicy policy) {
        if (clientName != null && policy != null) {
            clientPolicies.put(clientName, policy);
            // 清除相关缓存
            clearClientCache(clientName);
        }
    }
    
    /**
     * 获取客户端权限策略
     */
    public PermissionPolicy getClientPermissionPolicy(String clientName) {
        return clientPolicies.get(clientName);
    }
    
    /**
     * 设置默认权限策略
     */
    public void setDefaultPermissionPolicy(PermissionPolicy policy) {
        if (policy != null) {
            this.defaultPolicy = policy;
            clearAllCache();
        }
    }
    
    /**
     * 获取默认权限策略
     */
    public PermissionPolicy getDefaultPermissionPolicy() {
        return defaultPolicy;
    }
    
    /**
     * 设置严格模式
     */
    public void setStrictMode(boolean strictMode) {
        this.strictMode = strictMode;
        clearAllCache();
    }
    
    /**
     * 检查是否为严格模式
     */
    public boolean isStrictMode() {
        return strictMode;
    }
    
    /**
     * 添加自定义权限规则
     */
    public void addCustomRule(CustomPermissionRule rule) {
        if (rule != null) {
            synchronized (customRules) {
                customRules.add(rule);
                // 按优先级排序（高优先级在前）
                customRules.sort((r1, r2) -> Integer.compare(r2.getPriority(), r1.getPriority()));
            }
            clearAllCache();
        }
    }
    
    /**
     * 移除自定义权限规则
     */
    public void removeCustomRule(CustomPermissionRule rule) {
        if (rule != null) {
            synchronized (customRules) {
                customRules.remove(rule);
            }
            clearAllCache();
        }
    }
    
    /**
     * 获取所有自定义权限规则
     */
    public List<CustomPermissionRule> getCustomRules() {
        synchronized (customRules) {
            return new ArrayList<>(customRules);
        }
    }
    
    /**
     * 清除权限缓存
     */
    public void clearPermissionCache() {
        permissionCache.clear();
        cacheTimestamps.clear();
    }
    
    /**
     * 获取权限检查统计信息
     */
    public PermissionStatistics getStatistics() {
        return new PermissionStatistics(
            toolPolicies.size(),
            clientPolicies.size(),
            customRules.size(),
            permissionCache.size()
        );
    }
    
    // ==================== 私有方法 ====================
    
    /**
     * 执行权限检查
     */
    private boolean performPermissionCheck(PlayerEntity player, MCPTool tool) {
        try {
            // 1. 检查自定义规则（最高优先级）
            for (CustomPermissionRule rule : customRules) {
                if (rule.isApplicable(player, tool)) {
                    return rule.checkPermission(player, tool);
                }
            }
            
            // 2. 检查工具特定策略
            String toolName = tool.getFullName();
            PermissionPolicy toolPolicy = toolPolicies.get(toolName);
            if (toolPolicy != null) {
                return evaluatePolicy(toolPolicy, player, tool);
            }
            
            // 3. 检查客户端策略
            String clientName = tool.getClientName();
            if (clientName != null) {
                PermissionPolicy clientPolicy = clientPolicies.get(clientName);
                if (clientPolicy != null) {
                    return evaluatePolicy(clientPolicy, player, tool);
                }
            }
            
            // 4. 使用默认策略
            if (strictMode && defaultPolicy == PermissionPolicy.ALLOW_ALL) {
                // 严格模式下，未配置的工具默认拒绝
                return false;
            }
            
            return evaluatePolicy(defaultPolicy, player, tool);
            
        } catch (Exception e) {
            System.err.println("权限检查失败: " + e.getMessage());
            // 出错时默认拒绝访问
            return false;
        }
    }
    
    /**
     * 评估权限策略
     */
    private boolean evaluatePolicy(PermissionPolicy policy, PlayerEntity player, MCPTool tool) {
        switch (policy) {
            case ALLOW_ALL:
                return true;
                
            case DENY_ALL:
                return false;
                
            case OP_ONLY:
                return player.hasPermissionLevel(2); // OP权限
                
            case CUSTOM:
                // 自定义策略在自定义规则中处理
                return false;
                
            default:
                return false;
        }
    }
    
    /**
     * 获取缓存的权限结果
     */
    private Boolean getCachedPermission(String cacheKey) {
        Long timestamp = cacheTimestamps.get(cacheKey);
        if (timestamp == null) {
            return null;
        }
        
        // 检查是否过期
        if (System.currentTimeMillis() - timestamp > CACHE_EXPIRE_TIME) {
            permissionCache.remove(cacheKey);
            cacheTimestamps.remove(cacheKey);
            return null;
        }
        
        return permissionCache.get(cacheKey);
    }
    
    /**
     * 缓存权限结果
     */
    private void cachePermission(String cacheKey, boolean hasPermission) {
        permissionCache.put(cacheKey, hasPermission);
        cacheTimestamps.put(cacheKey, System.currentTimeMillis());
        
        // 定期清理过期缓存
        if (permissionCache.size() % 100 == 0) {
            cleanExpiredCache();
        }
    }
    
    /**
     * 清理过期缓存
     */
    private void cleanExpiredCache() {
        long currentTime = System.currentTimeMillis();
        List<String> expiredKeys = new ArrayList<>();
        
        for (Map.Entry<String, Long> entry : cacheTimestamps.entrySet()) {
            if (currentTime - entry.getValue() > CACHE_EXPIRE_TIME) {
                expiredKeys.add(entry.getKey());
            }
        }
        
        for (String key : expiredKeys) {
            permissionCache.remove(key);
            cacheTimestamps.remove(key);
        }
    }
    
    /**
     * 清除工具相关缓存
     */
    private void clearToolCache(String toolName) {
        List<String> keysToRemove = new ArrayList<>();
        for (String key : permissionCache.keySet()) {
            if (key.endsWith(":" + toolName)) {
                keysToRemove.add(key);
            }
        }
        
        for (String key : keysToRemove) {
            permissionCache.remove(key);
            cacheTimestamps.remove(key);
        }
    }
    
    /**
     * 清除客户端相关缓存
     */
    private void clearClientCache(String clientName) {
        List<String> keysToRemove = new ArrayList<>();
        for (String key : permissionCache.keySet()) {
            if (key.contains("mcp_" + clientName + "_")) {
                keysToRemove.add(key);
            }
        }
        
        for (String key : keysToRemove) {
            permissionCache.remove(key);
            cacheTimestamps.remove(key);
        }
    }
    
    /**
     * 清除所有缓存
     */
    private void clearAllCache() {
        permissionCache.clear();
        cacheTimestamps.clear();
    }
    
    // ==================== 内部类 ====================
    
    /**
     * 自定义权限规则接口
     */
    public interface CustomPermissionRule {
        
        /**
         * 检查规则是否适用于指定的玩家和工具
         */
        boolean isApplicable(PlayerEntity player, MCPTool tool);
        
        /**
         * 检查权限
         */
        boolean checkPermission(PlayerEntity player, MCPTool tool);
        
        /**
         * 获取规则优先级（数字越大优先级越高）
         */
        int getPriority();
        
        /**
         * 获取规则名称
         */
        String getName();
        
        /**
         * 获取规则描述
         */
        String getDescription();
    }
    
    /**
     * 基于模式匹配的权限规则
     */
    public static class PatternBasedRule implements CustomPermissionRule {
        private final String name;
        private final String description;
        private final Pattern toolPattern;
        private final Pattern clientPattern;
        private final BiPredicate<PlayerEntity, MCPTool> permissionChecker;
        private final int priority;
        
        public PatternBasedRule(String name, String description, String toolPattern, 
                               BiPredicate<PlayerEntity, MCPTool> permissionChecker, int priority) {
            this(name, description, toolPattern, null, permissionChecker, priority);
        }
        
        public PatternBasedRule(String name, String description, String toolPattern, 
                               String clientPattern, BiPredicate<PlayerEntity, MCPTool> permissionChecker, int priority) {
            this.name = name;
            this.description = description;
            this.toolPattern = toolPattern != null ? Pattern.compile(toolPattern) : null;
            this.clientPattern = clientPattern != null ? Pattern.compile(clientPattern) : null;
            this.permissionChecker = permissionChecker;
            this.priority = priority;
        }
        
        @Override
        public boolean isApplicable(PlayerEntity player, MCPTool tool) {
            if (toolPattern != null && !toolPattern.matcher(tool.getName()).matches()) {
                return false;
            }
            
            if (clientPattern != null && tool.getClientName() != null 
                && !clientPattern.matcher(tool.getClientName()).matches()) {
                return false;
            }
            
            return true;
        }
        
        @Override
        public boolean checkPermission(PlayerEntity player, MCPTool tool) {
            return permissionChecker.test(player, tool);
        }
        
        @Override
        public int getPriority() {
            return priority;
        }
        
        @Override
        public String getName() {
            return name;
        }
        
        @Override
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * 权限统计信息
     */
    public static class PermissionStatistics {
        private final int toolPolicyCount;
        private final int clientPolicyCount;
        private final int customRuleCount;
        private final int cacheSize;
        
        public PermissionStatistics(int toolPolicyCount, int clientPolicyCount, 
                                  int customRuleCount, int cacheSize) {
            this.toolPolicyCount = toolPolicyCount;
            this.clientPolicyCount = clientPolicyCount;
            this.customRuleCount = customRuleCount;
            this.cacheSize = cacheSize;
        }
        
        public int getToolPolicyCount() { return toolPolicyCount; }
        public int getClientPolicyCount() { return clientPolicyCount; }
        public int getCustomRuleCount() { return customRuleCount; }
        public int getCacheSize() { return cacheSize; }
        
        @Override
        public String toString() {
            return String.format("PermissionStatistics{tools=%d, clients=%d, rules=%d, cache=%d}",
                               toolPolicyCount, clientPolicyCount, customRuleCount, cacheSize);
        }
    }
}