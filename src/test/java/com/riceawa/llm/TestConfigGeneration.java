package com.riceawa.llm;
import com.riceawa.llm.config.LLMChatConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileReader;

public class TestConfigGeneration {
    public static void main(String[] args) {
        try {
            // 删除现有配置文件（如果存在）
            File configFile = new File("run/config/lllmchat/config.json");
            if (configFile.exists()) {
                configFile.delete();
                System.out.println("已删除现有配置文件");
            }
            
            // 创建配置实例，这会触发默认配置生成
            LLMChatConfig config = LLMChatConfig.getInstance();
            
            // 检查providers是否正确生成
            System.out.println("Providers数量: " + config.getProviders().size());
            System.out.println("当前Provider: " + config.getCurrentProvider());
            System.out.println("当前Model: " + config.getCurrentModel());
            
            // 读取生成的配置文件内容
            if (configFile.exists()) {
                System.out.println("\n生成的配置文件内容:");
                try (FileReader reader = new FileReader(configFile)) {
                    Gson gson = new GsonBuilder().setPrettyPrinting().create();
                    Object configData = gson.fromJson(reader, Object.class);
                    System.out.println(gson.toJson(configData));
                }
            } else {
                System.out.println("配置文件未生成");
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
