package com.riceawa.llm.template;

import com.riceawa.llm.util.EntityHelper;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 提示词模板编辑器
 * 管理玩家的模板编辑会话
 */
public class TemplateEditor {
    private static TemplateEditor instance;
    private final Map<UUID, EditSession> editSessions;

    private TemplateEditor() {
        this.editSessions = new ConcurrentHashMap<>();
    }

    public static TemplateEditor getInstance() {
        if (instance == null) {
            synchronized (TemplateEditor.class) {
                if (instance == null) {
                    instance = new TemplateEditor();
                }
            }
        }
        return instance;
    }

    /**
     * 开始编辑会话
     */
    public void startEditSession(PlayerEntity player, String templateId, boolean isNewTemplate) {
        UUID playerId = player.getUuid();
        
        // 如果已有编辑会话，先结束
        if (editSessions.containsKey(playerId)) {
            endEditSession(player);
        }

        PromptTemplateManager templateManager = PromptTemplateManager.getInstance();
        PromptTemplate template;

        if (isNewTemplate) {
            // 创建新模板
            template = new PromptTemplate(templateId, "新模板", "用户创建的模板", "");
            player.sendMessage(Text.literal("✨ 开始创建新模板: " + templateId).formatted(Formatting.GREEN), false);
        } else {
            // 编辑现有模板
            template = templateManager.getTemplate(templateId);
            if (template == null) {
                player.sendMessage(Text.literal("❌ 模板不存在: " + templateId).formatted(Formatting.RED), false);
                return;
            }
            player.sendMessage(Text.literal("✏️ 开始编辑模板: " + templateId).formatted(Formatting.GREEN), false);
        }

        // 创建编辑会话
        EditSession session = new EditSession(templateId, template.copy(), isNewTemplate);
        editSessions.put(playerId, session);

        // 显示编辑菜单
        showEditMenu(player);
    }

    /**
     * 结束编辑会话
     */
    public void endEditSession(PlayerEntity player) {
        UUID playerId = player.getUuid();
        EditSession session = editSessions.remove(playerId);
        
        if (session != null) {
            player.sendMessage(Text.literal("📝 编辑会话已结束").formatted(Formatting.YELLOW), false);
        }
    }

    /**
     * 获取编辑会话
     */
    public EditSession getEditSession(PlayerEntity player) {
        return editSessions.get(player.getUuid());
    }

    /**
     * 检查玩家是否在编辑模式
     */
    public boolean isEditing(PlayerEntity player) {
        return editSessions.containsKey(player.getUuid());
    }

