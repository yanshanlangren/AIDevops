package com.example.aidevops.api;

import com.example.aidevops.model.IncidentContext;
import com.example.aidevops.task.IncidentTaskService;
import com.example.aidevops.task.TaskSubmission;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/incidents", produces = MediaType.APPLICATION_JSON_VALUE)
public class IncidentController {
    private static final Logger log = LoggerFactory.getLogger(IncidentController.class);

    private final IncidentTaskService taskService;

    public IncidentController(IncidentTaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping(path = "/analyze", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TaskSubmission> analyze(@RequestBody IncidentContext incidentContext) {
        log.info("Received incident analysis request: incidentId={}", incidentContext.getIncidentId());
        TaskSubmission submission = taskService.submitAnalysis(incidentContext);
        return ResponseEntity.accepted()
                .location(URI.create(submission.getStatusUrl()))
                .body(submission);
    }

    @PostMapping(path = "/pull-requests", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TaskSubmission> createPullRequest(
            @RequestBody IncidentContext incidentContext,
            @RequestParam(name = "dryRun", required = false) Boolean dryRun) {
        log.info("Received pull request workflow request: incidentId={}, dryRun={}",
                incidentContext.getIncidentId(), dryRun);
        TaskSubmission submission = taskService.submitPullRequest(incidentContext, dryRun);
        return ResponseEntity.accepted()
                .location(URI.create(submission.getStatusUrl()))
                .body(submission);
    }
}
