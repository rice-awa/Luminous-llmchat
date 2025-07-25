package com.riceawa.llm.function;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 权限管理工具类，统一处理LLM函数的权限检查
 */
public class PermissionHelper {
    
    /**
     * 危险指令黑名单 - 这些指令不允许通过LLM执行
     */
    private static final Set<String> COMMAND_BLACKLIST = new HashSet<>(Arrays.asList(
        "stop", "restart", "shutdown",  // 服务器控制
        "op", "deop",                   // 权限管理
        "whitelist",                    // 白名单管理
        "ban", "ban-ip", "pardon", "pardon-ip",  // 封禁管理
        "save-all", "save-off", "save-on",       // 存档管理
        "reload",                       // 重载配置
        "debug",                        // 调试命令
        "perf",                         // 性能分析
        "jfr",                          // Java Flight Recorder
        "datapack",                     // 数据包管理
        "function"                      // 函数执行（避免递归）
    ));
    
    /**
     * 需要特殊权限的指令前缀
     */
    private static final Set<String> RESTRICTED_COMMAND_PREFIXES = new HashSet<>(Arrays.asList(
        "execute",      // 执行命令
        "forceload",    // 强制加载区块
        "worldborder",  // 世界边界
        "difficulty",   // 难度设置
        "gamerule"      // 游戏规则
    ));
    
    /**
     * 检查玩家是否为OP
     */
    public static boolean isOperator(PlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }
        return server.getPlayerManager().isOperator(player.getGameProfile());
    }
    
    /**
     * 检查玩家是否有指定级别的命令权限
     */
    public static boolean hasCommandPermission(PlayerEntity player, int level) {
        if (player == null) {
            return false;
        }
        
        // 获取玩家所在的世界
        World world = player.getWorld();
        if (!(world instanceof ServerWorld)) {
            return false;
        }
        
        ServerWorld serverWorld = (ServerWorld) world;
        ServerCommandSource source = player.getCommandSource(serverWorld);
        return source.hasPermissionLevel(level);
    }
    
    /**
     * 检查玩家是否可以修改世界
     */
    public static boolean canModifyWorld(PlayerEntity player) {
        return isOperator(player);
    }
    
    /**
     * 检查玩家是否可以执行指定指令
     */
    public static boolean canExecuteCommand(PlayerEntity player, String command) {
        if (!isOperator(player)) {
            return false;
        }
        
        return !isCommandBlacklisted(command);
    }
    
    /**
     * 检查指令是否在黑名单中
     */
    public static boolean isCommandBlacklisted(String command) {
        if (command == null || command.trim().isEmpty()) {
            return true;
        }
        
        String cleanCommand = command.trim().toLowerCase();
        
        // 移除开头的斜杠
        if (cleanCommand.startsWith("/")) {
            cleanCommand = cleanCommand.substring(1);
        }
        
        // 检查完整指令名
        String[] parts = cleanCommand.split("\\s+");
        if (parts.length > 0) {
            String commandName = parts[0];
            
            // 检查黑名单
            if (COMMAND_BLACKLIST.contains(commandName)) {
                return true;
            }
            
            // 检查受限制的指令前缀
            for (String prefix : RESTRICTED_COMMAND_PREFIXES) {
                if (commandName.startsWith(prefix)) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * 检查玩家是否可以查看其他玩家的信息
     */
    public static boolean canViewOtherPlayerInfo(PlayerEntity requester, PlayerEntity target) {
        // 可以查看自己的信息
        if (requester.equals(target)) {
            return true;
        }
        
        // OP可以查看任何玩家的信息
        return isOperator(requester);
    }
    
    /**
     * 检查玩家是否可以对其他玩家执行操作
     */
    public static boolean canOperateOnOtherPlayer(PlayerEntity requester, PlayerEntity target) {
        // 不能对自己执行某些操作
        if (requester.equals(target)) {
            return false;
        }
        
        // 只有OP可以对其他玩家执行操作
        return isOperator(requester);
    }
    
    /**
     * 检查玩家是否可以发送广播消息
     */
    public static boolean canSendBroadcast(PlayerEntity player) {
        return isOperator(player);
    }
    
    /**
     * 检查玩家是否可以控制服务器环境（天气、时间等）
     */
    public static boolean canControlEnvironment(PlayerEntity player) {
        return isOperator(player);
    }
    
    /**
     * 检查玩家是否可以生成实体
     */
    public static boolean canSummonEntity(PlayerEntity player) {
        return isOperator(player);
    }
    
    /**
     * 检查玩家是否可以传送其他玩家
     */
    public static boolean canTeleportOthers(PlayerEntity player) {
        return isOperator(player);
    }
    
    /**
     * 获取权限错误消息
     */
    public static String getPermissionErrorMessage(String action) {
        return "没有权限执行操作: " + action + "（需要OP权限）";
    }
    
    /**
     * 获取指令黑名单错误消息
     */
    public static String getBlacklistErrorMessage(String command) {
        return "指令 '" + command + "' 被禁止通过LLM执行，出于安全考虑";
    }
}
