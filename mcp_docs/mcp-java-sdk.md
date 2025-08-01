# Java MCP Client

> Learn how to use the Model Context Protocol (MCP) client to interact with MCP servers

Go to the [Java MCP Server](/sdk/java/mcp-server) to learn how to build MCP servers or the [Java MCP Overview](/sdk/java/mcp-overview) to understand the overall architecture.

# Model Context Protocol Client

The MCP Client is a key component in the Model Context Protocol (MCP) architecture, responsible for establishing and managing connections with MCP servers. It implements the client-side of the protocol, handling:

* Protocol version negotiation to ensure compatibility with servers
* Capability negotiation to determine available features
* Message transport and JSON-RPC communication
* Tool discovery and execution
* Resource access and management
* Prompt system interactions
* Optional features like roots management and sampling support

<Tip>
  The core `io.modelcontextprotocol.sdk:mcp` module provides STDIO and SSE client transport implementations without requiring external web frameworks.

  Spring-specific transport implementations are available as an **optional** dependency `io.modelcontextprotocol.sdk:mcp-spring-webflux` for [Spring Framework](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-client-boot-starter-docs.html) users.
</Tip>

<Tip>
  This [quickstart demo](/quickstart/client), based on Spring AI MCP, will show
  you how to build an AI client that connects to MCP servers.
</Tip>

The client provides both synchronous and asynchronous APIs for flexibility in different application contexts.

<Tabs>
  <Tab title="Sync API">
    ```java
    // Create a sync client with custom configuration
    McpSyncClient client = McpClient.sync(transport)
        .requestTimeout(Duration.ofSeconds(10))
        .capabilities(ClientCapabilities.builder()
            .roots(true)      // Enable roots capability
            .sampling()       // Enable sampling capability
            .build())
        .sampling(request -> new CreateMessageResult(response))
        .build();

    // Initialize connection
    client.initialize();

    // List available tools
    ListToolsResult tools = client.listTools();

    // Call a tool
    CallToolResult result = client.callTool(
        new CallToolRequest("calculator",
            Map.of("operation", "add", "a", 2, "b", 3))
    );

    // List and read resources
    ListResourcesResult resources = client.listResources();
    ReadResourceResult resource = client.readResource(
        new ReadResourceRequest("resource://uri")
    );

    // List and use prompts
    ListPromptsResult prompts = client.listPrompts();
    GetPromptResult prompt = client.getPrompt(
        new GetPromptRequest("greeting", Map.of("name", "Spring"))
    );

    // Add/remove roots
    client.addRoot(new Root("file:///path", "description"));
    client.removeRoot("file:///path");

    // Close client
    client.closeGracefully();
    ```
  </Tab>

  <Tab title="Async API">
    ```java
    // Create an async client with custom configuration
    McpAsyncClient client = McpClient.async(transport)
        .requestTimeout(Duration.ofSeconds(10))
        .capabilities(ClientCapabilities.builder()
            .roots(true)      // Enable roots capability
            .sampling()       // Enable sampling capability
            .build())
        .sampling(request -> Mono.just(new CreateMessageResult(response)))
        .toolsChangeConsumer(tools -> Mono.fromRunnable(() -> {
            logger.info("Tools updated: {}", tools);
        }))
        .resourcesChangeConsumer(resources -> Mono.fromRunnable(() -> {
            logger.info("Resources updated: {}", resources);
        }))
        .promptsChangeConsumer(prompts -> Mono.fromRunnable(() -> {
            logger.info("Prompts updated: {}", prompts);
        }))
        .build();

    // Initialize connection and use features
    client.initialize()
        .flatMap(initResult -> client.listTools())
        .flatMap(tools -> {
            return client.callTool(new CallToolRequest(
                "calculator",
                Map.of("operation", "add", "a", 2, "b", 3)
            ));
        })
        .flatMap(result -> {
            return client.listResources()
                .flatMap(resources ->
                    client.readResource(new ReadResourceRequest("resource://uri"))
                );
        })
        .flatMap(resource -> {
            return client.listPrompts()
                .flatMap(prompts ->
                    client.getPrompt(new GetPromptRequest(
                        "greeting",
                        Map.of("name", "Spring")
                    ))
                );
        })
        .flatMap(prompt -> {
            return client.addRoot(new Root("file:///path", "description"))
                .then(client.removeRoot("file:///path"));
        })
        .doFinally(signalType -> {
            client.closeGracefully().subscribe();
        })
        .subscribe();
    ```
  </Tab>
