package com.riceawa.llm.subagent.search;

/**
 * 搜索轮次信息
 * 记录单次搜索的详细信息
 */
public class SearchRound {
    
    private final int roundNumber;
    private final String query;
    private final String rawResult;
    private final long processingTimeMs;
    private final String nextQueryReason;
    private final long timestamp;
    private final int resultCount;
    private final boolean successful;
    
    /**
     * 构造函数
     * 
     * @param roundNumber 轮次编号（从1开始）
     * @param query 搜索查询
     * @param rawResult 原始搜索结果
     * @param processingTimeMs 处理时间（毫秒）
     * @param nextQueryReason 下一轮搜索的原因
     * @param resultCount 结果数量
     * @param successful 是否成功
     */
    public SearchRound(int roundNumber, String query, String rawResult, 
                      long processingTimeMs, String nextQueryReason, 
                      int resultCount, boolean successful) {
        this.roundNumber = roundNumber;
        this.query = query;
        this.rawResult = rawResult;
        this.processingTimeMs = processingTimeMs;
        this.nextQueryReason = nextQueryReason;
        this.resultCount = resultCount;
        this.successful = successful;
        this.timestamp = System.currentTimeMillis();
    }
    
    /**
     * 创建成功的搜索轮次
     * 
     * @param roundNumber 轮次编号
     * @param query 搜索查询
     * @param rawResult 原始搜索结果
     * @param processingTimeMs 处理时间
     * @param nextQueryReason 下一轮搜索的原因
     * @param resultCount 结果数量
     * @return 搜索轮次对象
     */
    public static SearchRound success(int roundNumber, String query, String rawResult, 
                                    long processingTimeMs, String nextQueryReason, int resultCount) {
        return new SearchRound(roundNumber, query, rawResult, processingTimeMs, 
                             nextQueryReason, resultCount, true);
    }
    
    /**
     * 创建失败的搜索轮次
     * 
     * @param roundNumber 轮次编号
     * @param query 搜索查询
     * @param errorMessage 错误信息
     * @param processingTimeMs 处理时间
     * @return 搜索轮次对象
     */
    public static SearchRound failure(int roundNumber, String query, String errorMessage, 
                                    long processingTimeMs) {
        return new SearchRound(roundNumber, query, errorMessage, processingTimeMs, 
                             null, 0, false);
    }
    
    /**
     * 获取轮次编号
     * 
     * @return 轮次编号
     */
    public int getRoundNumber() {
        return roundNumber;
    }
    
    /**
     * 获取搜索查询
     * 
     * @return 搜索查询
     */
    public String getQuery() {
        return query;
    }
    
    /**
     * 获取原始搜索结果
     * 
     * @return 原始搜索结果
     */
    public String getRawResult() {
        return rawResult;
    }
    
    /**
     * 获取处理时间
     * 
     * @return 处理时间（毫秒）
     */
    public long getProcessingTimeMs() {
        return processingTimeMs;
    }
    
    /**
     * 获取下一轮搜索的原因
     * 
     * @return 下一轮搜索的原因
     */
    public String getNextQueryReason() {
        return nextQueryReason;
    }
    
    /**
     * 获取时间戳
     * 
     * @return 时间戳
     */
    public long getTimestamp() {
        return timestamp;
    }
    
    /**
     * 获取结果数量
     * 
     * @return 结果数量
     */
    public int getResultCount() {
        return resultCount;
    }
    
    /**
     * 检查是否成功
     * 
     * @return 是否成功
     */
    public boolean isSuccessful() {
        return successful;
    }
    
    /**
     * 检查是否有下一轮搜索
     * 
     * @return 是否有下一轮搜索
     */
    public boolean hasNextRound() {
        return successful && nextQueryReason != null && !nextQueryReason.trim().isEmpty();
    }
    
    /**
     * 获取搜索轮次摘要
     * 
     * @return 搜索轮次摘要
     */
    public String getSummary() {
        if (successful) {
            return String.format("第%d轮搜索: \"%s\" - 找到%d个结果 (%dms)", 
                               roundNumber, query, resultCount, processingTimeMs);
        } else {
            return String.format("第%d轮搜索失败: \"%s\" - %s (%dms)", 
                               roundNumber, query, rawResult, processingTimeMs);
        }
    }
    
    @Override
    public String toString() {
        return "SearchRound{" +
                "roundNumber=" + roundNumber +
                ", query='" + query + '\'' +
                ", successful=" + successful +
                ", resultCount=" + resultCount +
                ", processingTimeMs=" + processingTimeMs +
                '}';
    }
}