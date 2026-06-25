# L3 自动创建 PR Demo

Java 8 / Spring Boot REST 原型。Spring Boot 启动时只提供 HTTP 服务，不读取固定 IncidentContext，不克隆仓库，也不调用模型。

IncidentContext 由调用方作为 JSON 请求体传入：

```text
HTTP IncidentContext
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

该接口克隆并检索远端仓库，返回根因假设、变更计划和测试计划，但不应用补丁。

```bash
curl -X POST http://localhost:8080/api/v1/incidents/analyze \
  -H 'Content-Type: application/json' \
  --data-binary @incidents/incident-rowkey.json
```

## PR 流程接口

```http
POST /api/v1/incidents/pull-requests?dryRun=true
Content-Type: application/json
```

请求体仍为完整 IncidentContext。

Dry Run 会执行补丁和工程验证，但不提交、不推送、不创建 PR：

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

## 响应示例

```json
{
  "taskId": "a-request-specific-uuid",
  "incidentId": "INC-DEMO-000001",
  "status": "DIAGNOSED",
  "stage": "diagnosis",
  "message": "Diagnosis and change plan generated; repository was not modified",
  "auditDirectory": "/path/audit/INC-DEMO-000001/a-request-specific-uuid",
  "pullRequestUrl": null,
  "analysis": {
    "root_cause_hypothesis": "...",
    "confidence": 0.85,
    "change_plan": ["..."],
    "target_files": ["..."],
    "test_plan": ["..."]
  }
}
```

每次请求生成独立 `taskId`，工作区和审计目录相互隔离。

## 控制边界

- `analyze` 不修改仓库。
- PR 接口必须通过 L3 准入、补丁门禁和工程验证。
- 默认 `app.dry-run: true`。
- 只提交门禁确认过的源码和测试文件。
- 不自动合并，不自动发布，不修改生产配置。
- 失败时返回结构化错误或降级结果，并保留审计记录。
