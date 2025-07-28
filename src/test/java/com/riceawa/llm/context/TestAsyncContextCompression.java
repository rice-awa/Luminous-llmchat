package com.riceawa.llm.context;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 测试异步上下文压缩功能
 */
public class TestAsyncContextCompression {

    public static void main(String[] args) {
        TestAsyncContextCompression test = new TestAsyncContextCompression();

        System.out.println("开始测试异步上下文压缩功能...");

        try {
            test.testAsyncCompressionTriggering();
            test.testCompressionNotTriggeredWhenNotNeeded();
            test.testMultipleCompressionCallsDoNotOverlap();

            System.out.println("所有测试通过！");
        } catch (Exception e) {
            System.err.println("测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    void testAsyncCompressionTriggering() throws InterruptedException {
        System.out.println("测试1: 异步压缩触发");

        UUID testPlayerId = UUID.randomUUID();
        ChatContext chatContext = new ChatContext(testPlayerId);
        chatContext.setMaxContextCharacters(500);

        // 创建一个计数器来跟踪压缩事件
        CountDownLatch compressionStartedLatch = new CountDownLatch(1);
        CountDownLatch compressionCompletedLatch = new CountDownLatch(1);

        // 设置事件监听器
        chatContext.setEventListener(new ChatContext.ContextEventListener() {
            @Override
            public void onContextCompressionStarted(UUID playerId, int messagesToCompress) {
                System.out.println("压缩开始: " + messagesToCompress + " 条消息");
                compressionStartedLatch.countDown();
            }

            @Override
            public void onContextCompressionCompleted(UUID playerId, boolean success, int originalCount, int compressedCount) {
                System.out.println("压缩完成: 成功=" + success + ", 原始=" + originalCount + ", 压缩后=" + compressedCount);
                compressionCompletedLatch.countDown();
            }
        });

        // 添加足够多的消息以触发压缩
        chatContext.addSystemMessage("系统消息");
        for (int i = 0; i < 10; i++) {
            chatContext.addUserMessage("这是一个很长的用户消息，包含大量的文字内容，用来测试异步压缩功能是否能正确工作。消息编号：" + i);
            chatContext.addAssistantMessage("这是一个很长的助手回复，同样包含大量的文字内容，继续测试异步压缩功能的有效性。回复编号：" + i);
        }

        // 检查是否超过限制
        int totalChars = chatContext.calculateTotalCharacters();
        int maxChars = chatContext.getMaxContextCharacters();
        System.out.println("总字符数: " + totalChars + ", 限制: " + maxChars);

        if (totalChars <= maxChars) {
            throw new RuntimeException("消息总长度应该超过限制");
        }

        // 触发异步压缩
        chatContext.scheduleCompressionIfNeeded();

        // 等待压缩开始（最多等待5秒）
        if (!compressionStartedLatch.await(5, TimeUnit.SECONDS)) {
            throw new RuntimeException("压缩应该在5秒内开始");
        }

        // 等待压缩完成（最多等待10秒）
        if (!compressionCompletedLatch.await(10, TimeUnit.SECONDS)) {
            throw new RuntimeException("压缩应该在10秒内完成");
        }

        System.out.println("测试1通过: 异步压缩正常工作");
    }

    void testCompressionNotTriggeredWhenNotNeeded() throws InterruptedException {
        System.out.println("测试2: 未超过限制时不触发压缩");

        UUID testPlayerId = UUID.randomUUID();
        ChatContext chatContext = new ChatContext(testPlayerId);
        chatContext.setMaxContextCharacters(500);

        // 创建一个计数器来跟踪压缩事件
        CountDownLatch compressionLatch = new CountDownLatch(1);

        // 设置事件监听器
        chatContext.setEventListener(new ChatContext.ContextEventListener() {
            @Override
            public void onContextCompressionStarted(UUID playerId, int messagesToCompress) {
                System.out.println("意外触发了压缩");
                compressionLatch.countDown();
            }

            @Override
            public void onContextCompressionCompleted(UUID playerId, boolean success, int originalCount, int compressedCount) {
                System.out.println("意外完成了压缩");
                compressionLatch.countDown();
            }
        });

        // 添加少量消息，不超过限制
        chatContext.addSystemMessage("系统消息");
        chatContext.addUserMessage("短消息");
        chatContext.addAssistantMessage("短回复");

        // 检查是否未超过限制
        int totalChars = chatContext.calculateTotalCharacters();
        int maxChars = chatContext.getMaxContextCharacters();
        System.out.println("总字符数: " + totalChars + ", 限制: " + maxChars);

        if (totalChars > maxChars) {
            throw new RuntimeException("消息总长度不应该超过限制");
        }

        // 尝试触发压缩
        chatContext.scheduleCompressionIfNeeded();

        // 等待一小段时间，确保没有触发压缩
        if (compressionLatch.await(2, TimeUnit.SECONDS)) {
            throw new RuntimeException("不应该触发压缩");
        }

        System.out.println("测试2通过: 未超过限制时不触发压缩");
    }

    void testMultipleCompressionCallsDoNotOverlap() throws InterruptedException {
        System.out.println("测试3: 多次压缩调用不重叠");

        UUID testPlayerId = UUID.randomUUID();
        ChatContext chatContext = new ChatContext(testPlayerId);
        chatContext.setMaxContextCharacters(500);

        // 设置事件监听器来计数压缩事件
        CountDownLatch compressionStartedLatch = new CountDownLatch(1);

        chatContext.setEventListener(new ChatContext.ContextEventListener() {
            @Override
            public void onContextCompressionStarted(UUID playerId, int messagesToCompress) {
                System.out.println("压缩开始: " + messagesToCompress + " 条消息");
                compressionStartedLatch.countDown();
            }

            @Override
            public void onContextCompressionCompleted(UUID playerId, boolean success, int originalCount, int compressedCount) {
                System.out.println("压缩完成: 成功=" + success);
            }
        });

        // 添加大量消息
        chatContext.addSystemMessage("系统消息");
        for (int i = 0; i < 15; i++) {
            chatContext.addUserMessage("这是一个很长的用户消息，包含大量的文字内容，用来测试多次压缩调用不会重叠。消息编号：" + i);
            chatContext.addAssistantMessage("这是一个很长的助手回复，同样包含大量的文字内容，继续测试功能。回复编号：" + i);
        }

        // 多次调用压缩
        System.out.println("多次调用压缩...");
        chatContext.scheduleCompressionIfNeeded();
        chatContext.scheduleCompressionIfNeeded();
        chatContext.scheduleCompressionIfNeeded();

        // 应该只有一次压缩开始
        if (!compressionStartedLatch.await(5, TimeUnit.SECONDS)) {
            throw new RuntimeException("应该有一次压缩开始");
        }

        // 等待一段时间确保没有额外的压缩
        Thread.sleep(1000);

        System.out.println("测试3通过: 多次压缩调用不重叠");
    }
}
