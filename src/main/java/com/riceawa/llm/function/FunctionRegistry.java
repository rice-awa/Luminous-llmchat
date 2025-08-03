package com.riceawa.llm.function;

import com.google.gson.JsonObject;
import com.riceawa.llm.core.LLMConfig;
import net.minecraft.entity.player.PlayerEntity;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Function注册表，管理所有可用的LLM函数
 */
public class FunctionRegistry {
    private static FunctionRegistry instance;
    private final Map<String, LLMFunction> functions;
    private final Map<String, Set<String>> categoryFunctions;

    private FunctionRegistry() {
        this.functions = new ConcurrentHashMap<>();
        this.categoryFunctions = new ConcurrentHashMap<>();
        registerDefaultFunctions();
    }

    public static FunctionRegistry getInstance() {
        if (instance == null) {
            synchronized (FunctionRegistry.class) {
                if (instance == null) {
                    instance = new FunctionRegistry();
                }
            }
        }
        return instance;
    }

    /**
     * 注册函数
     */
    public void registerFunction(LLMFunction function) {
        functions.put(function.getName(), function);
        
        // 添加到类别映射
        String category = function.getCategory();
        categoryFunctions.computeIfAbsent(category, k -> new HashSet<>()).add(function.getName());
    }

    /**
     * 注销函数
     */
    public void unregisterFunction(String name) {
        LLMFunction function = functions.remove(name);
        if (function != null) {
            String category = function.getCategory();
            Set<String> categorySet = categoryFunctions.get(category);
            if (categorySet != null) {
                categorySet.remove(name);
                if (categorySet.isEmpty()) {
                    categoryFunctions.remove(category);
                }
            }
        }
    }

    /**
     * 获取函数
     */
    public LLMFunction getFunction(String name) {
        return functions.get(name);
    }

    /**
     * 获取所有函数
     */
    public Collection<LLMFunction> getAllFunctions() {
        return new ArrayList<>(functions.values());
    }

    /**
     * 获取启用的函数
     */
    public Collection<LLMFunction> getEnabledFunctions() {
        return functions.values().stream()
                .filter(LLMFunction::isEnabled)
                .collect(Collectors.toList());
    }

    /**
     * 获取玩家可用的函数
     */
    public Collection<LLMFunction> getAvailableFunctions(PlayerEntity player) {
        return functions.values().stream()
                .filter(LLMFunction::isEnabled)
                .filter(function -> function.hasPermission(player))
                .collect(Collectors.toList());
    }

