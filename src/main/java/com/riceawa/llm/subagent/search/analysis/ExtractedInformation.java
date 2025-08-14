package com.riceawa.llm.subagent.search.analysis;

import java.util.*;

/**
 * 从搜索结果中提取的信息
 * 包含关键词、来源、内容质量等结构化信息
 */
public class ExtractedInformation {
    
    private final List<SearchResult> searchResults;
    private final Set<String> keywords;
    private final Set<String> sources;
    private final Map<Integer, ContentQuality> contentQualities;
    private final SearchStatistics searchStatistics;
    
    /**
     * 构造函数
     */
    public ExtractedInformation(List<SearchResult> searchResults, Set<String> keywords, 
                               Set<String> sources, Map<Integer, ContentQuality> contentQualities,
                               SearchStatistics searchStatistics) {
        this.searchResults = Collections.unmodifiableList(new ArrayList<>(searchResults));
        this.keywords = Collections.unmodifiableSet(new HashSet<>(keywords));
        this.sources = Collections.unmodifiableSet(new HashSet<>(sources));
        this.contentQualities = Collections.unmodifiableMap(new HashMap<>(contentQualities));
        this.searchStatistics = searchStatistics;
    }
    
    /**
     * 获取搜索结果
     */
    public List<SearchResult> getSearchResults() {
        return searchResults;
    }
    
    /**
     * 获取关键词
     */
    public Set<String> getKeywords() {
        return keywords;
    }
    
    /**
     * 获取信息来源
     */
    public Set<String> getSources() {
        return sources;
    }
    
    /**
     * 获取内容质量评估
     */
    public Map<Integer, ContentQuality> getContentQualities() {
        return contentQualities;
    }
    
    /**
     * 获取搜索统计
     */
    public SearchStatistics getSearchStatistics() {
        return searchStatistics;
    }
    
    /**
     * 获取内容质量
     */
    public ContentQuality getContentQuality(int roundNumber) {
        return contentQualities.get(roundNumber);
    }
    
    /**
     * 搜索结果内部类
     */
    public static class SearchResult {
        private final int roundNumber;
        private final String query;
        private final String content;
        private final int contentLength;
        
        public SearchResult(int roundNumber, String query, String content) {
            this.roundNumber = roundNumber;
            this.query = query;
            this.content = content;
            this.contentLength = content != null ? content.length() : 0;
        }
        
        public int getRoundNumber() {
            return roundNumber;
        }
        
        public String getQuery() {
            return query;
        }
        
        public String getContent() {
            return content;
        }
        
        public int getContentLength() {
            return contentLength;
        }
        
        public ContentQuality getQuality() {
            // 基于内容长度和质量映射获取质量等级
            if (contentLength < 100) {
                return ContentQuality.POOR;
            } else if (contentLength < 500) {
                return ContentQuality.FAIR;
            } else if (contentLength < 1000) {
                return ContentQuality.GOOD;
            } else {
                return ContentQuality.EXCELLENT;
            }
        }
    }
    
    /**
     * 构建器类
     */
    public static class Builder {
        private final List<SearchResult> searchResults = new ArrayList<>();
        private final Set<String> keywords = new LinkedHashSet<>();
        private final Set<String> sources = new LinkedHashSet<>();
        private final Map<Integer, ContentQuality> contentQualities = new HashMap<>();
        private SearchStatistics searchStatistics;
        
        public Builder addSearchResult(int roundNumber, String query, String content) {
            this.searchResults.add(new SearchResult(roundNumber, query, content));
            return this;
        }
        
        public Builder addKeyword(String keyword) {
            if (keyword != null && !keyword.trim().isEmpty()) {
                this.keywords.add(keyword.trim());
            }
            return this;
        }
        
        public Builder addSource(String source) {
            if (source != null && !source.trim().isEmpty()) {
                this.sources.add(source.trim());
            }
            return this;
        }
        
        public Builder addContentQuality(int roundNumber, ContentQuality quality) {
            if (quality != null) {
                this.contentQualities.put(roundNumber, quality);
            }
            return this;
        }
        
        public Builder searchStatistics(SearchStatistics statistics) {
            this.searchStatistics = statistics;
            return this;
        }
        
        public ExtractedInformation build() {
            if (searchStatistics == null) {
                // 如果没有提供统计信息，创建默认统计
                searchStatistics = new SearchStatistics(
                    searchResults.size(),
                    searchResults.size(), // 假设都成功
                    searchResults.stream().mapToInt(SearchResult::getContentLength).sum(),
                    0, // 没有时间信息
                    0.0 // 默认丰富度
                );
            }
            
            return new ExtractedInformation(
                searchResults,
                keywords,
                sources,
                contentQualities,
                searchStatistics
            );
        }
    }
    
    /**
     * 创建构建器
     */
    public static Builder builder() {
        return new Builder();
    }
}