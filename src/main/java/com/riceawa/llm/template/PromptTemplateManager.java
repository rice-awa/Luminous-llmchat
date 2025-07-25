package com.riceawa.llm.template;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 提示词模板管理器
 */
public class PromptTemplateManager {
    private static PromptTemplateManager instance;
    private final Map<String, PromptTemplate> templates;
    private final Gson gson;
    private final Path templatesFile;

    private PromptTemplateManager() {
        this.templates = new ConcurrentHashMap<>();
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        
        Path configDir = FabricLoader.getInstance().getConfigDir().resolve("lllmchat");
        this.templatesFile = configDir.resolve("prompt_templates.json");
        
        // 确保配置目录存在
        try {
            Files.createDirectories(configDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create config directory", e);
        }
        
        // 加载模板
        loadTemplates();
        
        // 如果没有模板，创建默认模板
        if (templates.isEmpty()) {
            createDefaultTemplates();
            saveTemplates();
        }
    }

    public static PromptTemplateManager getInstance() {
        if (instance == null) {
            synchronized (PromptTemplateManager.class) {
                if (instance == null) {
                    instance = new PromptTemplateManager();
                }
            }
        }
        return instance;
    }

    /**
     * 获取模板
     */
    public PromptTemplate getTemplate(String id) {
        return templates.get(id);
    }

    /**
     * 获取所有模板
     */
    public Collection<PromptTemplate> getAllTemplates() {
        return new ArrayList<>(templates.values());
    }

    /**
     * 获取启用的模板
     */
    public Collection<PromptTemplate> getEnabledTemplates() {
        return templates.values().stream()
                .filter(PromptTemplate::isEnabled)
                .toList();
    }

    /**
     * 添加模板
     */
    public void addTemplate(PromptTemplate template) {
        templates.put(template.getId(), template);
        saveTemplates();
    }

    /**
     * 更新模板
     */
    public void updateTemplate(PromptTemplate template) {
        if (templates.containsKey(template.getId())) {
            templates.put(template.getId(), template);
            saveTemplates();
        }
    }

    /**
     * 删除模板
     */
    public void removeTemplate(String id) {
        templates.remove(id);
        saveTemplates();
    }

    /**
     * 检查模板是否存在
     */
    public boolean hasTemplate(String id) {
        return templates.containsKey(id);
    }

    /**
     * 获取默认模板
     */
    public PromptTemplate getDefaultTemplate() {
        return getTemplate("default");
    }

    /**
     * 创建默认模板
     */
    private void createDefaultTemplates() {
        // 默认助手模板
        PromptTemplate defaultTemplate = new PromptTemplate(
                "default",
                "默认助手",
                "通用的AI助手模板",
                "你是一个有用的AI助手，在Minecraft游戏中为玩家提供帮助。请用中文回答问题，保持友好和有帮助的态度。"
        );
        templates.put("default", defaultTemplate);

        // 猫猫模板
        PromptTemplate meowTemplate = new PromptTemplate(
                "meow",
                "猫猫",
                "可爱的猫猫",
                "请你扮演一只可爱的猫猫，在Minecraft游戏中为玩家提供帮助。请用中文回答问题，适当添加颜文字，始终保持友好和有帮助的态度。"
        );
        templates.put("default", meowTemplate);

        // 创造模式助手
        PromptTemplate creativeTemplate = new PromptTemplate(
                "creative",
                "创造助手",
                "专门帮助创造模式建筑的助手",
                "你是一个专业的Minecraft建筑师助手。你擅长建筑设计、红石电路、装饰技巧等。请为玩家提供创造性的建议和详细的建造指导。"
        );
        templates.put("creative", creativeTemplate);

        // 生存模式助手
        PromptTemplate survivalTemplate = new PromptTemplate(
                "survival",
                "生存助手",
                "专门帮助生存模式游戏的助手",
                "你是一个经验丰富的Minecraft生存专家。你了解游戏机制、怪物特性、资源获取、农业种植等生存技巧。请帮助玩家在生存模式中更好地游戏。"
        );
        templates.put("survival", survivalTemplate);

        // 红石工程师
        PromptTemplate redstoneTemplate = new PromptTemplate(
                "redstone",
                "红石工程师",
                "专门帮助红石电路设计的助手",
                "你是一个红石电路专家。你精通各种红石元件、逻辑门、时序电路、自动化装置的设计和优化。请为玩家提供专业的红石技术指导。"
        );
        templates.put("redstone", redstoneTemplate);

        // 模组助手
        PromptTemplate modTemplate = new PromptTemplate(
                "mod",
                "模组助手",
                "帮助玩家了解和使用各种模组",
                "你是一个Minecraft模组专家。你了解各种流行模组的功能、用法、配置和兼容性。请帮助玩家更好地使用和配置模组。"
        );
        templates.put("mod", modTemplate);
    }

    /**
     * 加载模板
     */
    private void loadTemplates() {
        if (!Files.exists(templatesFile)) {
            return;
        }

        try (FileReader reader = new FileReader(templatesFile.toFile())) {
            Type mapType = new TypeToken<Map<String, PromptTemplate>>(){}.getType();
            Map<String, PromptTemplate> loadedTemplates = gson.fromJson(reader, mapType);
            if (loadedTemplates != null) {
                templates.putAll(loadedTemplates);
            }
        } catch (IOException e) {
            System.err.println("Failed to load prompt templates: " + e.getMessage());
        }
    }

    /**
     * 保存模板
     */
    private void saveTemplates() {
        try (FileWriter writer = new FileWriter(templatesFile.toFile())) {
            gson.toJson(templates, writer);
        } catch (IOException e) {
            System.err.println("Failed to save prompt templates: " + e.getMessage());
        }
    }

    /**
     * 重新加载模板
     */
    public void reload() {
        templates.clear();
        loadTemplates();
        if (templates.isEmpty()) {
            createDefaultTemplates();
            saveTemplates();
        }
    }
}
