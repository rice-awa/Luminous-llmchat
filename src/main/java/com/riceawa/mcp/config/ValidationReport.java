package com.riceawa.mcp.config;

import java.util.ArrayList;
import java.util.List;

/**
 * MCP配置验证报告
 */
public class ValidationReport {
    private final boolean valid;
    private final List<String> issues;
    private final List<String> warnings;
    private final String reportText;

    public ValidationReport(boolean valid, List<String> issues, List<String> warnings) {
        this.valid = valid;
        this.issues = issues != null ? new ArrayList<>(issues) : new ArrayList<>();
        this.warnings = warnings != null ? new ArrayList<>(warnings) : new ArrayList<>();
        this.reportText = generateReportText();
    }

    public boolean isValid() {
        return valid;
    }

    public List<String> getIssues() {
        return new ArrayList<>(issues);
    }

    public List<String> getWarnings() {
        return new ArrayList<>(warnings);
    }

    public String getReportText() {
        return reportText;
    }

    private String generateReportText() {
        StringBuilder sb = new StringBuilder();
        
        if (valid) {
            sb.append("✅ MCP配置验证通过\n");
        } else {
            sb.append("❌ MCP配置验证失败\n");
        }
        
        if (!issues.isEmpty()) {
            sb.append("\n错误:\n");
            for (String issue : issues) {
                sb.append("  ❌ ").append(issue).append("\n");
            }
        }
        
        if (!warnings.isEmpty()) {
            sb.append("\n警告:\n");
            for (String warning : warnings) {
                sb.append("  ⚠️ ").append(warning).append("\n");
            }
        }
        
        return sb.toString();
    }
}