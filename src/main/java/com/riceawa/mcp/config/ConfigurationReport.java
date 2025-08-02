package com.riceawa.mcp.config;

import java.util.Map;

/**
 * MCP配置状态报告
 */
public class ConfigurationReport {
    private final String reportText;
    private final Map<String, Object> details;

    public ConfigurationReport(String reportText, Map<String, Object> details) {
        this.reportText = reportText;
        this.details = details;
    }

    public String getReportText() {
        return reportText;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}