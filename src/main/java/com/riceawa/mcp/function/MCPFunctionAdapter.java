package com.riceawa.mcp.function;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.riceawa.llm.function.LLMFunction;
import com.riceawa.mcp.exception.MCPException;
import com.riceawa.mcp.model.MCPTool;
import com.riceawa.mcp.model.MCPToolResult;
import com.riceawa.mcp.service.MCPService;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * MCP功能适配器
 * 将MCP工具适配为LLMFunction接口，使其能够在现有的LLM系统中使用
 */
public class MCPFunctionAdapter implements LLMFunction {
    
    private final MCPTool mcpTool;
    private final MCPService mcpService;
    private final MCPToolPermissionManager permissionManager;
    
    // 默认超时时间
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    
    // MCP工具类别
    private static final String MCP_CATEGORY = "mcp";
    
    public MCPFunctionAdapter(MCPTool mcpTool, MCPService mcpService, MCPToolPermissionManager permissionManager) {
        this.mcpTool = mcpTool;
        this.mcpService = mcpService;
        this.permissionManager = permissionManager;
    }
    
    @Override
    public String getName() {
        // 使用完整名称以避免冲突
        return mcpTool.getFullName();
    }
    
    @Override
    public String getDescription() {
        String description = mcpTool.getDescription();
        if (description == null || description.trim().isEmpty()) {
            description = mcpTool.getDisplayName();
        }
        
        // 添加MCP工具标识
        String clientName = mcpTool.getClientName();
        if (clientName != null && !clientName.isEmpty()) {
            description = String.format("[MCP:%s] %s", clientName, description);
        } else {
            description = "[MCP] " + description;
        }
        
        return description;
    }
    
    @Override
    public JsonObject getParametersSchema() {
        JsonObject inputSchema = mcpTool.getInputSchema();
        if (inputSchema != null) {
            return deepCopyJsonObject(inputSchema);
        }
        
        // 如果没有输入schema，返回空对象schema
        JsonObject emptySchema = new JsonObject();
        emptySchema.addProperty("type", "object");
        emptySchema.add("properties", new JsonObject());
        emptySchema.add("required", new com.google.gson.JsonArray());
        return emptySchema;
    }
    
