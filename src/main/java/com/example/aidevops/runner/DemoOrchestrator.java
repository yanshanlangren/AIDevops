package com.example.aidevops.runner;

import com.example.aidevops.audit.AuditService;
import com.example.aidevops.audit.AuditSession;
import com.example.aidevops.config.AppProperties;
import com.example.aidevops.config.GithubProperties;
import com.example.aidevops.config.ModelProperties;
import com.example.aidevops.config.OutputProperties;
import com.example.aidevops.config.PolicyProperties;
import com.example.aidevops.github.GithubPullRequestClient;
import com.example.aidevops.github.PullRequestBodyBuilder;
import com.example.aidevops.llm.LlmGateway;
import com.example.aidevops.llm.PromptBuilder;
import com.example.aidevops.model.AdmissionResult;
import com.example.aidevops.model.DemoResult;
import com.example.aidevops.model.IncidentContext;
import com.example.aidevops.model.LlmPlan;
import com.example.aidevops.model.PatchApplyCheckResult;
import com.example.aidevops.model.PatchValidationResult;
import com.example.aidevops.model.PrResult;
import com.example.aidevops.model.RetrievalResult;
import com.example.aidevops.model.VerificationResult;
import com.example.aidevops.patch.PatchService;
import com.example.aidevops.policy.AdmissionService;
import com.example.aidevops.repo.RepoManager;
import com.example.aidevops.repo.RepoWorkspace;
import com.example.aidevops.report.ManualReportService;
import com.example.aidevops.retriever.CodeRetriever;
import com.example.aidevops.security.SecretRedactor;
import com.example.aidevops.testexec.TestRunner;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DemoOrchestrator {
    private static final Logger log = LoggerFactory.getLogger(DemoOrchestrator.class);

    private final AdmissionService admissionService;
    private final AuditService auditService;
    private final RepoManager repoManager;
    private final CodeRetriever codeRetriever;
    private final PromptBuilder promptBuilder;
    private final LlmGateway llmGateway;
    private final PatchService patchService;
    private final TestRunner testRunner;
    private final ManualReportService manualReportService;
    private final PullRequestBodyBuilder prBodyBuilder;
    private final GithubPullRequestClient githubClient;
    private final OutputProperties output;
    private final GithubProperties github;
    private final ModelProperties model;
    private final PolicyProperties policy;
    private final AppProperties app;
    private final ObjectMapper mapper;
    private final SecretRedactor redactor;

    public DemoOrchestrator(AdmissionService admissionService,
                            AuditService auditService,
                            RepoManager repoManager,
                            CodeRetriever codeRetriever,
                            PromptBuilder promptBuilder,
                            LlmGateway llmGateway,
                            PatchService patchService,
                            TestRunner testRunner,
                            ManualReportService manualReportService,
                            PullRequestBodyBuilder prBodyBuilder,
                            GithubPullRequestClient githubClient,
                            OutputProperties output,
                            GithubProperties github,
                            ModelProperties model,
                            PolicyProperties policy,
                            AppProperties app,
                            ObjectMapper mapper,
                            SecretRedactor redactor) {
        this.admissionService = admissionService;
        this.auditService = auditService;
        this.repoManager = repoManager;
        this.codeRetriever = codeRetriever;
        this.promptBuilder = promptBuilder;
        this.llmGateway = llmGateway;
        this.patchService = patchService;
        this.testRunner = testRunner;
        this.manualReportService = manualReportService;
        this.prBodyBuilder = prBodyBuilder;
        this.githubClient = githubClient;
        this.output = output;
        this.github = github;
        this.model = model;
        this.policy = policy;
        this.app = app;
        this.mapper = mapper;
        this.redactor = redactor;
    }

    public DemoResult analyze(IncidentContext incident, String taskId) {
        return execute("diagnose", incident, Boolean.TRUE, taskId);
    }

    public DemoResult generatePullRequest(
            IncidentContext incident, Boolean dryRunOverride, String taskId) {
        return execute("run", incident, dryRunOverride, taskId);
    }

    private DemoResult execute(
            String mode, IncidentContext incident, Boolean dryRunOverride, String taskId) {
        validateRequest(incident);
        if (taskId == null || taskId.trim().isEmpty()) {
            throw new IllegalArgumentException("taskId is required");
        }
        log.info("Incident workflow started: taskId={}, incidentId={}, mode={}, dryRunOverride={}",
                taskId, incident.getIncidentId(), mode, dryRunOverride);
        AuditSession audit = auditService.start(incident.getIncidentId(), taskId);
        Instant start = Instant.now();
        audit.writeJson("incident-context.json", redacted(incident));

        AdmissionResult admission = admissionService.evaluate(incident);
        audit.writeJson("admission-result.json", admission);
        log.info("L3 admission evaluated: taskId={}, incidentId={}, admitted={}, level={}, blockedReasons={}",
                taskId, incident.getIncidentId(), admission.isAdmitted(),
                admission.getLevel(), admission.getBlockedReasons().size());
        if ("run".equals(mode) && !admission.isAdmitted()) {
            manualReportService.write(audit, incident, "L3 admission", admission.getBlockedReasons(), null);
            return finish(audit, incident, taskId, start, "DOWNGRADED", "L3 admission",
                    "Incident was downgraded to L2", null, null);
        }

        try (RepoWorkspace workspace = repoManager.prepare(incident, taskId)) {
            log.info("Repository workspace prepared: taskId={}, incidentId={}, branch={}, directory={}",
                    taskId, incident.getIncidentId(), workspace.getBranch(), workspace.getDirectory());
            RetrievalResult retrieval = codeRetriever.retrieve(workspace.getDirectory(), incident);
            audit.writeJson("retrieval-result.json", redacted(retrieval));
            log.info("Code retrieval completed: taskId={}, incidentId={}, targetFiles={}, ignoredFiles={}",
                    taskId, incident.getIncidentId(), retrieval.getTargetFiles().size(),
                    retrieval.getIgnoredFiles().size());
            if (retrieval.getTargetFiles().isEmpty()) {
                return downgrade(audit, incident, taskId, start, "code retrieval",
                        list("no related code files were found"), null);
            }

            String prompt = promptBuilder.build(incident, retrieval);
            audit.writeText("prompt.txt", prompt);
            log.info("Calling model for diagnosis: taskId={}, incidentId={}, candidateFiles={}",
                    taskId, incident.getIncidentId(), retrieval.getTargetFiles().size());
            LlmPlan plan = llmGateway.generate(prompt);
            audit.writeJson("llm-output.json", redacted(plan));
            log.info("Model diagnosis completed: taskId={}, incidentId={}, confidence={}, targetFiles={}",
                    taskId, incident.getIncidentId(), plan.getConfidence(),
                    plan.getTargetFiles() == null ? 0 : plan.getTargetFiles().size());

            if ("diagnose".equals(mode)) {
                writeDiagnosis(audit, incident, plan);
                return finish(audit, incident, taskId, start, "DIAGNOSED", "diagnosis",
                        "Diagnosis and change plan generated; repository was not modified", null, plan);
            }

            if (plan.getConfidence() == null || plan.getConfidence() < policy.getMinConfidence()) {
                return downgrade(audit, incident, taskId, start, "model confidence",
                        list("model confidence below threshold " + policy.getMinConfidence()), plan);
            }

            List<String> modelGuardReasons = validateModelGuard(plan);
            if (!modelGuardReasons.isEmpty()) {
                return downgrade(audit, incident, taskId, start, "model policy", modelGuardReasons, plan);
            }

            if (plan.hasStructuredEdits()) {
                PatchValidationResult structuredPreflight = patchService.preflightStructured(plan);
                audit.writeJson("structured-edit-preflight.json", structuredPreflight);
                log.info("Structured edit preflight completed: taskId={}, incidentId={}, valid={}, files={}, lines={}",
                        taskId, incident.getIncidentId(), structuredPreflight.isValid(),
                        structuredPreflight.getChangedFiles().size(), structuredPreflight.getDiffLines());
                if (!structuredPreflight.isValid()) {
                    return downgrade(audit, incident, taskId, start, "structured edit preflight",
                            structuredPreflight.getBlockedReasons(), plan);
                }
                try {
                    patchService.applyStructuredEdits(workspace, plan);
                    plan.setUnifiedDiff(patchService.currentDiff(workspace));
                    audit.writeText("patch.diff", redactor.redactText(plan.getUnifiedDiff()));
                    log.info("Structured edits applied and converted to git diff: taskId={}, incidentId={}",
                            taskId, incident.getIncidentId());
                } catch (RuntimeException e) {
                    return downgrade(audit, incident, taskId, start, "structured edit apply",
                            list(e.getMessage()), plan);
                }
                PatchValidationResult generatedPreflight = patchService.preflight(plan.getUnifiedDiff());
                audit.writeJson("generated-patch-preflight.json", generatedPreflight);
                if (!generatedPreflight.isValid()) {
                    return downgrade(audit, incident, taskId, start, "generated patch preflight",
                            generatedPreflight.getBlockedReasons(), plan);
                }
            } else {
                audit.writeText("patch-original.diff", redactor.redactText(
                        plan.getUnifiedDiff() == null ? "" : plan.getUnifiedDiff()));
                PatchValidationResult preflight = patchService.preflight(plan.getUnifiedDiff());
                audit.writeJson("patch-preflight.json", preflight);
                log.info("Patch preflight completed: taskId={}, incidentId={}, valid={}, files={}, diffLines={}",
                        taskId, incident.getIncidentId(), preflight.isValid(),
                        preflight.getChangedFiles().size(), preflight.getDiffLines());
                if (!preflight.isValid()) {
                    return downgrade(audit, incident, taskId, start, "patch preflight",
                            preflight.getBlockedReasons(), plan);
                }

                PatchApplyCheckResult applyCheck = patchService.checkApply(workspace, plan.getUnifiedDiff());
                audit.writeJson("patch-apply-check.json", applyCheck);
                if (!applyCheck.isValid()) {
                    audit.writeText("patch-apply-check-error.log", applyCheck.getMessage());
                    log.warn("Patch apply check failed; requesting one model repair: taskId={}, incidentId={}, error={}",
                            taskId, incident.getIncidentId(), applyCheck.getMessage());
                    String repairPrompt = promptBuilder.buildPatchRepairPrompt(
                            incident, retrieval, plan, applyCheck.getMessage());
                    audit.writeText("patch-repair-prompt.txt", repairPrompt);
                    LlmPlan repairedPlan = llmGateway.generate(repairPrompt);
                    audit.writeJson("llm-patch-repair-output.json", redacted(repairedPlan));
                    List<String> repairGuardReasons = validateModelGuard(repairedPlan);
                    if (!repairGuardReasons.isEmpty()) {
                        return downgrade(audit, incident, taskId, start, "patch repair model policy",
                                repairGuardReasons, repairedPlan);
                    }
                    PatchValidationResult repairPreflight = patchService.preflight(repairedPlan.getUnifiedDiff());
                    audit.writeJson("patch-repair-preflight.json", repairPreflight);
                    audit.writeText("patch-repaired.diff", redactor.redactText(
                            repairedPlan.getUnifiedDiff() == null ? "" : repairedPlan.getUnifiedDiff()));
                    if (!repairPreflight.isValid()) {
                        return downgrade(audit, incident, taskId, start, "patch repair preflight",
                                repairPreflight.getBlockedReasons(), repairedPlan);
                    }
                    PatchApplyCheckResult repairCheck = patchService.checkApply(workspace, repairedPlan.getUnifiedDiff());
                    audit.writeJson("patch-repair-apply-check.json", repairCheck);
                    if (!repairCheck.isValid()) {
                        audit.writeText("patch-repair-apply-check-error.log", repairCheck.getMessage());
                        return downgrade(audit, incident, taskId, start, "patch apply check",
                                list("patch failed git apply --check and one repair attempt also failed: "
                                        + repairCheck.getMessage()), repairedPlan);
                    }
                    plan = repairedPlan;
                }
                audit.writeText("patch.diff", redactor.redactText(
                        plan.getUnifiedDiff() == null ? "" : plan.getUnifiedDiff()));
                try {
                    patchService.apply(workspace, plan.getUnifiedDiff());
                    log.info("Patch applied: taskId={}, incidentId={}", taskId, incident.getIncidentId());
                } catch (RuntimeException e) {
                    return downgrade(audit, incident, taskId, start, "patch apply", list(e.getMessage()), plan);
                }
            }

            PatchValidationResult patchValidation = patchService.validate(workspace, plan.getUnifiedDiff());
            audit.writeJson("patch-validation.json", patchValidation);
            audit.writeText("applied.diff", patchService.currentDiff(workspace));
            log.info("Applied patch validation completed: taskId={}, incidentId={}, valid={}, files={}",
                    taskId, incident.getIncidentId(), patchValidation.isValid(),
                    patchValidation.getChangedFiles().size());
            if (!patchValidation.isValid()) {
                return downgrade(audit, incident, taskId, start, "patch policy",
                        patchValidation.getBlockedReasons(), plan);
            }

            VerificationResult verification = testRunner.verify(workspace.getDirectory(), audit);
            audit.writeJson("test-result.json", verification);
            log.info("Engineering verification completed: taskId={}, incidentId={}, success={}, failureReason={}",
                    taskId, incident.getIncidentId(), verification.isSuccess(),
                    verification.getFailureReason());
            if (!verification.isSuccess()) {
                return downgrade(audit, incident, taskId, start, "engineering verification",
                        list(verification.getFailureReason()), plan);
            }
            PatchValidationResult postTestValidation = patchService.validate(workspace, plan.getUnifiedDiff());
            audit.writeJson("post-test-patch-validation.json", postTestValidation);
            if (!postTestValidation.isValid()
                    || !sameFiles(patchValidation.getChangedFiles(), postTestValidation.getChangedFiles())) {
                List<String> reasons = new ArrayList<String>(postTestValidation.getBlockedReasons());
                if (!sameFiles(patchValidation.getChangedFiles(), postTestValidation.getChangedFiles())) {
                    reasons.add("test execution changed files outside the approved patch set");
                }
                return downgrade(audit, incident, taskId, start, "post-test patch policy", reasons, plan);
            }

            boolean dryRun = dryRunOverride == null ? app.isDryRun() : dryRunOverride.booleanValue();
            PrResult prResult = new PrResult();
            prResult.setBranch(workspace.getBranch());
            if (dryRun || !output.isCreatePullRequest()) {
                prResult.setSkipped(true);
                prResult.setMessage(dryRun
                        ? "Dry-run completed; branch was not pushed and PR was not created"
                        : "Pull request creation is disabled by configuration");
                log.info("Pull request creation skipped: taskId={}, incidentId={}, dryRun={}, enabled={}",
                        taskId, incident.getIncidentId(), dryRun, output.isCreatePullRequest());
            } else {
                String commit = repoManager.commit(workspace, incident, patchValidation.getChangedFiles());
                log.info("Generated changes committed: taskId={}, incidentId={}, commit={}, branch={}",
                        taskId, incident.getIncidentId(), commit, workspace.getBranch());
                repoManager.push(workspace);
                log.info("Generated branch pushed: taskId={}, incidentId={}, branch={}",
                        taskId, incident.getIncidentId(), workspace.getBranch());
                String title = github.getTitlePrefix() + "[" + incident.getIncidentId()
                        + "] Fix " + incident.getErrorFingerprint();
                String body = prBodyBuilder.build(incident, plan, patchValidation, verification, audit);
                prResult = githubClient.create(workspace.getBranch(), title, body);
                prResult.setCommit(commit);
                log.info("Pull request created: taskId={}, incidentId={}, url={}",
                        taskId, incident.getIncidentId(), prResult.getUrl());
            }
            audit.writeJson("pr-result.json", prResult);
            String status = prResult.isCreated() ? "PR_CREATED" : "DRY_RUN_COMPLETE";
            String message = prResult.getMessage();
            return finish(audit, incident, taskId, start, status, "completed",
                    message, prResult.getUrl(), plan);
        }
    }

    private DemoResult downgrade(AuditSession audit, IncidentContext incident, String taskId, Instant start,
                                 String stage, List<String> reasons, LlmPlan plan) {
        log.warn("Incident workflow downgraded: taskId={}, incidentId={}, stage={}, reasons={}",
                taskId, incident.getIncidentId(), stage, reasons);
        if (output.isWriteManualReportOnFailure()) {
            manualReportService.write(audit, incident, stage, reasons, plan);
        }
        return finish(audit, incident, taskId, start, "DOWNGRADED", stage,
                reasons == null || reasons.isEmpty() ? "Automatic PR stopped" : reasons.get(0), null, plan);
    }

    private DemoResult finish(AuditSession audit, IncidentContext incident, String taskId, Instant start,
                              String status, String stage, String message, String prUrl, LlmPlan plan) {
        DemoResult result = new DemoResult();
        result.setTaskId(taskId);
        result.setIncidentId(incident.getIncidentId());
        result.setStatus(status);
        result.setStage(stage);
        result.setMessage(message);
        result.setAuditDirectory(audit.getDirectory().toString());
        result.setPullRequestUrl(prUrl);
        result.setAnalysis(plan);

        Map<String, Object> summary = new LinkedHashMap<String, Object>();
        summary.put("incident_id", incident.getIncidentId());
        summary.put("task_id", taskId);
        summary.put("start_time", start.toString());
        summary.put("end_time", Instant.now().toString());
        summary.put("status", status);
        summary.put("stage", stage);
        summary.put("message", message);
        summary.put("model_provider", model.getProvider());
        summary.put("model", model.getModelName());
        summary.put("github_repo", github.getOwner() + "/" + github.getRepo());
        summary.put("pull_request_url", prUrl);
        audit.writeJson("audit.json", summary);
        log.info("Incident workflow finished: taskId={}, incidentId={}, status={}, stage={}, durationMs={}",
                taskId, incident.getIncidentId(), status, stage,
                java.time.Duration.between(start, Instant.now()).toMillis());
        return result;
    }

    private void validateRequest(IncidentContext incident) {
        if (incident == null) {
            throw new IllegalArgumentException("IncidentContext request body is required");
        }
        if (incident.getIncidentId() == null || incident.getIncidentId().trim().isEmpty()) {
            throw new IllegalArgumentException("incident_id is required");
        }
    }

    private void writeDiagnosis(AuditSession audit, IncidentContext incident, LlmPlan plan) {
        StringBuilder report = new StringBuilder();
        report.append("# 诊断报告\n\n");
        report.append("- IncidentId: ").append(incident.getIncidentId()).append('\n');
        report.append("- Confidence: ").append(plan.getConfidence()).append("\n\n");
        report.append("## 根因假设\n\n").append(plan.getRootCauseHypothesis()).append("\n\n");
        report.append("## 变更计划\n\n");
        for (String item : plan.getChangePlan()) {
            report.append("- ").append(item).append('\n');
        }
        report.append("\n## 测试计划\n\n");
        for (String item : plan.getTestPlan()) {
            report.append("- ").append(item).append('\n');
        }
        audit.writeText("diagnosis_report.md", report.toString());
    }

    private JsonNode redacted(Object value) {
        return redactor.redact(mapper.valueToTree(value));
    }

    private List<String> list(String value) {
        List<String> values = new ArrayList<String>();
        values.add(value == null ? "unspecified failure" : value);
        return values;
    }

    private List<String> validateModelGuard(LlmPlan plan) {
        List<String> blocked = new ArrayList<String>();
        List<String> confirmations = plan.getForbiddenActions();
        requireConfirmation(confirmations, "禁止生产发布", blocked, "no production release");
        requireConfirmation(confirmations, "禁止自动合并", blocked, "no auto merge");
        requireConfirmation(confirmations, "禁止修改生产配置", blocked, "no production config edit");
        return blocked;
    }

    private void requireConfirmation(List<String> confirmations, String required,
                                     List<String> blocked, String... aliases) {
        if (confirmations == null) {
            blocked.add("model did not confirm forbidden action: " + required);
            return;
        }
        for (String confirmation : confirmations) {
            if (required.equalsIgnoreCase(confirmation)) {
                return;
            }
            for (String alias : aliases) {
                if (alias.equalsIgnoreCase(confirmation)) {
                    return;
                }
            }
        }
        blocked.add("model did not confirm forbidden action: " + required);
    }

    private boolean sameFiles(List<String> left, List<String> right) {
        return new java.util.HashSet<String>(left).equals(new java.util.HashSet<String>(right));
    }
}