</Tabs>

## Client Transport

The transport layer handles the communication between MCP clients and servers, providing different implementations for various use cases. The client transport manages message serialization, connection establishment, and protocol-specific communication patterns.

<Tabs>
  <Tab title="STDIO">
    Creates transport for in-process based communication

    ```java
    ServerParameters params = ServerParameters.builder("npx")
        .args("-y", "@modelcontextprotocol/server-everything", "dir")
        .build();
    McpTransport transport = new StdioClientTransport(params);
    ```
  </Tab>

  <Tab title="SSE (HttpClient)">
    Creates a framework agnostic (pure Java API) SSE client transport. Included in the core mcp module.

    ```java
    McpTransport transport = new HttpClientSseClientTransport("http://your-mcp-server");
    ```
  </Tab>

  <Tab title="SSE (WebFlux)">
    Creates WebFlux-based SSE client transport. Requires the mcp-webflux-sse-transport dependency.

    ```java
    WebClient.Builder webClientBuilder = WebClient.builder()
        .baseUrl("http://your-mcp-server");
    McpTransport transport = new WebFluxSseClientTransport(webClientBuilder);
    ```
  </Tab>
</Tabs>

## Client Capabilities

The client can be configured with various capabilities:

```java
var capabilities = ClientCapabilities.builder()
    .roots(true)      // Enable filesystem roots support with list changes notifications
    .sampling()       // Enable LLM sampling support
    .build();
```

### Roots Support

Roots define the boundaries of where servers can operate within the filesystem:

```java
// Add a root dynamically
client.addRoot(new Root("file:///path", "description"));

// Remove a root
client.removeRoot("file:///path");

// Notify server of roots changes
client.rootsListChangedNotification();
```

The roots capability allows servers to:

* Request the list of accessible filesystem roots
* Receive notifications when the roots list changes
* Understand which directories and files they have access to

### Sampling Support

Sampling enables servers to request LLM interactions ("completions" or "generations") through the client:

```java
// Configure sampling handler
Function<CreateMessageRequest, CreateMessageResult> samplingHandler = request -> {
    // Sampling implementation that interfaces with LLM
    return new CreateMessageResult(response);
};

// Create client with sampling support
var client = McpClient.sync(transport)
    .capabilities(ClientCapabilities.builder()
        .sampling()
        .build())
    .sampling(samplingHandler)
    .build();
```

This capability allows:

* Servers to leverage AI capabilities without requiring API keys
* Clients to maintain control over model access and permissions
* Support for both text and image-based interactions
* Optional inclusion of MCP server context in prompts

### Logging Support

The client can register a logging consumer to receive log messages from the server and set the minimum logging level to filter messages:

```java
var mcpClient = McpClient.sync(transport)
        .loggingConsumer(notification -> {
            System.out.println("Received log message: " + notification.data());
        })
        .build();

mcpClient.initialize();

mcpClient.setLoggingLevel(McpSchema.LoggingLevel.INFO);

// Call the tool that can sends logging notifications
CallToolResult result = mcpClient.callTool(new McpSchema.CallToolRequest("logging-test", Map.of()));
```

Clients can control the minimum logging level they receive through the `mcpClient.setLoggingLevel(level)` request. Messages below the set level will be filtered out.
Supported logging levels (in order of increasing severity): DEBUG (0), INFO (1), NOTICE (2), WARNING (3), ERROR (4), CRITICAL (5), ALERT (6), EMERGENCY (7)