    @Override
    public FunctionResult execute(PlayerEntity player, MinecraftServer server, JsonObject arguments) {
        try {
            // 参数验证
            if (!validateParameters(arguments)) {
                return FunctionResult.error("参数验证失败");
            }
            
            // 权限检查
            if (!hasPermission(player)) {
                return FunctionResult.error("权限被拒绝");
            }
            
            // 调用MCP工具
            MCPToolResult mcpResult = mcpService.callTool(mcpTool.getName(), arguments, DEFAULT_TIMEOUT)
                    .get(DEFAULT_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            
            // 转换结果
            return convertMCPResultToFunctionResult(mcpResult);
            
        } catch (Exception e) {
            String errorMessage = "MCP工具调用失败";
            if (e.getCause() instanceof MCPException) {
                MCPException mcpException = (MCPException) e.getCause();
                errorMessage = mcpException.getUserFriendlyMessage();
            } else if (e.getMessage() != null) {
                errorMessage += ": " + e.getMessage();
            }
            
            return FunctionResult.error(errorMessage);
        }
    }
    
    @Override
    public boolean hasPermission(PlayerEntity player) {
        if (permissionManager == null) {
            // 如果没有权限管理器，默认允许所有玩家访问
            return true;
        }
        
        return permissionManager.checkPermission(player, mcpTool);
    }
    
    @Override
    public boolean isEnabled() {
        // MCP工具是否启用取决于对应的客户端是否可用
        if (mcpService == null) {
            return false;
        }
        
        String clientName = mcpTool.getClientName();
        if (clientName == null) {
            return false;
        }
        
        // 检查客户端状态
        var clientStatus = mcpService.getClientStatus(clientName);
        return clientStatus != null && clientStatus.isConnected();
    }
    
    @Override
    public String getCategory() {
        return MCP_CATEGORY;
    }
    
    /**
     * 获取原始的MCP工具
     */
    public MCPTool getMCPTool() {
        return mcpTool;
    }
    
    /**
     * 获取MCP服务
     */
    public MCPService getMCPService() {
        return mcpService;
    }
    
    /**
     * 验证参数
     */
    private boolean validateParameters(JsonObject arguments) {
        try {
            return MCPSchemaValidator.validateParameters(arguments, mcpTool.getInputSchema());
        } catch (Exception e) {
            System.err.println("参数验证失败: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 将MCPToolResult转换为FunctionResult
     */
    private FunctionResult convertMCPResultToFunctionResult(MCPToolResult mcpResult) {
        if (mcpResult == null) {
            return FunctionResult.error("MCP工具返回空结果");
        }
        
        try {
            // 检查是否是错误结果
            if (mcpResult.isError()) {
                String errorMessage = mcpResult.getErrorMessage();
                if (errorMessage == null || errorMessage.trim().isEmpty()) {
                    errorMessage = "MCP工具执行失败";
                }
                return FunctionResult.error(errorMessage);
            }
            
            // 获取成功结果
            String resultText = extractResultText(mcpResult);
            JsonObject resultData = extractResultData(mcpResult);
            
            if (resultData != null) {
                return FunctionResult.success(resultText, resultData);
            } else {
                return FunctionResult.success(resultText);
            }
            
        } catch (Exception e) {
            return FunctionResult.error("结果转换失败: " + e.getMessage());
        }
    }
    
    /**
     * 从MCPToolResult提取文本结果
     */
    private String extractResultText(MCPToolResult mcpResult) {
        // 优先使用getTextContent方法
        String textContent = mcpResult.getTextContent();
        if (textContent != null && !textContent.trim().isEmpty()) {
            return textContent;
        }
        
        // 如果没有文本内容，尝试从内容中提取
        var content = mcpResult.getContent();
        if (content != null && !content.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (var item : content) {
                if (item.getText() != null) {
                    if (sb.length() > 0) {
                        sb.append("\n");
                    }
                    sb.append(item.getText());
                }
            }
            if (sb.length() > 0) {
                return sb.toString();
            }
        }
        
        // 默认成功消息
        return "MCP工具执行成功";
    }
    
    /**
     * 从MCPToolResult提取JSON数据
     */
    private JsonObject extractResultData(MCPToolResult mcpResult) {
        try {
            JsonObject dataObject = new JsonObject();
            
            // 如果有结构化内容，添加到数据中
            Object structuredContent = mcpResult.getStructuredContent();
            if (structuredContent != null) {
                if (structuredContent instanceof JsonObject) {
                    JsonObject structured = (JsonObject) structuredContent;
                    for (String key : structured.keySet()) {
                        dataObject.add(key, structured.get(key));
                    }
                } else {
                    // 尝试将结构化内容转为JSON
                    try {
                        String json = structuredContent.toString();
                        JsonElement element = JsonParser.parseString(json);
                        if (element.isJsonObject()) {
                            JsonObject structured = element.getAsJsonObject();
                            for (String key : structured.keySet()) {
                                dataObject.add(key, structured.get(key));
                            }
                        } else {
                            dataObject.add("structured_content", element);
                        }
                    } catch (Exception e) {
                        dataObject.addProperty("structured_content", structuredContent.toString());
                    }
                }
            }
            
            // 如果有内容，尝试构建JSON对象
            var content = mcpResult.getContent();
            if (content != null && !content.isEmpty()) {
                for (int i = 0; i < content.size(); i++) {
                    var item = content.get(i);
                    JsonObject itemObj = new JsonObject();
                    
                    if (item.getType() != null) {
                        itemObj.addProperty("type", item.getType());
                    }
                    if (item.getText() != null) {
                        itemObj.addProperty("text", item.getText());
                    }
                    if (item.getData() != null) {
                        itemObj.addProperty("data", item.getData());
                    }
                    
                    if (itemObj.size() > 0) {
                        dataObject.add("content_" + i, itemObj);
                    }
                }
            }
            
            return dataObject.size() > 0 ? dataObject : null;
            
        } catch (Exception e) {
            System.err.println("提取结果数据失败: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 深度复制JsonObject
     */
    private JsonObject deepCopyJsonObject(JsonObject original) {
        if (original == null) {
            return new JsonObject();
        }
        
        try {
            // 通过序列化和反序列化实现深度复制
            String json = original.toString();
            return JsonParser.parseString(json).getAsJsonObject();
        } catch (Exception e) {
            // 如果深度复制失败，返回空schema
            JsonObject emptySchema = new JsonObject();
            emptySchema.addProperty("type", "object");
            emptySchema.add("properties", new JsonObject());
            return emptySchema;
        }
    }
    
    @Override
    public String toString() {
        return String.format("MCPFunctionAdapter{tool='%s', client='%s'}", 
                           mcpTool.getName(), mcpTool.getClientName());
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        MCPFunctionAdapter other = (MCPFunctionAdapter) obj;
        return mcpTool.equals(other.mcpTool);
    }
    
    @Override
    public int hashCode() {
        return mcpTool.hashCode();
    }
}