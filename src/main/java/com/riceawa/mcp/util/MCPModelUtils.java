package com.riceawa.mcp.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.riceawa.mcp.exception.MCPException;
import com.riceawa.mcp.exception.MCPErrorType;
import com.riceawa.mcp.model.*;

import java.util.*;
import java.util.regex.Pattern;

/**
 * MCP数据模型验证和转换工具类
 * 提供数据模型的验证、转换和清理方法
 */
public class MCPModelUtils {

    // URI格式验证正则表达式
    private static final Pattern URI_PATTERN = Pattern.compile(
        "^[a-zA-Z][a-zA-Z0-9+.-]*://[^\\s]*$"
    );
    
    // 工具名称验证正则表达式 (字母、数字、下划线、连字符)
    private static final Pattern TOOL_NAME_PATTERN = Pattern.compile(
        "^[a-zA-Z][a-zA-Z0-9_-]*$"
    );
    
    // MIME类型验证正则表达式
    private static final Pattern MIME_TYPE_PATTERN = Pattern.compile(
        "^[a-zA-Z][a-zA-Z0-9][a-zA-Z0-9!#$&\\-\\^_]*/" +
        "[a-zA-Z0-9][a-zA-Z0-9!#$&\\-\\^_.]*$"
    );

    // ==================== MCPTool 验证和转换 ====================

    /**
     * 验证MCPTool对象
     */
    public static ValidationResult validateTool(MCPTool tool) {
        ValidationResult result = new ValidationResult();
        
        if (tool == null) {
            result.addError("工具对象不能为null");
            return result;
        }
        
        // 验证名称
        if (!isValidString(tool.getName())) {
            result.addError("工具名称不能为空");
        } else if (!TOOL_NAME_PATTERN.matcher(tool.getName()).matches()) {
            result.addError("工具名称格式无效，只能包含字母、数字、下划线和连字符，且必须以字母开头");
        }
        
        // 验证标题（可选）
        if (tool.getTitle() != null && tool.getTitle().trim().isEmpty()) {
            result.addWarning("工具标题为空字符串，建议设置为null或有效内容");
        }
        
        // 验证描述（可选）
        if (tool.getDescription() != null && tool.getDescription().trim().isEmpty()) {
            result.addWarning("工具描述为空字符串，建议设置为null或有效内容");
        }
        
        // 验证输入Schema（可选）
        if (tool.getInputSchema() != null) {
            ValidationResult schemaResult = validateJsonSchema(tool.getInputSchema(), "输入Schema");
            result.merge(schemaResult);
        }
        
        // 验证输出Schema（可选）
        if (tool.getOutputSchema() != null) {
            ValidationResult schemaResult = validateJsonSchema(tool.getOutputSchema(), "输出Schema");
            result.merge(schemaResult);
        }
        
        // 验证客户端名称（可选）
        if (tool.getClientName() != null && tool.getClientName().trim().isEmpty()) {
            result.addWarning("客户端名称为空字符串，建议设置为null或有效内容");
        }
        
        return result;
    }

    /**
     * 清理MCPTool对象，移除无效数据
     */
    public static MCPTool cleanTool(MCPTool tool) throws MCPException {
        if (tool == null) {
            return null;
        }
        
        MCPTool cleaned = new MCPTool();
        
        // 清理名称
        cleaned.setName(cleanString(tool.getName()));
        
        // 清理标题
        cleaned.setTitle(cleanString(tool.getTitle()));
        
        // 清理描述
        cleaned.setDescription(cleanString(tool.getDescription()));
        
        // 清理Schema
        cleaned.setInputSchema(tool.getInputSchema());
        cleaned.setOutputSchema(tool.getOutputSchema());
        
        // 清理客户端名称
        cleaned.setClientName(cleanString(tool.getClientName()));
        
        // 清理注解
        cleaned.setAnnotations(cleanAnnotations(tool.getAnnotations()));
        
        return cleaned;
    }

    // ==================== MCPToolResult 验证和转换 ====================