    /**
     * 显示编辑菜单
     */
    public void showEditMenu(PlayerEntity player) {
        EditSession session = getEditSession(player);
        if (session == null) {
            return;
        }

        PromptTemplate template = session.getTemplate();
        
        player.sendMessage(Text.literal("").formatted(Formatting.GRAY), false);
        player.sendMessage(Text.literal("=== 模板编辑器 ===").formatted(Formatting.GOLD), false);
        player.sendMessage(Text.literal("模板ID: " + template.getId()).formatted(Formatting.AQUA), false);
        player.sendMessage(Text.literal("模板名称: " + template.getName()).formatted(Formatting.AQUA), false);
        player.sendMessage(Text.literal("").formatted(Formatting.GRAY), false);
        
        player.sendMessage(Text.literal("📝 编辑选项:").formatted(Formatting.YELLOW), false);
        player.sendMessage(Text.literal("  1️⃣ /llmchat template edit name <新名称> - 修改模板名称").formatted(Formatting.WHITE), false);
        player.sendMessage(Text.literal("  2️⃣ /llmchat template edit desc <新描述> - 修改模板描述").formatted(Formatting.WHITE), false);
        player.sendMessage(Text.literal("  3️⃣ /llmchat template edit system <系统提示词> - 修改系统提示词").formatted(Formatting.WHITE), false);
        player.sendMessage(Text.literal("  4️⃣ /llmchat template edit prefix <用户前缀> - 修改用户消息前缀").formatted(Formatting.WHITE), false);
        player.sendMessage(Text.literal("  5️⃣ /llmchat template edit suffix <用户后缀> - 修改用户消息后缀").formatted(Formatting.WHITE), false);
        player.sendMessage(Text.literal("").formatted(Formatting.GRAY), false);
        
        player.sendMessage(Text.literal("🔧 变量管理:").formatted(Formatting.YELLOW), false);
        player.sendMessage(Text.literal("  📋 /llmchat template var list - 列出所有变量").formatted(Formatting.WHITE), false);
        player.sendMessage(Text.literal("  ➕ /llmchat template var set <名称> <值> - 设置自定义变量").formatted(Formatting.WHITE), false);
        player.sendMessage(Text.literal("  ➖ /llmchat template var remove <名称> - 删除自定义变量").formatted(Formatting.WHITE), false);
        player.sendMessage(Text.literal("").formatted(Formatting.GRAY), false);

        player.sendMessage(Text.literal("🔍 其他操作:").formatted(Formatting.YELLOW), false);
        player.sendMessage(Text.literal("  👁️ /llmchat template preview - 预览当前模板").formatted(Formatting.WHITE), false);
        player.sendMessage(Text.literal("  💾 /llmchat template save - 保存并应用模板").formatted(Formatting.WHITE), false);
        player.sendMessage(Text.literal("  ❌ /llmchat template cancel - 取消编辑").formatted(Formatting.WHITE), false);
        player.sendMessage(Text.literal("").formatted(Formatting.GRAY), false);

        player.sendMessage(Text.literal("💡 变量使用提示:").formatted(Formatting.YELLOW), false);
        player.sendMessage(Text.literal("  • 使用 {{变量名}} 格式在模板中引用变量").formatted(Formatting.GRAY), false);
        player.sendMessage(Text.literal("  • 内置变量: {{player}}, {{time}}, {{date}}, {{x}}, {{y}}, {{z}} 等").formatted(Formatting.GRAY), false);
        player.sendMessage(Text.literal("  • 内置变量会自动获取当前值，无需手动设置").formatted(Formatting.GRAY), false);
        player.sendMessage(Text.literal("  • 使用 preview 命令查看所有可用变量及其当前值").formatted(Formatting.GRAY), false);
    }

