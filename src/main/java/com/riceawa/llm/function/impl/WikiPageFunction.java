package com.riceawa.llm.function.impl;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.riceawa.llm.config.LLMChatConfig;
import com.riceawa.llm.function.LLMFunction;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Wiki页面内容获取函数，用于获取指定Wiki页面的详细内容
 */
public class WikiPageFunction implements LLMFunction {
    
    private static final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();
    
    private static final Gson gson = new Gson();
    
    @Override
    public String getName() {
        return "wiki_page";
    }
    
    @Override
    public String getDescription() {
        return "获取指定Minecraft Wiki页面的详细内容";
    }
    
    @Override
    public JsonObject getParametersSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject properties = new JsonObject();
        
        // 必需参数：页面名称
        JsonObject pageName = new JsonObject();
        pageName.addProperty("type", "string");
        pageName.addProperty("description", "Wiki页面名称，如：钻石、金锭、红石等");
        properties.add("page_name", pageName);
        
        // 可选参数：输出格式
        JsonObject format = new JsonObject();
        format.addProperty("type", "string");
        format.addProperty("description", "输出格式：markdown（默认）或 html");
        JsonArray enumArray = new JsonArray();
        enumArray.add("markdown");
        enumArray.add("html");
        format.add("enum", enumArray);
        format.addProperty("default", "markdown");
        properties.add("format", format);
        
        // 可选参数：是否包含元数据
        JsonObject includeMetadata = new JsonObject();
        includeMetadata.addProperty("type", "boolean");
        includeMetadata.addProperty("description", "是否包含页面元数据信息");
        includeMetadata.addProperty("default", false);
        properties.add("include_metadata", includeMetadata);
        
        // 可选参数：内容长度限制
        JsonObject maxLength = new JsonObject();
        maxLength.addProperty("type", "integer");
        maxLength.addProperty("description", "内容长度限制（字符数），0表示不限制，没特殊情况不要限制。");
        maxLength.addProperty("minimum", 0);
        maxLength.addProperty("maximum", 11000);
        maxLength.addProperty("default", 5000);
        properties.add("max_length", maxLength);
        
        schema.add("properties", properties);
        
        // 必需参数列表
        JsonArray required = new JsonArray();
        required.add("page_name");
        schema.add("required", required);
        
