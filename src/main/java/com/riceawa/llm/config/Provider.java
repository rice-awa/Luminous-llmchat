package com.riceawa.llm.config;

import java.util.List;
import java.util.Objects;

/**
 * API提供商配置类
 */
public class Provider {
    private String name;
    private String apiBaseUrl;
    private String apiKey;
    private List<String> models;

    public Provider() {
    }

    public Provider(String name, String apiBaseUrl, String apiKey, List<String> models) {
        this.name = name;
        this.apiBaseUrl = apiBaseUrl;
        this.apiKey = apiKey;
        this.models = models;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public void setApiBaseUrl(String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public List<String> getModels() {
        return models;
    }

    public void setModels(List<String> models) {
        this.models = models;
    }

    /**
     * 检查提供商配置是否有效
     */
    public boolean isValid() {
        return name != null && !name.trim().isEmpty() &&
               apiBaseUrl != null && !apiBaseUrl.trim().isEmpty() &&
               apiKey != null && !apiKey.trim().isEmpty() &&
               models != null && !models.isEmpty();
    }

    /**
     * 检查是否支持指定模型
     */
    public boolean supportsModel(String model) {
        return models != null && models.contains(model);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Provider provider = (Provider) o;
        return Objects.equals(name, provider.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return "Provider{" +
                "name='" + name + '\'' +
                ", apiBaseUrl='" + apiBaseUrl + '\'' +
                ", models=" + models +
                '}';
    }
}