    /**
     * 验证MCPToolResult对象
     */
    public static ValidationResult validateToolResult(MCPToolResult result) {
        ValidationResult validationResult = new ValidationResult();
        
        if (result == null) {
            validationResult.addError("工具结果对象不能为null");
            return validationResult;
        }
        
        // 如果是错误结果，验证错误信息
        if (result.isError()) {
            if (!isValidString(result.getErrorMessage())) {
                validationResult.addError("错误结果必须包含错误消息");
            }
        } else {
            // 验证内容列表
            for (MCPContent content : result.getContent()) {
                ValidationResult contentResult = validateContent(content);
                validationResult.merge(contentResult);
            }
        }
        
        // 验证工具名称和客户端名称（可选）
        if (result.getToolName() != null && result.getToolName().trim().isEmpty()) {
            validationResult.addWarning("工具名称为空字符串");
        }
        
        if (result.getClientName() != null && result.getClientName().trim().isEmpty()) {
            validationResult.addWarning("客户端名称为空字符串");
        }
        
        return validationResult;
    }

    // ==================== MCPContent 验证和转换 ====================

    /**
     * 验证MCPContent对象
     */
    public static ValidationResult validateContent(MCPContent content) {
        ValidationResult result = new ValidationResult();
        
        if (content == null) {
            result.addError("内容对象不能为null");
            return result;
        }
        
        // 验证类型
        if (!isValidString(content.getType())) {
            result.addError("内容类型不能为空");
            return result;
        }
        
        String type = content.getType().toLowerCase();
        
        // 根据类型验证对应字段
        switch (type) {
            case "text":
                if (content.getText() == null) {
                    result.addError("文本类型内容必须包含text字段");
                }
                break;
                
            case "image":
            case "audio":
                if (!isValidString(content.getData())) {
                    result.addError(type + "类型内容必须包含data字段");
                }
                if (!isValidString(content.getMimeType())) {
                    result.addError(type + "类型内容必须包含mimeType字段");
                } else if (!MIME_TYPE_PATTERN.matcher(content.getMimeType()).matches()) {
                    result.addError("MIME类型格式无效: " + content.getMimeType());
                }
                break;
                
            case "resource_link":
            case "resource":
                if (!isValidString(content.getUri())) {
                    result.addError(type + "类型内容必须包含uri字段");
                } else if (!isValidUri(content.getUri())) {
                    result.addError("URI格式无效: " + content.getUri());
                }
                break;
                
            default:
                result.addWarning("未知的内容类型: " + type);
        }
        
        return result;
    }

    /**
     * 转换MCPContent到指定类型
     */
    public static MCPContent convertContentType(MCPContent source, String targetType) throws MCPException {
        if (source == null) {
            throw new MCPException(MCPErrorType.INVALID_PARAMETERS, "源内容不能为null");
        }
        
        if (!isValidString(targetType)) {
            throw new MCPException(MCPErrorType.INVALID_PARAMETERS, "目标类型不能为空");
        }
        
        String sourceType = source.getType();
        if (sourceType.equals(targetType)) {
            return source; // 已经是目标类型
        }
        
        MCPContent converted = new MCPContent();
        converted.setType(targetType);
        converted.setAnnotations(source.getAnnotations());
        
        // 执行类型转换
        switch (targetType.toLowerCase()) {
            case "text":
                converted.setText(source.getContentString());
                break;
                
            case "resource_link":
                if (source.getUri() != null) {
                    converted.setUri(source.getUri());
                } else {
                    throw new MCPException(MCPErrorType.INVALID_PARAMETERS, 
                                         "无法转换为resource_link类型：缺少URI");
                }
                break;
                
            default:
                throw new MCPException(MCPErrorType.INVALID_PARAMETERS, 
                                     "不支持转换到类型: " + targetType);
        }
        
        return converted;
    }

    // ==================== MCPResource 验证和转换 ====================

