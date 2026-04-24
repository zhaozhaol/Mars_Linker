# NexusMQ

把「自建 MQTT、与现网 EMQX 行为一致、device_logic 少改」从想法落到**可运行环境 + 可演进代码仓库**。

执行顺序：**技术设计 → 开发计划 → 代码迭代**（见下方文档）。

## 文档（设计 / 计划）

| 顺序 | 文档 |
|------|------|
| 1 | [doc/NexusMQ技术设计说明书.md](doc/MarsLinker技术设计说明书) |
| 2 | [doc/NexusMQ开发计划.md](doc/MarsLinker开发计划) |
| 3 | [doc/NexusMQ从0到1落地路线图.md](doc/MarsLinker从0到1落地路线图) |

## 模块：nexus-mqtt-broker（阶段 0～3 持续落地）

- **作用**：自研 MQTT Broker 的 **Spring Boot 宿主进程**；当前可构建、可启动、暴露 Actuator。
- **构建**：仓库根目录执行 `mvn clean package`，产物 `nexus-mqtt-broker/target/nexus-mqtt-broker-*.jar`。
- **运行**：`java -jar nexus-mqtt-broker/target/nexus-mqtt-broker-1.0-SNAPSHOT.jar`，健康检查 <http://localhost:8080/actuator/health>。
- **说明**：默认 **不启用** Netty MQTT 监听（`nexus.mqtt.broker.netty-enabled=false`），避免与本机 `docker-compose` 中的 EMQX 抢端口。
- **启用自研 Broker（阶段 3～4 持续演进）**：
  `java -jar ... --nexus.mqtt.broker.netty-enabled=true --nexus.mqtt.broker.tcp-port=11883`
  - **已支持**：CONNECT/CONNACK、用户名密码鉴权（可开关）、Will、PINGREQ/PINGRESP、SUBSCRIBE/SUBACK（精确/通配符 `+/#`/`$share`）、UNSUBSCRIBE/UNSUBACK、PUBLISH QoS0/1（投递 QoS=min(发布,订阅)、上行 QoS1 回 PUBACK、下行 QoS1 inflight + 可选重传(DUP=1)）。
  - **未支持（后续）**：ACL、Retain 持久化、QoS2、会话持久化/离线消息、集群会话一致性等（见 `doc/`）。

### 自研 Broker 关键配置（摘要）

- **监听开关与端口**
  - `nexus.mqtt.broker.netty-enabled=true`
  - `nexus.mqtt.broker.tcp-port=11883`
- **鉴权（可选）**
  - `nexus.mqtt.broker.auth-enabled=true`
  - `nexus.mqtt.broker.auth-username=...`
  - `nexus.mqtt.broker.auth-password=...`
- **下行 QoS1 重传（可选）**
  - `nexus.mqtt.broker.qos1-retransmit-enabled=true`
  - `nexus.mqtt.broker.qos1-retransmit-interval-ms=5000`
  - `nexus.mqtt.broker.qos1-retransmit-max-attempts=3`
- **ACL 增强（可选）**
  - `nexus.mqtt.broker.acl-enabled=true`
  - `nexus.mqtt.broker.acl-default-deny=true|false`
  - `nexus.mqtt.broker.acl-allow-subscribe-prefixes[0]=dev/`
  - `nexus.mqtt.broker.acl-allow-publish-prefixes[0]=up/`
  - `nexus.mqtt.broker.acl-deny-subscribe-prefixes[0]=sys/`
  - `nexus.mqtt.broker.acl-deny-publish-prefixes[0]=sys/`

## 第一步：本机跑 MQTT（约 5 分钟，联调 EMQX）

前置：已安装 [Docker](https://docs.docker.com/get-docker/) 与 Docker Compose v2。

```bash
cd <本仓库根目录>
docker compose up -d
```

- **Dashboard**：<http://localhost:18083>（默认 `admin` / `public`，**登录后请立即修改密码**）
- **MQTT 端口**：`1883`（明文 TCP 上的 MQTT）

验证连通（任选其一）：

- 使用 [MQTTX](https://mqttx.app/) 新建连接：`localhost:1883`
- 或使用 Mosquitto 客户端订阅/发布测试主题

停止：

```bash
docker compose down
```

## 文档

| 文件 | 说明 |
|------|------|
| [doc/NexusMQ从0到1落地路线图.md](doc/MarsLinker从0到1落地路线图) | **从只有想法到可执行**，阶段与决策点 |
| [doc/设备接入MQTT技术架构与开发规划.md](doc/设备接入MQTT技术架构与开发规划.md) | 万级连接、4 实例、Rabbit 单机架构 |
| [doc/小规模EMQX兼容Broker落地计划.md](doc/小规模EMQX兼容Broker落地计划.md) | 与 EMQX 对齐的检查思路 |
| [doc/NexusMQ基线对齐清单.md](doc/MarsLinker基线对齐清单) | 文档目标与当前实现状态对齐 |
| [doc/NexusMQ生产化最小手册.md](doc/NexusMQ生产化最小手册.md) | 容器化、监控、压测、回滚最小闭环 |
| [doc/NexusMQ自研Broker增强PoC与决策门.md](doc/MarsLinker自研Broker增强PoC与决策门) | 自研增强分批 PoC 与 go/no-go 标准 |

## 与 device_logic 联调（概要）

1. 在 EMQX Dashboard 中创建与线上一致风格的 **用户名/密码** 与 **ACL**（测试环境可用宽松策略起步，再收紧）。
2. 将 `device_logic` 配置中的 `mqtt.host` 改为 `tcp://<本机或服务器IP>:1883`（或映射到你们习惯的端口）。
3. 按 `doc` 中清单核对 **`$share` 主题** 与 **ClientId 唯一性**。

## 说明

- `docker-compose.yml` 中的 EMQX **版本号可固定为团队评审通过的版本**；升级前请在测试环境验证。
- 生产环境请另行配置 TLS、强密码、资源限制与监控，勿直接使用开发默认口令。
