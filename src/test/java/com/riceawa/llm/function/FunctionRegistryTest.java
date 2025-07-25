package com.riceawa.llm.function;

import com.google.gson.JsonObject;
import com.riceawa.llm.core.LLMConfig;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FunctionRegistryTest {

    @Mock
    private PlayerEntity mockPlayer;

    @Mock
    private MinecraftServer mockServer;

    private FunctionRegistry functionRegistry;
    private TestFunction testFunction;

    @BeforeEach
    void setUp() {
        functionRegistry = FunctionRegistry.getInstance();
        // 清理注册表
        functionRegistry.unregisterFunction("test_function");
        
        testFunction = new TestFunction();
        functionRegistry.registerFunction(testFunction);
    }

    @Test
    void testRegisterFunction() {
        LLMFunction retrievedFunction = functionRegistry.getFunction("test_function");
        assertNotNull(retrievedFunction);
        assertEquals("test_function", retrievedFunction.getName());
        assertEquals("测试函数", retrievedFunction.getDescription());
    }

    @Test
    void testUnregisterFunction() {
        functionRegistry.unregisterFunction("test_function");
        LLMFunction retrievedFunction = functionRegistry.getFunction("test_function");
        assertNull(retrievedFunction);
    }

    @Test
    void testGenerateToolDefinitions() {
        when(mockPlayer.hasPermissionLevel(anyInt())).thenReturn(true);
        
        List<LLMConfig.ToolDefinition> toolDefinitions = functionRegistry.generateToolDefinitions(mockPlayer);
        
        assertFalse(toolDefinitions.isEmpty());
        
        // 查找我们的测试函数
        LLMConfig.ToolDefinition testToolDef = toolDefinitions.stream()
            .filter(tool -> "test_function".equals(tool.getFunction().getName()))
            .findFirst()
            .orElse(null);
        
        assertNotNull(testToolDef);
        assertEquals("function", testToolDef.getType());
        assertEquals("test_function", testToolDef.getFunction().getName());
        assertEquals("测试函数", testToolDef.getFunction().getDescription());
    }

    @Test
    void testExecuteFunction() {
        JsonObject arguments = new JsonObject();
        arguments.addProperty("test_param", "test_value");
        
        LLMFunction.FunctionResult result = functionRegistry.executeFunction("test_function", mockPlayer, arguments);
        
        assertTrue(result.isSuccess());
        assertEquals("测试函数执行成功: test_value", result.getResult());
    }

    @Test
    void testExecuteNonExistentFunction() {
        JsonObject arguments = new JsonObject();
        
        LLMFunction.FunctionResult result = functionRegistry.executeFunction("non_existent", mockPlayer, arguments);
        
        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("函数不存在"));
    }

    @Test
    void testExecuteDisabledFunction() {
        DisabledTestFunction disabledFunction = new DisabledTestFunction();
        functionRegistry.registerFunction(disabledFunction);
        
        JsonObject arguments = new JsonObject();
        LLMFunction.FunctionResult result = functionRegistry.executeFunction("disabled_function", mockPlayer, arguments);
        
        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("函数已禁用"));
        
        functionRegistry.unregisterFunction("disabled_function");
    }

    @Test
    void testExecuteFunctionWithoutPermission() {
        NoPermissionTestFunction noPermFunction = new NoPermissionTestFunction();
        functionRegistry.registerFunction(noPermFunction);
        
        JsonObject arguments = new JsonObject();
        LLMFunction.FunctionResult result = functionRegistry.executeFunction("no_permission_function", mockPlayer, arguments);
        
        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("没有权限"));
        
        functionRegistry.unregisterFunction("no_permission_function");
    }

    // 测试用的函数实现
    private static class TestFunction implements LLMFunction {
        @Override
        public String getName() {
            return "test_function";
        }

        @Override
        public String getDescription() {
            return "测试函数";
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            JsonObject properties = new JsonObject();
            
            JsonObject testParam = new JsonObject();
            testParam.addProperty("type", "string");
            testParam.addProperty("description", "测试参数");
            properties.add("test_param", testParam);
            
            schema.add("properties", properties);
            return schema;
        }

        @Override
        public FunctionResult execute(PlayerEntity player, MinecraftServer server, JsonObject arguments) {
            String testParam = arguments.has("test_param") ? arguments.get("test_param").getAsString() : "default";
            return FunctionResult.success("测试函数执行成功: " + testParam);
        }

        @Override
        public boolean hasPermission(PlayerEntity player) {
            return true;
        }

        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public String getCategory() {
            return "test";
        }
    }

    private static class DisabledTestFunction implements LLMFunction {
        @Override
        public String getName() {
            return "disabled_function";
        }

        @Override
        public String getDescription() {
            return "禁用的测试函数";
        }

        @Override
        public JsonObject getParametersSchema() {
            return new JsonObject();
        }

        @Override
        public FunctionResult execute(PlayerEntity player, MinecraftServer server, JsonObject arguments) {
            return FunctionResult.success("不应该执行到这里");
        }

        @Override
        public boolean hasPermission(PlayerEntity player) {
            return true;
        }

        @Override
        public boolean isEnabled() {
            return false; // 禁用状态
        }

        @Override
        public String getCategory() {
            return "test";
        }
    }

    private static class NoPermissionTestFunction implements LLMFunction {
        @Override
        public String getName() {
            return "no_permission_function";
        }

        @Override
        public String getDescription() {
            return "无权限测试函数";
        }

        @Override
        public JsonObject getParametersSchema() {
            return new JsonObject();
        }

        @Override
        public FunctionResult execute(PlayerEntity player, MinecraftServer server, JsonObject arguments) {
            return FunctionResult.success("不应该执行到这里");
        }

        @Override
        public boolean hasPermission(PlayerEntity player) {
            return false; // 无权限
        }

        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public String getCategory() {
            return "test";
        }
    }
}
