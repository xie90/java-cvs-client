# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

```bash
# 构建项目
mvn package

# 运行（通过启动脚本）
./jcvs <command> [args...]

# 或直接使用 java
java -dfile.encoding=GBK -jar target/cvs-client-1.0-SNAPSHOT.jar <command> [args...]
```

## 项目架构

### 核心组件

- **CVSClient** (`src/main/java/com/lobsterxie/cvs/CVSClient.java`) - 客户端主入口，支持多服务器配置管理
- **ServerConfig** (`config/ServerConfig.java`) - 服务器配置数据类
- **ConfigHandle** (`handler/ConfigHandle.java`) - 配置文件管理（`~/.cvs/.cvs-servers.properties`）
- **AbstractHandle** (`handler/AbstractHandle.java`) - CVS 命令处理器基类

### Handler 包

每个 CVS 命令对应一个处理器：
- `CheckoutHandle` - checkout 检出
- `CommitHandle` - commit 提交
- `UpdateHandle` - update 更新
- `DiffHandle` - diff 差异
- `LogHandle` - log 日志
- `StatusHandle` - status 状态
- `AddHandle` - add 添加
- `RevertHandle` - revert 回退
- `LoginHandle` - login 登录

### 依赖

- `org.netbeans.lib:cvsclient:20060125` - NetBeans CVS 客户端库
- Java 17+

### 配置文件

- `~/.cvs/.cvs-servers.properties` - 主配置文件
- `~/.cvs/.cvs-servers.secure.properties` - 安全配置文件（存储密码）

### CVSROOT 格式

```
:pserver:用户名@主机名[:端口]/仓库路径
例如：:pserver:anoncvs@example.com:2401/repo
```
