package com.example.aidevops.llm;

import com.example.aidevops.config.ModelProperties;
import com.example.aidevops.model.CandidateFile;
import com.example.aidevops.model.IncidentContext;
import com.example.aidevops.model.RetrievalResult;
import com.example.aidevops.security.SecretRedactor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

@Service
public class PromptBuilder {
    private final ObjectMapper mapper;
    private final ModelProperties model;
    private final SecretRedactor redactor;

    public PromptBuilder(ObjectMapper mapper, ModelProperties model, SecretRedactor redactor) {
        this.mapper = mapper;
        this.model = model;
        this.redactor = redactor;
    }

    public String build(IncidentContext incident, RetrievalResult retrieval) {
        try {
            JsonNode redactedIncident = redactor.redact(mapper.valueToTree(incident));
            StringBuilder prompt = new StringBuilder();
            prompt.append("You are a controlled DevOps software engineering agent.\n");
            prompt.append("Analyze only the supplied evidence and code. Produce a minimal Java change.\n");
            prompt.append("Never propose production release, automatic merge, production configuration edits, ");
            prompt.append("permission/authentication/risk-control changes, secret exposure, or bypassing tests.\n");
            prompt.append("Return one JSON object only. It must contain: root_cause_hypothesis, confidence, ");
            prompt.append("change_plan, target_files, test_plan, risk_notice, forbidden_actions_confirmed, unified_diff.\n");
            prompt.append("unified_diff must be a valid git unified diff relative to repository root and include tests.\n\n");
            prompt.append("IncidentContext:\n");
            prompt.append(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(redactedIncident));
            prompt.append("\n\nCandidate code:\n");
            for (CandidateFile file : retrieval.getTargetFiles()) {
                prompt.append("\n--- FILE: ").append(file.getPath()).append(" ---\n");
                prompt.append(redactor.redactText(file.getContent())).append('\n');
                if (prompt.length() >= model.getMaxInputChars()) {
                    break;
                }
            }
            if (prompt.length() > model.getMaxInputChars()) {
                return prompt.substring(0, model.getMaxInputChars());
            }
            return prompt.toString();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot build model prompt", e);
        }
    }
}
