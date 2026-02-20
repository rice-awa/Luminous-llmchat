package com.riceawa.llm.util;

import com.riceawa.mixin.ServerPlayerEntityAccessor;
import net.minecraft.command.permission.PermissionCheck;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Utility class for accessing Entity world and server references in Minecraft 1.21.11.
 * 
 * In Minecraft 1.21.11, several methods were removed from Entity/PlayerEntity:
 * - getWorld() / getEntityWorld() - removed
 * - getServer() - removed from PlayerEntity
 * - getPos() - removed
 * - hasPermissionLevel() - removed from ServerCommandSource
 * 
 * This utility provides alternative ways to access these values.
 */
public final class EntityHelper {
    
    private EntityHelper() {} // Prevent instantiation
    
    /**
     * Get the ServerWorld from a ServerPlayerEntity.
     * Uses the server reference to get the overworld as context for the command source.
     */
    public static ServerWorld getServerWorld(ServerPlayerEntity player) {
        MinecraftServer server = getServer(player);
        // Get the world from the player's command source
        return player.getCommandSource(server.getOverworld()).getWorld();
    }
    
    /**
     * Get the MinecraftServer from a ServerPlayerEntity.
     * Uses the private server field via Mixin accessor.
     */
    public static MinecraftServer getServer(ServerPlayerEntity player) {
        return ((ServerPlayerEntityAccessor) player).getServerInstance();
    }
    
    /**
     * Get the MinecraftServer from any PlayerEntity.
     * Returns null if the player is not a ServerPlayerEntity.
     */
    @Nullable
    public static MinecraftServer getServerSafe(PlayerEntity player) {
        if (player instanceof ServerPlayerEntity serverPlayer) {
            return getServer(serverPlayer);
        }
        return null;
    }
    
    /**
     * Get the World from any PlayerEntity.
     * For ServerPlayerEntity, uses the server world.
     * For client players, returns null.
     */
    @Nullable
    public static World getWorld(PlayerEntity player) {
        if (player instanceof ServerPlayerEntity serverPlayer) {
            return getServerWorld(serverPlayer);
        }
        // For client-side players, we cannot reliably get the world
        return null;
    }
    
    /**
     * Get the entity's position as a Vec3d.
     * In 1.21.11, Entity.getPos() was removed.
     * Use getEyePos() or construct from getX(), getY(), getZ().
     */
    public static Vec3d getPos(Entity entity) {
        return entity.getEyePos();
    }
    
    /**
     * Get the entity's exact position (feet position).
     * Uses getX(), getY(), getZ() instead of removed getPos().
     */
    public static Vec3d getExactPos(Entity entity) {
        return new Vec3d(entity.getX(), entity.getY(), entity.getZ());
    }
    
    /**
     * Check if a player has OP permission.
     * Uses the player's permission predicate from their command source.
     */
    public static boolean isOperator(PlayerEntity player) {
        if (player instanceof ServerPlayerEntity serverPlayer) {
            MinecraftServer server = getServer(serverPlayer);
            ServerWorld world = server.getOverworld();
            ServerCommandSource source = player.getCommandSource(world);
            // Check if player has any elevated permissions
            return hasPermissionLevel(source, 2);
        }
        return false;
    }
    
    /**
     * Check if a ServerCommandSource has a specific permission level.
     * In 1.21.11, hasPermissionLevel(int) was removed.
     * Uses the new PermissionPredicate API.
     */
    public static boolean hasPermissionLevel(ServerCommandSource source, int level) {
        // Map permission levels to the new PermissionCheck constants
        PermissionCheck check = switch (level) {
            case 1 -> CommandManager.MODERATORS_CHECK;
            case 2 -> CommandManager.GAMEMASTERS_CHECK;
            case 3 -> CommandManager.ADMINS_CHECK;
            case 4 -> CommandManager.OWNERS_CHECK;
            default -> CommandManager.GAMEMASTERS_CHECK;
        };
        return CommandManager.requirePermissionLevel(check).test(source);
    }
    
    /**
     * Get a ServerWorld from a PlayerEntity, with proper type checking.
     * Returns null if the player is not in a server world.
     */
    @Nullable
    public static ServerWorld getServerWorldSafe(PlayerEntity player) {
        if (player instanceof ServerPlayerEntity serverPlayer) {
            try {
                return getServerWorld(serverPlayer);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }
}
