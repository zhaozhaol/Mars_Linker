# Mars Linker

`Mars_Linker` 是一个 MQTT Broker 与设备链路相关的工程，当前核心模块为 `mars-linker-broker`。

## 项目结构

- `mars-linker-broker`: Broker 核心实现（Netty、协议处理、会话/retain 存储）
- `doc`: 设计、执行状态与计划文档
- `tools`: 压测与辅助脚本

## 快速开始

1. 准备 JDK 11+ 与 Maven 3.8+
2. 进入项目根目录执行：

```bash
mvn -pl mars-linker-broker -am clean package
```

3. 通过 Spring Boot 配置文件设置 Broker 参数后启动服务

## 持久化配置说明

持久化现在有两层配置：

1. **总开关**：`storage-enabled`
2. **存储类型**：`storage-mode`（`file` / `redis` / `db`）

当 `storage-enabled=false` 时，系统使用内存 no-op 存储，不会写文件、不会连接 Redis/DB，也不会执行启动迁移。

### 示例 1：关闭持久化（仅内存）

```yaml
mars:
  linker:
    broker:
      storage-enabled: false
```

### 示例 2：开启持久化 + 文件存储

```yaml
mars:
  linker:
    broker:
      storage-enabled: true
      storage-mode: file
      session-store-file-path: data/session-store.tsv
      retain-store-file-path: data/retain-store.tsv
```

### 示例 3：开启持久化 + Redis 存储

```yaml
mars:
  linker:
    broker:
      storage-enabled: true
      storage-mode: redis
      storage-redis-address: redis://127.0.0.1:6379
      storage-redis-database: 0
      storage-redis-key-prefix: ml
```

### 示例 4：开启持久化 + DB 存储

```yaml
mars:
  linker:
    broker:
      storage-enabled: true
      storage-mode: db
      storage-db-jdbc-url: jdbc:mysql://127.0.0.1:3306/mars_linker
      storage-db-username: root
      storage-db-password: your_password
      storage-db-schema: public
      storage-db-table-prefix: ml_
```

## 离线消息与 Retain 限制配置

可通过以下配置控制持久化数据规模与保留时长：

- `session-offline-max-messages`: 每个客户端离线消息最大保留条数
- `session-offline-ttl-ms`: 离线消息 TTL（毫秒）
- `retain-max-messages`: retain 总条数上限
- `retain-ttl-ms`: retain TTL（毫秒）

## 说明

- `storage-mode` 仅在 `storage-enabled=true` 时生效
- 启动迁移（`storage-migrate-on-startup`）仅在启用持久化时生效

## 生产推荐配置模板

以下为建议起步配置（可按业务量调整）：

```yaml
mars:
  linker:
    broker:
      netty-enabled: true
      tcp-port: 11883
      max-connections: 200000

      # 持久化总开关 + 类型
      storage-enabled: true
      storage-mode: redis
      storage-redis-address: redis://127.0.0.1:6379
      storage-redis-database: 0
      storage-redis-timeout-ms: 3000
      storage-redis-key-prefix: ml_prod

      # 启动迁移（首次切换存储时按需开启，完成后建议关闭）
      storage-migrate-on-startup: false

      # 离线消息与 retain 限额（按业务规模调整）
      session-offline-max-messages: 5000
      session-offline-ttl-ms: 604800000   # 7天
      retain-max-messages: 200000
      retain-ttl-ms: 2592000000           # 30天

      # 安全能力（按需）
      auth-enabled: true
      auth-mode: static
      acl-enabled: true
      acl-mode: static
```

## 压测指南（你本机执行）

### 1) 环境准备

- 安装 `k6`
- 安装支持 MQTT 的 `xk6` 扩展（`xk6-mqtt`）
- 确保 Broker 已启动，监听端口与压测参数一致（默认 `11883`）

> 当前仓库压测脚本位于 `tools`，入口脚本是 `tools/run-load-and-report.ps1`。

### 2) 快速冒烟压测

在项目根目录执行：

```powershell
powershell -ExecutionPolicy Bypass -File "tools/run-load-and-report.ps1" -Broker "tcp://127.0.0.1:11883" -Vus 20 -Duration "1m" -Qos 1
```

输出文件：

- `tools/out/load-summary.json`
- `tools/out/load-report.md`

### 3) 分阶段压测建议

按下面节奏逐步放量，每一步都观察成功率和 p95：

- 阶段 A：`Vus=20`, `Duration=1m`
- 阶段 B：`Vus=50`, `Duration=2m`
- 阶段 C：`Vus=100`, `Duration=3m`
- 阶段 D：`Vus=200`, `Duration=5m`

示例（阶段 C）：

```powershell
powershell -ExecutionPolicy Bypass -File "tools/run-load-and-report.ps1" -Broker "tcp://127.0.0.1:11883" -Vus 100 -Duration "3m" -Qos 1
```

### 4) 重点关注指标

- `mqtt_success_rate`: 建议 `>= 0.99`
- `mqtt_connect_ms p95`: 建议 `< 1500ms`
- `mqtt_publish_ms p95`: 建议 `< 1200ms`
- Broker 进程 CPU、内存、GC 抖动
- Redis/DB 端 CPU、慢查询、连接数

### 5) 对比压测建议

建议固定同一批压测参数，分别验证下面几组配置，对比 `load-report.md`：

- `storage-enabled=false`（纯内存）
- `storage-enabled=true` + `storage-mode=file`
- `storage-enabled=true` + `storage-mode=redis`
- `storage-enabled=true` + `storage-mode=db`

这样可以直接看出持久化方式对吞吐与时延的影响。