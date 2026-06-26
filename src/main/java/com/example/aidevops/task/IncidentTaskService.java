package com.example.aidevops.task;

import com.example.aidevops.model.DemoResult;
import com.example.aidevops.model.IncidentContext;
import com.example.aidevops.runner.DemoOrchestrator;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

@Service
public class IncidentTaskService {
    private static final Logger log = LoggerFactory.getLogger(IncidentTaskService.class);

    private final DemoOrchestrator orchestrator;
    private final TaskExecutor taskExecutor;
    private final ConcurrentMap<String, TaskRecord> tasks =
            new ConcurrentHashMap<String, TaskRecord>();

    public IncidentTaskService(
            DemoOrchestrator orchestrator,
            @Qualifier("incidentTaskExecutor") TaskExecutor taskExecutor) {
        this.orchestrator = orchestrator;
        this.taskExecutor = taskExecutor;
    }

    public TaskSubmission submitAnalysis(IncidentContext incident) {
        return submit(incident, TaskType.ANALYZE, null);
    }

    public TaskSubmission submitPullRequest(IncidentContext incident, Boolean dryRun) {
        return submit(incident, TaskType.PULL_REQUEST, dryRun);
    }

    public TaskRecord get(String taskId) {
        TaskRecord task = tasks.get(taskId);
        if (task == null) {
            throw new TaskNotFoundException(taskId);
        }
        return task;
    }

    private TaskSubmission submit(final IncidentContext incident, final TaskType type, final Boolean dryRun) {
        validate(incident);
        final String taskId = UUID.randomUUID().toString();
        final TaskRecord task = new TaskRecord(taskId, incident.getIncidentId(), type);
        tasks.put(taskId, task);
        try {
            taskExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    runTask(task, incident, type, dryRun);
                }
            });
        } catch (RejectedExecutionException e) {
            tasks.remove(taskId);
            log.error("Incident task rejected by executor: taskId={}, incidentId={}, type={}",
                    taskId, incident.getIncidentId(), type, e);
            throw new IllegalStateException("Incident task queue is full", e);
        }
        log.info("Incident task submitted: taskId={}, incidentId={}, type={}, dryRun={}",
                taskId, incident.getIncidentId(), type, dryRun);
        return new TaskSubmission(
                taskId,
                incident.getIncidentId(),
                TaskStatus.QUEUED,
                "/api/v1/tasks/" + taskId);
    }

    private void runTask(TaskRecord task, IncidentContext incident, TaskType type, Boolean dryRun) {
        task.markRunning();
        log.info("Incident task started: taskId={}, incidentId={}, type={}",
                task.getTaskId(), task.getIncidentId(), type);
        try {
            DemoResult result = type == TaskType.ANALYZE
                    ? orchestrator.analyze(incident, task.getTaskId())
                    : orchestrator.generatePullRequest(incident, dryRun, task.getTaskId());
            task.markSucceeded(result);
            log.info("Incident task succeeded: taskId={}, incidentId={}, type={}, workflowStatus={}",
                    task.getTaskId(), task.getIncidentId(), type, result.getStatus());
        } catch (Throwable throwable) {
            task.markFailed(throwable);
            log.error("Incident task failed: taskId={}, incidentId={}, type={}, message={}",
                    task.getTaskId(), task.getIncidentId(), type, throwable.getMessage(), throwable);
        }
    }

    private void validate(IncidentContext incident) {
        if (incident == null) {
            throw new IllegalArgumentException("IncidentContext request body is required");
        }
        if (incident.getIncidentId() == null || incident.getIncidentId().trim().isEmpty()) {
            throw new IllegalArgumentException("incident_id is required");
        }
    }
}