        return schema;
    }
    
    @Override
    public FunctionResult execute(PlayerEntity player, MinecraftServer server, JsonObject arguments) {
        try {
            // 解析参数
            if (!arguments.has("page_name")) {
                return FunctionResult.error("缺少必需参数: page_name");
            }
            
            String pageName = arguments.get("page_name").getAsString().trim();
            if (pageName.isEmpty()) {
                return FunctionResult.error("页面名称不能为空");
            }
            
            String format = arguments.has("format") ? 
                    arguments.get("format").getAsString() : "markdown";
            if (!format.equals("markdown") && !format.equals("html")) {
                format = "markdown";
            }
            
            boolean includeMetadata = arguments.has("include_metadata") && 
                                    arguments.get("include_metadata").getAsBoolean();
            
            int maxLength = arguments.has("max_length") ? 
                    arguments.get("max_length").getAsInt() : 5000;
            if (maxLength < 0) maxLength = 5000;
            if (maxLength > 11000) maxLength = 11000;
            
            // 构建API请求URL
            String wikiBaseUrl = LLMChatConfig.getInstance().getWikiApiUrl();
            StringBuilder urlBuilder = new StringBuilder(wikiBaseUrl + "/api/page/");
            urlBuilder.append(java.net.URLEncoder.encode(pageName, "UTF-8"));
            urlBuilder.append("?format=").append(format);
            urlBuilder.append("&includeMetadata=").append(includeMetadata);
            
            String url = urlBuilder.toString();
            
            // 发送HTTP请求
            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", "Luminous-LLMChat-Mod/1.0")
                    .build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                String responseBody = response.body().string();
                
                if (!response.isSuccessful()) {
                    // 尝试解析错误响应
                    if (responseBody != null && !responseBody.isEmpty()) {
                        try {
                            JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
                            if (jsonResponse != null && jsonResponse.has("error")) {
                                JsonObject error = jsonResponse.getAsJsonObject("error");
                                String errorMsg = WikiErrorHandler.handleError(error, pageName);
                                return FunctionResult.error("Wiki页面获取失败: " + errorMsg);
                            }
                        } catch (Exception parseEx) {
                            // 解析失败，使用原有逻辑
                        }
                    }
                    
                    // 原有的HTTP状态码处理
                    if (response.code() == 404) {
                        return FunctionResult.error("Wiki页面不存在: " + pageName);
                    }
                    return FunctionResult.error("Wiki API请求失败: HTTP " + response.code());
                }
                
                JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
                
                if (!jsonResponse.get("success").getAsBoolean()) {
                    JsonObject error = jsonResponse.getAsJsonObject("error");
                    String errorMsg = WikiErrorHandler.handleError(error, pageName);
                    return FunctionResult.error("Wiki页面获取失败: " + errorMsg);
                }
                
                // 解析页面内容
                JsonObject data = jsonResponse.getAsJsonObject("data");
                JsonObject page = data.getAsJsonObject("page");
                String title = page.get("title").getAsString();
                JsonObject content = page.getAsJsonObject("content");
                
                // 获取内容文本
                String pageContent;
                if (format.equals("markdown")) {
                    pageContent = content.get("markdown").getAsString();
                } else {
                    pageContent = content.get("html").getAsString();
                }
                
                // 应用长度限制
                if (maxLength > 0 && pageContent.length() > maxLength) {
                    pageContent = pageContent.substring(0, maxLength) + "\n\n[内容过长，已截断...]";
                }
                
                // 构建返回结果
                StringBuilder resultText = new StringBuilder();
                resultText.append("=== ").append(title).append(" ===\n\n");
                resultText.append(pageContent);
                
                if (includeMetadata && page.has("meta")) {
                    JsonObject meta = page.getAsJsonObject("meta");
                    resultText.append("\n\n=== 页面信息 ===\n");
                    if (meta.has("wordCount") && !meta.get("wordCount").isJsonNull()) {
                        resultText.append("字数: ").append(meta.get("wordCount").getAsInt()).append("\n");
                    }
                    if (meta.has("imageCount") && !meta.get("imageCount").isJsonNull()) {
                        resultText.append("图片数: ").append(meta.get("imageCount").getAsInt()).append("\n");
                    }
                    if (meta.has("tableCount") && !meta.get("tableCount").isJsonNull()) {
                        resultText.append("表格数: ").append(meta.get("tableCount").getAsInt()).append("\n");
                    }
                    if (meta.has("sectionCount") && !meta.get("sectionCount").isJsonNull()) {
                        resultText.append("章节数: ").append(meta.get("sectionCount").getAsInt()).append("\n");
                    }
                }
                
                // 添加来源信息
                resultText.append("\n📖 内容来源: 中文 Minecraft Wiki (CC BY-NC-SA 3.0)\n");
                if (page.has("url") && !page.get("url").isJsonNull()) {
                    resultText.append("🔗 页面链接: ").append(page.get("url").getAsString()).append("\n");
                }
                
                // 返回结构化数据
                JsonObject resultData = new JsonObject();
                resultData.addProperty("pageName", pageName);
                resultData.addProperty("title", title);
                resultData.addProperty("format", format);
                resultData.addProperty("contentLength", pageContent.length());
                if (page.has("url") && !page.get("url").isJsonNull()) {
                    resultData.addProperty("url", page.get("url").getAsString());
                }
                
                return FunctionResult.success(resultText.toString(), resultData);
                
            }
            
        } catch (IOException e) {
            return FunctionResult.error("网络请求失败: " + e.getMessage());
        } catch (Exception e) {
            return FunctionResult.error("页面处理失败: " + e.getMessage());
        }
    }
    
    @Override
    public boolean hasPermission(PlayerEntity player) {
        return true; // 所有玩家都可以查看Wiki页面
    }
    
    @Override
    public boolean isEnabled() {
        return true;
    }
    
    @Override
    public String getCategory() {
        return "wiki";
    }
}