    /**
     * 获取指定类别的函数
     */
    public Collection<LLMFunction> getFunctionsByCategory(String category) {
        Set<String> functionNames = categoryFunctions.get(category);
        if (functionNames == null) {
            return Collections.emptyList();
        }
        
        return functionNames.stream()
                .map(functions::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 获取所有类别
     */
    public Set<String> getCategories() {
        return new HashSet<>(categoryFunctions.keySet());
    }

    /**
     * 检查函数是否存在
     */
    public boolean hasFunction(String name) {
        return functions.containsKey(name);
    }

    /**
     * 为LLM配置生成工具定义（新的OpenAI API格式）
     */
    public List<LLMConfig.ToolDefinition> generateToolDefinitions(PlayerEntity player) {
        Collection<LLMFunction> availableFunctions = getAvailableFunctions(player);
        List<LLMConfig.ToolDefinition> definitions = new ArrayList<>();

        for (LLMFunction function : availableFunctions) {
            LLMConfig.FunctionDefinition functionDef = new LLMConfig.FunctionDefinition(
                    function.getName(),
                    function.getDescription(),
                    function.getParametersSchema()
            );
            LLMConfig.ToolDefinition toolDef = new LLMConfig.ToolDefinition(functionDef);
            definitions.add(toolDef);
        }

        return definitions;
    }

    /**
     * 为LLM配置生成函数定义（保持向后兼容）
     * @deprecated 使用 generateToolDefinitions 替代
     */
    @Deprecated
    public List<LLMConfig.FunctionDefinition> generateFunctionDefinitions(PlayerEntity player) {
        Collection<LLMFunction> availableFunctions = getAvailableFunctions(player);
        List<LLMConfig.FunctionDefinition> definitions = new ArrayList<>();

        for (LLMFunction function : availableFunctions) {
            LLMConfig.FunctionDefinition definition = new LLMConfig.FunctionDefinition(
                    function.getName(),
                    function.getDescription(),
                    function.getParametersSchema()
            );
            definitions.add(definition);
        }

        return definitions;
    }

    /**
     * 执行函数调用
     */
    public LLMFunction.FunctionResult executeFunction(String functionName, PlayerEntity player, 
                                                     JsonObject arguments) {
        LLMFunction function = getFunction(functionName);
        if (function == null) {
            return LLMFunction.FunctionResult.error("函数不存在: " + functionName);
        }
        
        if (!function.isEnabled()) {
            return LLMFunction.FunctionResult.error("函数已禁用: " + functionName);
        }
        
        if (!function.hasPermission(player)) {
            return LLMFunction.FunctionResult.error("没有权限调用函数: " + functionName);
        }
        
        try {
            return function.execute(player, player.getServer(), arguments);
        } catch (Exception e) {
            return LLMFunction.FunctionResult.error("函数执行失败: " + e.getMessage());
        }
    }

    /**
     * 注册默认函数
     */
    private void registerDefaultFunctions() {
        // 注册基础信息函数
        registerFunction(new GetTimeFunction());
        registerFunction(new GetPlayerInfoFunction());
        registerFunction(new GetWeatherFunction());

        // 注册信息查询函数
        registerFunction(new com.riceawa.llm.function.impl.WorldInfoFunction());
        registerFunction(new com.riceawa.llm.function.impl.PlayerStatsFunction());
        registerFunction(new com.riceawa.llm.function.impl.InventoryFunction());
        registerFunction(new com.riceawa.llm.function.impl.ServerInfoFunction());
        registerFunction(new com.riceawa.llm.function.impl.NearbyEntitiesFunction());
        registerFunction(new com.riceawa.llm.function.impl.PlayerEffectsFunction());

        // 注册交互功能函数
        registerFunction(new com.riceawa.llm.function.impl.SendMessageFunction());
        registerFunction(new com.riceawa.llm.function.impl.TeleportPlayerFunction());

        // 注册管理员功能函数（需要OP权限）
        registerFunction(new com.riceawa.llm.function.impl.ExecuteCommandFunction());
        registerFunction(new com.riceawa.llm.function.impl.SetBlockFunction());
        registerFunction(new com.riceawa.llm.function.impl.SummonEntityFunction());
        registerFunction(new com.riceawa.llm.function.impl.WeatherControlFunction());
        registerFunction(new com.riceawa.llm.function.impl.TimeControlFunction());
        
        // 注册Wiki功能函数
        registerFunction(new com.riceawa.llm.function.impl.WikiSearchFunction());
        registerFunction(new com.riceawa.llm.function.impl.WikiPageFunction());
        registerFunction(new com.riceawa.llm.function.impl.WikiBatchPagesFunction());
    }

    /**
     * 获取时间函数（示例）
     */
    private static class GetTimeFunction implements LLMFunction {
        @Override
        public String getName() {
            return "get_time";
        }

        @Override
        public String getDescription() {
            return "获取当前游戏时间";
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            schema.add("properties", new JsonObject());
            return schema;
        }

        @Override
        public FunctionResult execute(PlayerEntity player, net.minecraft.server.MinecraftServer server, JsonObject arguments) {
            long time = player.getWorld().getTimeOfDay();
            int hours = (int) ((time / 1000 + 6) % 24);
            int minutes = (int) ((time % 1000) * 60 / 1000);
            
            String timeString = String.format("%02d:%02d", hours, minutes);
            return FunctionResult.success("当前游戏时间是: " + timeString);
        }

        @Override
        public boolean hasPermission(PlayerEntity player) {
            return true; // 所有玩家都可以查看时间
        }

        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public String getCategory() {
            return "info";
        }
    }

    /**
     * 获取玩家信息函数（示例）
     */
    private static class GetPlayerInfoFunction implements LLMFunction {
        @Override
        public String getName() {
            return "get_player_info";
        }

        @Override
        public String getDescription() {
            return "获取玩家基本信息";
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            schema.add("properties", new JsonObject());
            return schema;
        }

        @Override
        public FunctionResult execute(PlayerEntity player, net.minecraft.server.MinecraftServer server, JsonObject arguments) {
            String info = String.format("玩家: %s, 生命值: %.1f/%.1f, 经验等级: %d", 
                    player.getName().getString(),
                    player.getHealth(),
                    player.getMaxHealth(),
                    player.experienceLevel);
            
            return FunctionResult.success(info);
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
            return "info";
        }
    }

    /**
     * 获取天气函数（示例）
     */
    private static class GetWeatherFunction implements LLMFunction {
        @Override
        public String getName() {
            return "get_weather";
        }

        @Override
        public String getDescription() {
            return "获取当前天气信息";
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            schema.add("properties", new JsonObject());
            return schema;
        }

        @Override
        public FunctionResult execute(PlayerEntity player, net.minecraft.server.MinecraftServer server, JsonObject arguments) {
            boolean isRaining = player.getWorld().isRaining();
            boolean isThundering = player.getWorld().isThundering();
            
            String weather;
            if (isThundering) {
                weather = "雷雨";
            } else if (isRaining) {
                weather = "下雨";
            } else {
                weather = "晴朗";
            }
            
            return FunctionResult.success("当前天气: " + weather);
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
            return "info";
        }
    }
}