    /**
     * 预览模板
     */
    public void previewTemplate(PlayerEntity player) {
        EditSession session = getEditSession(player);
        if (session == null) {
            player.sendMessage(Text.literal("❌ 没有正在编辑的模板").formatted(Formatting.RED), false);
            return;
        }

        PromptTemplate template = session.getTemplate();
        
        player.sendMessage(Text.literal("").formatted(Formatting.GRAY), false);
        player.sendMessage(Text.literal("=== 模板预览 ===").formatted(Formatting.GOLD), false);
        player.sendMessage(Text.literal("ID: " + template.getId()).formatted(Formatting.AQUA), false);
        player.sendMessage(Text.literal("名称: " + template.getName()).formatted(Formatting.AQUA), false);
        player.sendMessage(Text.literal("描述: " + template.getDescription()).formatted(Formatting.AQUA), false);
        player.sendMessage(Text.literal("").formatted(Formatting.GRAY), false);
        
        player.sendMessage(Text.literal("📋 系统提示词:").formatted(Formatting.YELLOW), false);
        String systemPrompt = template.getSystemPrompt();
        if (systemPrompt != null && !systemPrompt.trim().isEmpty()) {
            // 分行显示长文本
            String[] lines = systemPrompt.split("\n");
            for (String line : lines) {
                if (line.length() > 80) {
                    // 长行分割显示
                    for (int i = 0; i < line.length(); i += 80) {
                        int end = Math.min(i + 80, line.length());
                        player.sendMessage(Text.literal("  " + line.substring(i, end)).formatted(Formatting.WHITE), false);
                    }
                } else {
                    player.sendMessage(Text.literal("  " + line).formatted(Formatting.WHITE), false);
                }
            }
        } else {
            player.sendMessage(Text.literal("  (未设置)").formatted(Formatting.GRAY), false);
        }
        
        player.sendMessage(Text.literal("").formatted(Formatting.GRAY), false);
        player.sendMessage(Text.literal("📝 用户消息前缀:").formatted(Formatting.YELLOW), false);
        String prefix = template.getUserPromptPrefix();
        if (prefix != null && !prefix.trim().isEmpty()) {
            player.sendMessage(Text.literal("  " + prefix).formatted(Formatting.WHITE), false);
        } else {
            player.sendMessage(Text.literal("  (未设置)").formatted(Formatting.GRAY), false);
        }
        
        player.sendMessage(Text.literal("📝 用户消息后缀:").formatted(Formatting.YELLOW), false);
        String suffix = template.getUserPromptSuffix();
        if (suffix != null && !suffix.trim().isEmpty()) {
            player.sendMessage(Text.literal("  " + suffix).formatted(Formatting.WHITE), false);
        } else {
            player.sendMessage(Text.literal("  (未设置)").formatted(Formatting.GRAY), false);
        }
        
        player.sendMessage(Text.literal("").formatted(Formatting.GRAY), false);

        // 显示内置变量
        player.sendMessage(Text.literal("🔧 内置变量 (自动获取):").formatted(Formatting.YELLOW), false);
        player.sendMessage(Text.literal("  {{player}} = " + player.getName().getString() + " (玩家名)").formatted(Formatting.GREEN), false);
        player.sendMessage(Text.literal("  {{time}} = " + java.time.LocalDateTime.now().format(
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + " (当前时间)").formatted(Formatting.GREEN), false);
        player.sendMessage(Text.literal("  {{date}} = " + java.time.LocalDate.now().format(
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")) + " (当前日期)").formatted(Formatting.GREEN), false);
        player.sendMessage(Text.literal("  {{world}} = " + EntityHelper.getWorld(player).getRegistryKey().getValue().toString() + " (世界)").formatted(Formatting.GREEN), false);
        player.sendMessage(Text.literal("  {{x}}, {{y}}, {{z}} = " + (int)player.getX() + ", " + (int)player.getY() + ", " + (int)player.getZ() + " (坐标)").formatted(Formatting.GREEN), false);
        player.sendMessage(Text.literal("  {{health}}, {{level}} = " + (int)player.getHealth() + ", " + player.experienceLevel + " (生命值, 等级)").formatted(Formatting.GREEN), false);
        player.sendMessage(Text.literal("  更多内置变量: {{hour}}, {{minute}}, {{dimension}}, {{gamemode}}, {{weather}}, {{server}}").formatted(Formatting.GREEN), false);

        player.sendMessage(Text.literal("").formatted(Formatting.GRAY), false);
        player.sendMessage(Text.literal("🔧 自定义变量 (" + template.getVariables().size() + "个):").formatted(Formatting.YELLOW), false);
        if (!template.getVariables().isEmpty()) {
            for (Map.Entry<String, String> entry : template.getVariables().entrySet()) {
                player.sendMessage(Text.literal("  {{" + entry.getKey() + "}} = " + entry.getValue()).formatted(Formatting.AQUA), false);
            }
        } else {
            player.sendMessage(Text.literal("  (无自定义变量)").formatted(Formatting.GRAY), false);
        }

        player.sendMessage(Text.literal("").formatted(Formatting.GRAY), false);
    }

    /**
     * 保存模板
     */
    public void saveTemplate(PlayerEntity player) {
        EditSession session = getEditSession(player);
        if (session == null) {
            player.sendMessage(Text.literal("❌ 没有正在编辑的模板").formatted(Formatting.RED), false);
            return;
        }

        try {
            PromptTemplateManager templateManager = PromptTemplateManager.getInstance();
            PromptTemplate template = session.getTemplate();
            
            if (session.isNewTemplate()) {
                templateManager.addTemplate(template);
                player.sendMessage(Text.literal("✅ 新模板已创建并保存: " + template.getId()).formatted(Formatting.GREEN), false);
            } else {
                templateManager.updateTemplate(template);
                player.sendMessage(Text.literal("✅ 模板已更新并保存: " + template.getId()).formatted(Formatting.GREEN), false);
            }
            
            // 结束编辑会话
            endEditSession(player);
            
            player.sendMessage(Text.literal("💡 使用 /llmchat template set " + template.getId() + " 来应用此模板").formatted(Formatting.GRAY), false);
            
        } catch (Exception e) {
            player.sendMessage(Text.literal("❌ 保存模板失败: " + e.getMessage()).formatted(Formatting.RED), false);
        }
    }

    /**
     * 编辑会话类
     */
    public static class EditSession {
        private final String templateId;
        private final PromptTemplate template;
        private final boolean isNewTemplate;

        public EditSession(String templateId, PromptTemplate template, boolean isNewTemplate) {
            this.templateId = templateId;
            this.template = template;
            this.isNewTemplate = isNewTemplate;
        }

        public String getTemplateId() {
            return templateId;
        }

        public PromptTemplate getTemplate() {
            return template;
        }

        public boolean isNewTemplate() {
            return isNewTemplate;
        }
    }
}
