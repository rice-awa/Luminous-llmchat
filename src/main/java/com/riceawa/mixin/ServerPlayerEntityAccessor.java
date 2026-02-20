package com.riceawa.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor for ServerPlayerEntity and related classes.
 * This is needed because Minecraft 1.21.11 removed some public methods.
 */
@Mixin(ServerPlayerEntity.class)
public interface ServerPlayerEntityAccessor {
    
    /**
     * Access the private server field in ServerPlayerEntity.
     * @return The MinecraftServer instance
     */
    @Accessor("server")
    MinecraftServer getServerInstance();
}

/**
 * Accessor for ServerPlayerInteractionManager to get the world.
 */
@Mixin(ServerPlayerInteractionManager.class)
interface ServerPlayerInteractionManagerAccessor {
    
    /**
     * Access the protected world field.
     * @return The ServerWorld instance
     */
    @Accessor("world")
    ServerWorld getWorld();
    
    /**
     * Set the world field.
     */
    @Accessor("world")
    void setWorld(ServerWorld world);
}