    /**
     * 验证MCPResource对象
     */
    public static ValidationResult validateResource(MCPResource resource) {
        ValidationResult result = new ValidationResult();
        
        if (resource == null) {
            result.addError("资源对象不能为null");
            return result;
        }
        
        // 验证URI（必需）
        if (!isValidString(resource.getUri())) {
            result.addError("资源URI不能为空");
        } else if (!isValidUri(resource.getUri())) {
            result.addError("资源URI格式无效: " + resource.getUri());
        }
        
        // 验证名称（可选）
        if (resource.getName() != null && resource.getName().trim().isEmpty()) {
            result.addWarning("资源名称为空字符串");
        }
        
        // 验证MIME类型（可选）
        if (resource.getMimeType() != null) {
            if (resource.getMimeType().trim().isEmpty()) {
                result.addWarning("MIME类型为空字符串");
            } else if (!MIME_TYPE_PATTERN.matcher(resource.getMimeType()).matches()) {
                result.addError("MIME类型格式无效: " + resource.getMimeType());
            }
        }
        
        return result;
    }

    // ==================== MCPPrompt 验证和转换 ====================

    /**
     * 验证MCPPrompt对象
     */
    public static ValidationResult validatePrompt(MCPPrompt prompt) {
        ValidationResult result = new ValidationResult();
        
        if (prompt == null) {
            result.addError("提示词对象不能为null");
            return result;
        }
        
        // 验证名称
        if (!isValidString(prompt.getName())) {
            result.addError("提示词名称不能为空");
        } else if (!TOOL_NAME_PATTERN.matcher(prompt.getName()).matches()) {
            result.addError("提示词名称格式无效");
        }
        
        // 验证参数Schema（可选）
        if (prompt.getArgumentSchema() != null) {
            ValidationResult schemaResult = validateJsonSchema(prompt.getArgumentSchema(), "参数Schema");
            result.merge(schemaResult);
        }
        
        return result;
    }

    // ==================== 通用验证方法 ====================

    /**
     * 验证JSON Schema
     */
    public static ValidationResult validateJsonSchema(JsonObject schema, String fieldName) {
        ValidationResult result = new ValidationResult();
        
        if (schema == null) {
            result.addError(fieldName + "不能为null");
            return result;
        }
        
        // 基本的JSON Schema验证
        try {
            // 检查是否有type字段
            if (!schema.has("type")) {
                result.addWarning(fieldName + "缺少type字段");
            }
            
            // 检查是否有properties字段（对于object类型）
            if (schema.has("type") && "object".equals(schema.get("type").getAsString())) {
                if (!schema.has("properties")) {
                    result.addWarning(fieldName + "的object类型缺少properties字段");
                }
            }
            
        } catch (Exception e) {
            result.addError(fieldName + "格式错误: " + e.getMessage());
        }
        
        return result;
    }

    /**
     * 验证字符串是否有效（非null且非空）
     */
    public static boolean isValidString(String str) {
        return str != null && !str.trim().isEmpty();
    }

    /**
     * 验证URI格式
     */
    public static boolean isValidUri(String uri) {
        if (!isValidString(uri)) {
            return false;
        }
        return URI_PATTERN.matcher(uri).matches();
    }

    /**
     * 清理字符串（去除前后空格，空字符串转为null）
     */
    public static String cleanString(String str) {
        if (str == null) {
            return null;
        }
        str = str.trim();
        return str.isEmpty() ? null : str;
    }

    /**
     * 清理注解Map
     */
    public static Map<String, Object> cleanAnnotations(Map<String, Object> annotations) {
        if (annotations == null || annotations.isEmpty()) {
            return new HashMap<>();
        }
        
        Map<String, Object> cleaned = new HashMap<>();
        for (Map.Entry<String, Object> entry : annotations.entrySet()) {
            String key = cleanString(entry.getKey());
            if (key != null && entry.getValue() != null) {
                cleaned.put(key, entry.getValue());
            }
        }
        
        return cleaned;
    }

    // ==================== 验证结果类 ====================

    /**
     * 验证结果类
     */
    public static class ValidationResult {
        private final List<String> errors = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        
        public void addError(String error) {
            errors.add(error);
        }
        
        public void addWarning(String warning) {
            warnings.add(warning);
        }
        
        public void merge(ValidationResult other) {
            errors.addAll(other.errors);
            warnings.addAll(other.warnings);
        }
        
