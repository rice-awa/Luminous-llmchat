package com.riceawa.llm.function.impl;

import com.google.gson.JsonObject;
import com.riceawa.llm.function.LLMFunction;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

/**
 * 获取服务器信息的函数
 */
public class ServerInfoFunction implements LLMFunction {
    
    @Override
    public String getName() {
        return "get_server_info";
    }
    
    @Override
    public String getDescription() {
        return "获取服务器的基本信息和状态";
    }
    
    @Override
    public JsonObject getParametersSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject properties = new JsonObject();
        
        // 可选参数：是否包含性能信息
        JsonObject includePerformance = new JsonObject();
        includePerformance.addProperty("type", "boolean");
        includePerformance.addProperty("description", "是否包含服务器性能信息");
        includePerformance.addProperty("default", false);
        properties.add("include_performance", includePerformance);
        
        schema.add("properties", properties);
        return schema;
    }
    
    @Override
    public FunctionResult execute(PlayerEntity player, MinecraftServer server, JsonObject arguments) {
        try {
            boolean includePerformance = arguments.has("include_performance") && 
                                       arguments.get("include_performance").getAsBoolean();
            
            StringBuilder info = new StringBuilder();
            
            // 基本服务器信息
            info.append("=== 服务器信息 ===\n");
            info.append("服务器版本: ").append(server.getVersion()).append("\n");
            info.append("Minecraft版本: ").append(server.getServerModName()).append("\n");
            info.append("是否单人游戏: ").append(server.isSingleplayer() ? "是" : "否").append("\n");
            info.append("是否硬核模式: ").append(server.isHardcore() ? "是" : "否").append("\n");
            info.append("默认游戏模式: ").append(server.getDefaultGameMode().getTranslatableName().getString()).append("\n");
            info.append("难度: ").append(server.getOverworld().getDifficulty().getName()).append("\n");
            info.append("是否允许PvP: ").append(server.isPvpEnabled() ? "是" : "否").append("\n");
            
            // 玩家信息
            info.append("\n=== 玩家信息 ===\n");
            info.append("在线玩家数: ").append(server.getCurrentPlayerCount())
                .append("/").append(server.getMaxPlayerCount()).append("\n");
            
            // 列出在线玩家
            if (server.getCurrentPlayerCount() > 0) {
                info.append("在线玩家: ");
                boolean first = true;
                for (ServerPlayerEntity onlinePlayer : server.getPlayerManager().getPlayerList()) {
                    if (!first) {
                        info.append(", ");
                    }
                    info.append(onlinePlayer.getName().getString());
                    first = false;
                }
                info.append("\n");
            }
            
            // 世界信息
            info.append("\n=== 世界信息 ===\n");
            int worldCount = 0;
            for (ServerWorld world : server.getWorlds()) {
                worldCount++;
            }
            info.append("已加载世界数: ").append(worldCount).append("\n");
            
            // 运行时间
            long uptimeMillis = System.currentTimeMillis() - server.getTimeReference();
            long uptimeSeconds = uptimeMillis / 1000;
            long hours = uptimeSeconds / 3600;
            long minutes = (uptimeSeconds % 3600) / 60;
            long seconds = uptimeSeconds % 60;
            info.append("服务器运行时间: ").append(hours).append("小时 ")
                .append(minutes).append("分钟 ").append(seconds).append("秒\n");
            
            if (includePerformance) {
                // 性能信息（需要OP权限才能查看详细性能）
                boolean isOp = server.getPlayerManager().isOperator(player.getGameProfile());
                if (isOp) {
                    info.append("\n=== 性能信息 ===\n");
                    
                    // TPS信息 (简化版本，因为lastTickLengths在新版本中不可用)
                    info.append("TPS: 无法获取 (需要更新API)\n");
                    
                    // 内存使用情况
                    Runtime runtime = Runtime.getRuntime();
                    long maxMemory = runtime.maxMemory();
                    long totalMemory = runtime.totalMemory();
                    long freeMemory = runtime.freeMemory();
                    long usedMemory = totalMemory - freeMemory;
                    
                    info.append("内存使用: ").append(formatBytes(usedMemory))
                        .append("/").append(formatBytes(maxMemory))
                        .append(" (").append(String.format("%.1f%%", 
                            (double) usedMemory / maxMemory * 100)).append(")\n");
                    
                    // 线程数
                    info.append("活跃线程数: ").append(Thread.activeCount()).append("\n");
                    
                } else {
                    info.append("\n=== 性能信息 ===\n");
                    info.append("需要OP权限才能查看详细性能信息\n");
                }
            }
            
            return FunctionResult.success(info.toString());
            
        } catch (Exception e) {
            return FunctionResult.error("获取服务器信息失败: " + e.getMessage());
        }
    }
    
    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
    
    @Override
    public boolean hasPermission(PlayerEntity player) {
        return true; // 所有玩家都可以查看基本服务器信息
    }
    
    @Override
    public boolean isEnabled() {
        return true;
    }
    
    @Override
    public String getCategory() {
        return "server";
    }
}