## Using MCP Clients

### Tool Execution

Tools are server-side functions that clients can discover and execute. The MCP client provides methods to list available tools and execute them with specific parameters. Each tool has a unique name and accepts a map of parameters.

<Tabs>
  <Tab title="Sync API">
    ```java
    // List available tools and their names
    var tools = client.listTools();
    tools.forEach(tool -> System.out.println(tool.getName()));

    // Execute a tool with parameters
    var result = client.callTool("calculator", Map.of(
        "operation", "add",
        "a", 1,
        "b", 2
    ));
    ```
  </Tab>

  <Tab title="Async API">
    ```java
    // List available tools asynchronously
    client.listTools()
        .doOnNext(tools -> tools.forEach(tool ->
            System.out.println(tool.getName())))
        .subscribe();

    // Execute a tool asynchronously
    client.callTool("calculator", Map.of(
            "operation", "add",
            "a", 1,
            "b", 2
        ))
        .subscribe();
    ```
  </Tab>
</Tabs>

### Resource Access

Resources represent server-side data sources that clients can access using URI templates. The MCP client provides methods to discover available resources and retrieve their contents through a standardized interface.

<Tabs>
  <Tab title="Sync API">
    ```java
    // List available resources and their names
    var resources = client.listResources();
    resources.forEach(resource -> System.out.println(resource.getName()));

    // Retrieve resource content using a URI template
    var content = client.getResource("file", Map.of(
        "path", "/path/to/file.txt"
    ));
    ```
  </Tab>

  <Tab title="Async API">
    ```java
    // List available resources asynchronously
    client.listResources()
        .doOnNext(resources -> resources.forEach(resource ->
            System.out.println(resource.getName())))
        .subscribe();

    // Retrieve resource content asynchronously
    client.getResource("file", Map.of(
            "path", "/path/to/file.txt"
        ))
        .subscribe();
    ```
  </Tab>
</Tabs>

### Prompt System

The prompt system enables interaction with server-side prompt templates. These templates can be discovered and executed with custom parameters, allowing for dynamic text generation based on predefined patterns.

<Tabs>
  <Tab title="Sync API">
    ```java
    // List available prompt templates
    var prompts = client.listPrompts();
    prompts.forEach(prompt -> System.out.println(prompt.getName()));

    // Execute a prompt template with parameters
    var response = client.executePrompt("echo", Map.of(
        "text", "Hello, World!"
    ));
    ```
  </Tab>

  <Tab title="Async API">
    ```java
    // List available prompt templates asynchronously
    client.listPrompts()
        .doOnNext(prompts -> prompts.forEach(prompt ->
            System.out.println(prompt.getName())))
        .subscribe();

    // Execute a prompt template asynchronously
    client.executePrompt("echo", Map.of(
            "text", "Hello, World!"
        ))
        .subscribe();
    ```
  </Tab>
</Tabs>

### Using Completion

As part of the [Completion capabilities](/specification/2025-03-26/server/utilities/completion), MCP provides a standardized way for servers to offer argument autocompletion suggestions for prompts and resource URIs.

Check the [Server Completion capabilities](/sdk/java/mcp-server#completion-specification) to learn how to enable and configure completions on the server side.

On the client side, the MCP client provides methods to request auto-completions:

<Tabs>
  <Tab title="Sync API">
    ```java

    CompleteRequest request = new CompleteRequest(
            new PromptReference("code_review"),
            new CompleteRequest.CompleteArgument("language", "py"));

    CompleteResult result = syncMcpClient.completeCompletion(request);

    ```
  </Tab>

  <Tab title="Async API">
    ```java

    CompleteRequest request = new CompleteRequest(
            new PromptReference("code_review"),
            new CompleteRequest.CompleteArgument("language", "py"));

    Mono<CompleteResult> result = mcpClient.completeCompletion(request);

    ```
  </Tab>
</Tabs>
