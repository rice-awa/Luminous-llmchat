package com.riceawa.llm.function.impl;

import com.google.gson.JsonObject;
import com.riceawa.llm.function.LLMFunction;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.border.WorldBorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorldInfoFunctionTest {

    @Mock
    private PlayerEntity mockPlayer;

    @Mock
    private MinecraftServer mockServer;

    @Mock
    private ServerWorld mockWorld;

    @Mock
    private WorldBorder mockWorldBorder;

    @Mock
    private RegistryEntry<Biome> mockBiome;

    private WorldInfoFunction worldInfoFunction;

    @BeforeEach
    void setUp() {
        worldInfoFunction = new WorldInfoFunction();
        
        // 设置基本的mock行为
        when(mockPlayer.getWorld()).thenReturn(mockWorld);
        when(mockPlayer.getBlockPos()).thenReturn(new BlockPos(100, 64, 200));
        when(mockPlayer.getServer()).thenReturn(mockServer);
        
        when(mockWorld.getDifficulty()).thenReturn(Difficulty.NORMAL);
        when(mockWorld.getTimeOfDay()).thenReturn(6000L); // 中午
        when(mockWorld.isRaining()).thenReturn(false);
        when(mockWorld.isThundering()).thenReturn(false);
        when(mockWorld.getBiome(any(BlockPos.class))).thenReturn(mockBiome);
        when(mockWorld.getSeed()).thenReturn(12345L);
        when(mockWorld.getWorldBorder()).thenReturn(mockWorldBorder);
        when(mockWorld.getSpawnPos()).thenReturn(new BlockPos(0, 64, 0));
        when(mockWorld.getSeaLevel()).thenReturn(63);
        when(mockWorld.getBottomY()).thenReturn(-64);
        when(mockWorld.getTopY()).thenReturn(320);
        
        when(mockWorldBorder.getSize()).thenReturn(60000000.0);
        
        when(mockServer.getDefaultGameMode()).thenReturn(GameMode.SURVIVAL);
        when(mockServer.isHardcore()).thenReturn(false);
        
        when(mockBiome.getKey()).thenReturn(java.util.Optional.of(
            net.minecraft.util.Identifier.of("minecraft", "plains")
        ));
    }

    @Test
    void testFunctionBasicInfo() {
        assertEquals("get_world_info", worldInfoFunction.getName());
        assertEquals("获取当前世界的基本信息，包括维度、种子、难度等", worldInfoFunction.getDescription());
        assertEquals("world", worldInfoFunction.getCategory());
        assertTrue(worldInfoFunction.isEnabled());
        assertTrue(worldInfoFunction.hasPermission(mockPlayer));
    }

    @Test
    void testParametersSchema() {
        JsonObject schema = worldInfoFunction.getParametersSchema();
        
        assertNotNull(schema);
        assertEquals("object", schema.get("type").getAsString());
        assertTrue(schema.has("properties"));
        
        JsonObject properties = schema.getAsJsonObject("properties");
        assertTrue(properties.has("include_details"));
        
        JsonObject includeDetails = properties.getAsJsonObject("include_details");
        assertEquals("boolean", includeDetails.get("type").getAsString());
        assertEquals("是否包含详细的世界信息", includeDetails.get("description").getAsString());
        assertFalse(includeDetails.get("default").getAsBoolean());
    }

    @Test
    void testBasicWorldInfo() {
        JsonObject arguments = new JsonObject();
        
        LLMFunction.FunctionResult result = worldInfoFunction.execute(mockPlayer, mockServer, arguments);
        
        assertTrue(result.isSuccess());
        String info = result.getResult();
        
        // 验证基本信息
        assertTrue(info.contains("=== 世界信息 ==="));
        assertTrue(info.contains("维度: 主世界"));
        assertTrue(info.contains("难度: Normal"));
        assertTrue(info.contains("游戏模式: survival"));
        assertTrue(info.contains("是否硬核: 否"));
        assertTrue(info.contains("游戏时间: 12:00"));
        assertTrue(info.contains("游戏天数: 1"));
        assertTrue(info.contains("天气: 晴朗"));
        assertTrue(info.contains("玩家位置: 100, 64, 200"));
        assertTrue(info.contains("当前生物群系: minecraft:plains"));
    }

    @Test
    void testDetailedWorldInfo() {
        JsonObject arguments = new JsonObject();
        arguments.addProperty("include_details", true);
        
        LLMFunction.FunctionResult result = worldInfoFunction.execute(mockPlayer, mockServer, arguments);
        
        assertTrue(result.isSuccess());
        String info = result.getResult();
        
        // 验证详细信息
        assertTrue(info.contains("=== 详细信息 ==="));
        assertTrue(info.contains("世界种子: 12345"));
        assertTrue(info.contains("世界边界大小: 60000000"));
        assertTrue(info.contains("出生点: 0, 64, 0"));
        assertTrue(info.contains("海平面高度: 63"));
        assertTrue(info.contains("最低建筑高度: -64"));
        assertTrue(info.contains("最高建筑高度: 320"));
    }

    @Test
    void testRainyWeather() {
        when(mockWorld.isRaining()).thenReturn(true);
        when(mockWorld.isThundering()).thenReturn(false);
        
        JsonObject arguments = new JsonObject();
        LLMFunction.FunctionResult result = worldInfoFunction.execute(mockPlayer, mockServer, arguments);
        
        assertTrue(result.isSuccess());
        assertTrue(result.getResult().contains("天气: 下雨"));
    }

    @Test
    void testThunderWeather() {
        when(mockWorld.isRaining()).thenReturn(true);
        when(mockWorld.isThundering()).thenReturn(true);
        
        JsonObject arguments = new JsonObject();
        LLMFunction.FunctionResult result = worldInfoFunction.execute(mockPlayer, mockServer, arguments);
        
        assertTrue(result.isSuccess());
        assertTrue(result.getResult().contains("天气: 雷雨"));
    }

    @Test
    void testDifferentTimeOfDay() {
        // 测试夜晚时间 (18000 ticks = 24:00)
        when(mockWorld.getTimeOfDay()).thenReturn(18000L);
        
        JsonObject arguments = new JsonObject();
        LLMFunction.FunctionResult result = worldInfoFunction.execute(mockPlayer, mockServer, arguments);
        
        assertTrue(result.isSuccess());
        assertTrue(result.getResult().contains("游戏时间: 00:00"));
    }

    @Test
    void testDifferentDimensions() {
        // 测试下界
        when(mockWorld.getRegistryKey()).thenReturn(
            net.minecraft.registry.RegistryKey.of(
                net.minecraft.registry.RegistryKeys.WORLD,
                net.minecraft.util.Identifier.of("minecraft", "the_nether")
            )
        );
        
        JsonObject arguments = new JsonObject();
        LLMFunction.FunctionResult result = worldInfoFunction.execute(mockPlayer, mockServer, arguments);
        
        assertTrue(result.isSuccess());
        assertTrue(result.getResult().contains("维度: 下界"));
    }

    @Test
    void testHardcoreMode() {
        when(mockServer.isHardcore()).thenReturn(true);
        
        JsonObject arguments = new JsonObject();
        LLMFunction.FunctionResult result = worldInfoFunction.execute(mockPlayer, mockServer, arguments);
        
        assertTrue(result.isSuccess());
        assertTrue(result.getResult().contains("是否硬核: 是"));
    }

    @Test
    void testDifferentGameModes() {
        when(mockServer.getDefaultGameMode()).thenReturn(GameMode.CREATIVE);
        
        JsonObject arguments = new JsonObject();
        LLMFunction.FunctionResult result = worldInfoFunction.execute(mockPlayer, mockServer, arguments);
        
        assertTrue(result.isSuccess());
        assertTrue(result.getResult().contains("游戏模式: creative"));
    }

    @Test
    void testMultipleDays() {
        // 测试第3天
        when(mockWorld.getTimeOfDay()).thenReturn(48000L + 6000L); // 2 full days + noon
        
        JsonObject arguments = new JsonObject();
        LLMFunction.FunctionResult result = worldInfoFunction.execute(mockPlayer, mockServer, arguments);
        
        assertTrue(result.isSuccess());
        assertTrue(result.getResult().contains("游戏天数: 3"));
        assertTrue(result.getResult().contains("游戏时间: 12:00"));
    }

    @Test
    void testErrorHandling() {
        // 模拟异常情况
        when(mockPlayer.getWorld()).thenThrow(new RuntimeException("Test exception"));
        
        JsonObject arguments = new JsonObject();
        LLMFunction.FunctionResult result = worldInfoFunction.execute(mockPlayer, mockServer, arguments);
        
        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("获取世界信息失败"));
        assertTrue(result.getError().contains("Test exception"));
    }

    @Test
    void testBiomeWithoutKey() {
        when(mockBiome.getKey()).thenReturn(java.util.Optional.empty());
        
        JsonObject arguments = new JsonObject();
        LLMFunction.FunctionResult result = worldInfoFunction.execute(mockPlayer, mockServer, arguments);
        
        assertTrue(result.isSuccess());
        assertTrue(result.getResult().contains("当前生物群系: 未知"));
    }

    @Test
    void testTimeCalculation() {
        // 测试各种时间计算
        
        // 早晨 6:00 (0 ticks)
        when(mockWorld.getTimeOfDay()).thenReturn(0L);
        JsonObject arguments = new JsonObject();
        LLMFunction.FunctionResult result = worldInfoFunction.execute(mockPlayer, mockServer, arguments);
        assertTrue(result.getResult().contains("游戏时间: 06:00"));
        
        // 下午 15:30 (9500 ticks)
        when(mockWorld.getTimeOfDay()).thenReturn(9500L);
        result = worldInfoFunction.execute(mockPlayer, mockServer, arguments);
        assertTrue(result.getResult().contains("游戏时间: 15:30"));
        
        // 深夜 23:45 (17750 ticks)
        when(mockWorld.getTimeOfDay()).thenReturn(17750L);
        result = worldInfoFunction.execute(mockPlayer, mockServer, arguments);
        assertTrue(result.getResult().contains("游戏时间: 23:45"));
    }
}
