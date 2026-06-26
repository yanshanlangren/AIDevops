# L3 自动创建 PR Demo

Java 8 / Spring Boot REST 原型。Spring Boot 启动时只提供 HTTP 服务，不读取固定 IncidentContext，不克隆仓库，也不调用模型。分析和 PR 流程均作为后台异步任务执行。

IncidentContext 由调用方作为 JSON 请求体传入：

```text
HTTP IncidentContext
  -> 返回 202 + taskId
  -> 后台异步执行
  -> L3 准入
  -> 克隆远端 GitHub 仓库
  -> 代码检索
  -> OpenAI-compatible 模型
  -> Diff 门禁
  -> 编译、测试、静态检查
  -> GitHub Pull Request
  -> 审计归档
```

## 配置

所有运行配置统一位于：

[application.yml](src/main/resources/application.yml)

至少需要配置远端仓库和模型：

```yaml
repo:
  target-repo-name: historical-query-demo
  clone-url: https://github.com/OWNER/REPO.git
  default-branch: main

github:
  owner: OWNER
  repo: REPO
  target-branch: main

model:
  provider: openai-compatible
  api-base-url: https://your-model.example/v1
  model-name: your-model
```

密钥通过环境变量注入：

```bash
export GITHUB_TOKEN='...'
export LLM_API_KEY='...'
```

## 启动服务

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 1.8)
mvn clean package
java -jar target/ai-devops-pr-demo-1.0.0-SNAPSHOT.jar
```

默认监听 `8080`。

## IncidentContext 分析接口

```http
POST /api/v1/incidents/analyze
Content-Type: application/json
```

该接口立即返回 `202 Accepted` 和 `taskId`。后台任务克隆并检索远端仓库，生成根因假设、变更计划和测试计划，但不应用补丁。

```bash
curl -X POST http://localhost:8080/api/v1/incidents/analyze \
  -H 'Content-Type: application/json' \
  --data-binary @incidents/incident-rowkey.json
```

提交响应：

```json
{
  "taskId": "a-request-specific-uuid",
  "incidentId": "INC-HMS-ROWKEY-000001",
  "status": "QUEUED",
  "statusUrl": "/api/v1/tasks/a-request-specific-uuid"
}
```

## PR 流程接口

```http
POST /api/v1/incidents/pull-requests?dryRun=true
Content-Type: application/json
```

请求体仍为完整 IncidentContext。

接口立即返回 `202 Accepted`。Dry Run 后台任务会执行补丁和工程验证，但不提交、不推送、不创建 PR：

```bash
curl -X POST 'http://localhost:8080/api/v1/incidents/pull-requests?dryRun=true' \
  -H 'Content-Type: application/json' \
  --data-binary @incidents/incident-rowkey.json
```

创建真实 PR：

```bash
curl -X POST 'http://localhost:8080/api/v1/incidents/pull-requests?dryRun=false' \
  -H 'Content-Type: application/json' \
  --data-binary @incidents/incident-rowkey.json
```

未传 `dryRun` 时使用 `application.yml` 中的 `app.dry-run`。

## 查询任务状态

```bash
curl http://localhost:8080/api/v1/tasks/{taskId}
```

任务状态：

- `QUEUED`：等待后台线程执行。
- `RUNNING`：正在克隆、检索、调用模型或执行工程验证。
- `SUCCEEDED`：后台任务完成，最终工作流结果位于 `result`。
- `FAILED`：后台任务异常，错误摘要位于 `error`，完整堆栈记录在服务日志。

每次任务使用独立 `taskId`，工作区和审计目录相互隔离。

## 模型上下文裁剪

代码检索不会再把完整大文件发送给模型。系统根据 IncidentContext 中的类名、方法名、疑似组件和近期文件路径，提取命中位置附近的行号化代码片段。

相关配置：

```yaml
model:
  max-input-chars: 60000
  max-file-chars: 12000
  max-files: 10
  snippet-context-lines: 60
  max-snippets-per-file: 3
```

## 控制边界

- `analyze` 不修改仓库。
- PR 接口必须通过 L3 准入、补丁门禁和工程验证。
- 默认 `app.dry-run: true`。
- 只提交门禁确认过的源码和测试文件。
- 不自动合并，不自动发布，不修改生产配置。
- 失败时返回结构化错误或降级结果，并保留审计记录。
