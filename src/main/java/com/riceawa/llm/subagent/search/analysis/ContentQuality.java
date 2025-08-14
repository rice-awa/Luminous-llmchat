package com.riceawa.llm.subagent.search.analysis;

/**
 * 内容质量枚举
 */
public enum ContentQuality {
    POOR("差"),
    FAIR("一般"),
    GOOD("良好"),
    EXCELLENT("优秀");
    
    private final String displayName;
    
    ContentQuality(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
}