        public boolean isValid() {
            return errors.isEmpty();
        }
        
        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }
        
        public List<String> getErrors() {
            return new ArrayList<>(errors);
        }
        
        public List<String> getWarnings() {
            return new ArrayList<>(warnings);
        }
        
        public String getErrorSummary() {
            if (errors.isEmpty()) {
                return "验证通过";
            }
            return "验证失败: " + String.join("; ", errors);
        }
        
        public String getWarningSummary() {
            if (warnings.isEmpty()) {
                return "无警告";
            }
            return "警告: " + String.join("; ", warnings);
        }
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("ValidationResult{");
            sb.append("valid=").append(isValid());
            sb.append(", errors=").append(errors.size());
            sb.append(", warnings=").append(warnings.size());
            sb.append("}");
            return sb.toString();
        }
    }

    // ==================== 批量验证方法 ====================

    /**
     * 批量验证工具列表
     */
    public static Map<MCPTool, ValidationResult> validateTools(List<MCPTool> tools) {
        Map<MCPTool, ValidationResult> results = new HashMap<>();
        if (tools != null) {
            for (MCPTool tool : tools) {
                results.put(tool, validateTool(tool));
            }
        }
        return results;
    }

    /**
     * 批量验证资源列表
     */
    public static Map<MCPResource, ValidationResult> validateResources(List<MCPResource> resources) {
        Map<MCPResource, ValidationResult> results = new HashMap<>();
        if (resources != null) {
            for (MCPResource resource : resources) {
                results.put(resource, validateResource(resource));
            }
        }
        return results;
    }

    /**
     * 批量验证提示词列表
     */
    public static Map<MCPPrompt, ValidationResult> validatePrompts(List<MCPPrompt> prompts) {
        Map<MCPPrompt, ValidationResult> results = new HashMap<>();
        if (prompts != null) {
            for (MCPPrompt prompt : prompts) {
                results.put(prompt, validatePrompt(prompt));
            }
        }
        return results;
    }

    /**
     * 获取验证统计信息
     */
    public static ValidationStatistics getValidationStatistics(Map<?, ValidationResult> results) {
        int totalItems = results.size();
        int validItems = 0;
        int itemsWithWarnings = 0;
        int totalErrors = 0;
        int totalWarnings = 0;
        
        for (ValidationResult result : results.values()) {
            if (result.isValid()) {
                validItems++;
            }
            if (result.hasWarnings()) {
                itemsWithWarnings++;
            }
            totalErrors += result.getErrors().size();
            totalWarnings += result.getWarnings().size();
        }
        
        return new ValidationStatistics(totalItems, validItems, itemsWithWarnings, 
                                      totalErrors, totalWarnings);
    }

    /**
     * 验证统计信息类
     */
    public static class ValidationStatistics {
        private final int totalItems;
        private final int validItems;
        private final int itemsWithWarnings;
        private final int totalErrors;
        private final int totalWarnings;
        
        public ValidationStatistics(int totalItems, int validItems, int itemsWithWarnings, 
                                  int totalErrors, int totalWarnings) {
            this.totalItems = totalItems;
            this.validItems = validItems;
            this.itemsWithWarnings = itemsWithWarnings;
            this.totalErrors = totalErrors;
            this.totalWarnings = totalWarnings;
        }
        
        public int getTotalItems() { return totalItems; }
        public int getValidItems() { return validItems; }
        public int getInvalidItems() { return totalItems - validItems; }
        public int getItemsWithWarnings() { return itemsWithWarnings; }
        public int getTotalErrors() { return totalErrors; }
        public int getTotalWarnings() { return totalWarnings; }
        public double getValidPercentage() { 
            return totalItems == 0 ? 0.0 : (double) validItems / totalItems * 100; 
        }
        
        @Override
        public String toString() {
            return String.format("ValidationStatistics{total=%d, valid=%d(%.1f%%), " +
                               "withWarnings=%d, errors=%d, warnings=%d}", 
                               totalItems, validItems, getValidPercentage(), 
                               itemsWithWarnings, totalErrors, totalWarnings);
        }
    }
}