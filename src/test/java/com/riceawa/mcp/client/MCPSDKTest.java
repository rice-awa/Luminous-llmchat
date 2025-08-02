package com.riceawa.mcp.client;

import org.junit.jupiter.api.Test;

/**
 * Test to check what MCP SDK classes are available
 */
class MCPSDKTest {
    
    @Test
    void testMCPSDKAvailability() {
        try {
            // Try to load MCP classes
            Class.forName("io.modelcontextprotocol.sdk.client.McpClient");
            System.out.println("McpClient found");
        } catch (ClassNotFoundException e) {
            System.out.println("McpClient not found: " + e.getMessage());
        }
        
        try {
            Class.forName("io.modelcontextprotocol.sdk.client.McpAsyncClient");
            System.out.println("McpAsyncClient found");
        } catch (ClassNotFoundException e) {
            System.out.println("McpAsyncClient not found: " + e.getMessage());
        }
        
        try {
            Class.forName("io.modelcontextprotocol.sdk.client.transport.StdioClientTransport");
            System.out.println("StdioClientTransport found");
        } catch (ClassNotFoundException e) {
            System.out.println("StdioClientTransport not found: " + e.getMessage());
        }
        
        try {
            Class.forName("io.modelcontextprotocol.sdk.client.transport.HttpClientSseClientTransport");
            System.out.println("HttpClientSseClientTransport found");
        } catch (ClassNotFoundException e) {
            System.out.println("HttpClientSseClientTransport not found: " + e.getMessage());
        }
        
        // Try alternative package structures
        try {
            Class.forName("io.modelcontextprotocol.McpClient");
            System.out.println("Alternative McpClient found");
        } catch (ClassNotFoundException e) {
            System.out.println("Alternative McpClient not found: " + e.getMessage());
        }
        
        try {
            Class.forName("io.modelcontextprotocol.client.McpClient");
            System.out.println("Alternative client.McpClient found");
        } catch (ClassNotFoundException e) {
            System.out.println("Alternative client.McpClient not found: " + e.getMessage());
        }
    }
}