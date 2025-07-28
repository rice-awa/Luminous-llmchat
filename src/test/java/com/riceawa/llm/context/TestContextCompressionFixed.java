package com.riceawa.llm.context;
/**
 * 测试修复后的上下文压缩功能
 */
public class TestContextCompressionFixed {
    public static void main(String[] args) {
        System.out.println("=== 测试修复后的上下文压缩功能 ===");
        
        // 测试场景1：maxContextLength = 1
        System.out.println("\n场景1：maxContextLength = 1");
        testCompressionLogic(1, 0, 2);
        
        // 测试场景2：maxContextLength = 3，有1个系统消息
        System.out.println("\n场景2：maxContextLength = 3，有1个系统消息");
        testCompressionLogic(3, 1, 4);
        
        // 测试场景3：maxContextLength = 5，有2个系统消息
        System.out.println("\n场景3：maxContextLength = 5，有2个系统消息");
        testCompressionLogic(5, 2, 6);
        
        System.out.println("\n=== 测试完成 ===");
    }
    
    /**
     * 测试压缩逻辑
     */
    private static void testCompressionLogic(int maxContextLength, int systemMessageCount, int otherMessageCount) {
        System.out.println("maxContextLength: " + maxContextLength);
        System.out.println("系统消息数量: " + systemMessageCount);
        System.out.println("其他消息数量: " + otherMessageCount);
        
        // 模拟原始逻辑
        int maxOtherMessages = maxContextLength - systemMessageCount;
        System.out.println("最大其他消息数量: " + maxOtherMessages);
        
        if (otherMessageCount > maxOtherMessages) {
            // 新的压缩逻辑
            int availableSpaceAfterCompression = Math.max(1, maxContextLength - systemMessageCount - 1);
            int messagesToCompress = otherMessageCount - availableSpaceAfterCompression;
            
            if (messagesToCompress <= 0) {
                messagesToCompress = Math.max(1, otherMessageCount / 2);
            }
            
            System.out.println("压缩后可用空间: " + availableSpaceAfterCompression);
            System.out.println("需要压缩的消息数量: " + messagesToCompress);
            
            if (messagesToCompress > 0 && messagesToCompress < otherMessageCount) {
                int remainingMessages = otherMessageCount - messagesToCompress;
                int finalMessageCount = systemMessageCount + 1 + remainingMessages; // +1 为压缩摘要
                System.out.println("剩余消息数量: " + remainingMessages);
                System.out.println("压缩后总消息数量: " + finalMessageCount);
                
                if (finalMessageCount <= maxContextLength) {
                    System.out.println("✅ 压缩后符合长度限制");
                } else {
                    System.out.println("❌ 压缩后仍超过长度限制");
                }
            } else {
                System.out.println("❌ 压缩参数无效");
            }
        } else {
            System.out.println("✅ 无需压缩");
        }
    }
}
