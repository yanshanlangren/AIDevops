package com.example.aidevops.api;

import com.example.aidevops.model.DemoResult;
import com.example.aidevops.model.IncidentContext;
import com.example.aidevops.runner.DemoOrchestrator;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/incidents", produces = MediaType.APPLICATION_JSON_VALUE)
public class IncidentController {
    private final DemoOrchestrator orchestrator;

    public IncidentController(DemoOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostMapping(path = "/analyze", consumes = MediaType.APPLICATION_JSON_VALUE)
    public DemoResult analyze(@RequestBody IncidentContext incidentContext) {
        return orchestrator.analyze(incidentContext);
    }

    @PostMapping(path = "/pull-requests", consumes = MediaType.APPLICATION_JSON_VALUE)
    public DemoResult createPullRequest(
            @RequestBody IncidentContext incidentContext,
            @RequestParam(name = "dryRun", required = false) Boolean dryRun) {
        return orchestrator.generatePullRequest(incidentContext, dryRun);
    }
}
