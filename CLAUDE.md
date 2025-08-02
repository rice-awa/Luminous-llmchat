# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is **Luminous LLM Chat** - a Minecraft Fabric 1.21.7 mod that integrates LLM (Large Language Model) chat functionality. The mod provides intelligent AI assistance within Minecraft, supporting multiple AI providers, function calling, context management, and comprehensive administrative features.

## Build and Development Commands

### Build Commands
```bash
# Build the mod
./gradlew build

# Run tests
./gradlew test

# Generate test reports with coverage
./gradlew test jacocoTestReport

# Clean build artifacts  
./gradlew clean

# Build without tests (faster)
./gradlew build -x test
```

### Development Workflow
```bash
# Generate development environment
./gradlew genEclipseRuns
./gradlew genIntellijRuns

# Run client in development
./gradlew runClient

# Run server in development  
./gradlew runServer
```

### Testing Commands
```bash
# Run all tests
./gradlew test

# Run specific test classes
./gradlew test --tests "com.riceawa.llm.core.*"
./gradlew test --tests "com.riceawa.llm.function.*" 

# Run tests with detailed output
./gradlew test --info
```

## Architecture and Code Structure

### Core Architecture Components

**Main Entry Points:**
- `Lllmchat.java` - Server-side mod initializer, manages core systems
- `LllmchatClient.java` - Client-side entry point for UI integration

**Service Layer (`com.riceawa.llm.service/`):**
- `LLMServiceManager` - Manages multiple LLM service implementations
- `OpenAIService` - OpenAI-compatible API implementation with async chat
- All services implement `LLMService` interface for consistency

**Configuration System (`com.riceawa.llm.config/`):**
- `LLMChatConfig` - Singleton configuration manager with hot reload
- `Provider` - AI service provider configuration (OpenAI, OpenRouter, DeepSeek, etc.)
- Supports multiple providers, automatic config migration, version control

**Function Calling Framework (`com.riceawa.llm.function/`):**
- `FunctionRegistry` - Centralized function registration and discovery
- `LLMFunction` interface - Standard function contract
- `PermissionHelper` - Unified permission control system
- 13 built-in functions covering world info, player stats, admin operations
- OpenAI function calling API compliant

**Context Management (`com.riceawa.llm.context/`):**
- `ChatContextManager` - Manages per-player chat sessions
- `ChatContext` - Individual user conversation state
- Automatic context length management and history pruning

**Command System (`com.riceawa.llm.command/`):**
- `LLMChatCommand` - Main chat command with subcommands
- `HistoryCommand` - History management (export, search, stats)  
- `LogCommand` - Log system management
- Hierarchical permission system (player/OP)

### Key Design Patterns

**Singleton Pattern:** Configuration manager, service manager, function registry
**Factory Pattern:** LLM service creation based on provider type
**Observer Pattern:** Config change notifications and hot reload
**Template Method:** Prompt template system with variable substitution
**Strategy Pattern:** Different export formats for history data

### Function Calling System

The mod includes a comprehensive function calling framework with 13 built-in functions:

**Information Functions:** `get_world_info`, `get_player_stats`, `get_inventory`, `get_nearby_entities`, `get_server_info`
**Communication:** `send_message`  
**Admin Functions:** `execute_command`, `set_block`, `summon_entity`, `teleport_player`, `control_weather`, `control_time`

**Security Features:**
- Unified permission checking via `PermissionHelper`
- Command blacklist for dangerous operations
- Distance limitations for world operations
- Parameter validation with JSON Schema

### Configuration Management

**Multi-Provider Support:** Single config file supports multiple AI providers
**Hot Reload:** Runtime config changes without restart
**Version Migration:** Automatic upgrade of config format
**Default Generation:** Auto-creates missing providers on first run

**Key Config Paths:**
- `config/lllmchat/config.json` - Main configuration
- `config/lllmchat/prompt_templates.json` - AI prompt templates

### Data Persistence

**History System:** SQLite-based storage with retention policies
**Logging System:** Structured JSON logs with rotation and compression
**Export Formats:** JSON, CSV, TXT, HTML support for data portability

## Testing Framework

The project uses JUnit 5 + Mockito with comprehensive test coverage:

**Test Structure:**
- 8 test classes covering all major components
- 70+ test methods with integration and unit tests
- Mock-based testing for Minecraft server environment
- API compatibility verification with OpenAI standards

**Key Test Areas:**
- Function calling parameter validation and execution
- Permission system enforcement
- Configuration management and hot reload
- LLM service integration and error handling
- Command processing and response formatting

## Development Guidelines

### Adding New LLM Services
Implement the `LLMService` interface and register with `LLMServiceManager`. Follow the OpenAI-compatible request/response format for consistency.

### Adding New Functions
Implement `LLMFunction` interface, use `PermissionHelper` for access control, register with `FunctionRegistry`. Include comprehensive parameter validation and error handling.

### Configuration Changes
Update `LLMChatConfig` class, increment config version, add migration logic for backwards compatibility.

### Testing Requirements
All new functionality must include unit tests. Use mock objects for Minecraft components. Test both success and failure scenarios, especially for permission-controlled features.

### Post-Development Testing
After implementing new features, always perform core functionality testing:

1. **Build Verification**
   ```bash
   # Ensure clean build
   ./gradlew clean build
   ```

2. **Unit Test Execution**
   ```bash
   # Run all tests to ensure no regressions
   ./gradlew test
   ```

## Important File Locations

**Source Code:** `src/main/java/com/riceawa/`
**Resources:** `src/main/resources/`
**Tests:** `src/test/java/com/riceawa/`
**Config:** Runtime config in `config/lllmchat/`
**Mod Metadata:** `src/main/resources/fabric.mod.json`

## Dependencies and Versions

- **Minecraft:** 1.21.7
- **Fabric Loader:** Latest stable
- **Fabric API:** 0.129.0+1.21.7
- **Java:** 21 (source and target compatibility)
- **OkHttp3:** 4.12.0 (HTTP client)
- **Gson:** 2.10.1 (JSON processing)
- **Typesafe Config:** 1.4.3 (configuration)

The mod uses Gradle with Fabric Loom for build management and includes all dependencies in the final jar.