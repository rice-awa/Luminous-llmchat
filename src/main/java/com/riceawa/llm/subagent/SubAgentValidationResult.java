package com.riceawa.llm.subagent;

/**
 * 子代理验证结果
 * 封装验证结果和错误信息
 */
public class SubAgentValidationResult {
    
    private final boolean valid;
    private final String errorMessage;
    
    private SubAgentValidationResult(boolean valid, String errorMessage) {
        this.valid = valid;
        this.errorMessage = errorMessage;
    }
    
    /**
     * 创建有效的验证结果
     * 
     * @return 有效的验证结果
     */
    public static SubAgentValidationResult valid() {
        return new SubAgentValidationResult(true, null);
    }
    
    /**
     * 创建无效的验证结果
     * 
     * @param errorMessage 错误信息
     * @return 无效的验证结果
     */
    public static SubAgentValidationResult invalid(String errorMessage) {
        return new SubAgentValidationResult(false, errorMessage);
    }
    
    /**
     * 检查验证结果是否有效
     * 
     * @return 是否有效
     */
    public boolean isValid() {
        return valid;
    }
    
    /**
     * 获取错误信息
     * 
     * @return 错误信息，有效时为null
     */
    public String getErrorMessage() {
        return errorMessage;
    }
    
    @Override
    public String toString() {
        if (valid) {
            return "SubAgentValidationResult{valid=true}";
        } else {
            return "SubAgentValidationResult{valid=false, errorMessage='" + errorMessage + "'}";
        }
    }
}