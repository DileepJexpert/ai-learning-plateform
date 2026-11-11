package com.dileep.ailearning.compliance.prompt;

import com.dileep.ailearning.compliance.model.ComplianceClause;
import com.dileep.ailearning.ollama.dto.Message;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds auditor-grade prompt contracts for regulatory compliance synthesis (Blueprint 1).
 */
@Component
public class CompliancePromptFactory {

    private static final String SYSTEM_PROMPT = """
            You are a Principal Regulatory & Banking Compliance Audit AI.
            Your task is to analyze user regulatory questions against the provided official regulatory clauses.

            CRITICAL AUDIT RULES:
            1. GROUNDING: Base every conclusion strictly and solely on the provided clauses.
            2. ATTRIBUTION: For every finding, you MUST provide the exact circularId and clauseRef.
            3. REFUSAL: If the provided clauses do not contain sufficient evidence to answer, set "summary" to "INSUFFICIENT_REGULATORY_GROUNDING" and return an empty findings list.
            4. FORMAT: Emit ONLY a single valid JSON object. No markdown code blocks, no preamble.

            TARGET JSON SCHEMA:
            {
              "summary": "Concise executive compliance summary.",
              "findings": [
                {
                  "finding": "Factual regulatory rule statement.",
                  "circularId": "Circular identifier (e.g. RBI/2024-25/112)",
                  "clauseRef": "Specific section/clause (e.g. Section 4.2(a))",
                  "confidence": 0.95
                }
              ]
            }

            FEW-SHOT EXAMPLE:
            User: "What are the KYC refresh intervals for high risk vs low risk accounts?"
            Clauses:
            [Clause 1] circularId: RBI/2024-25/112 | clauseRef: Section 4.2(a) | sectionTitle: Periodic Updation
            "Periodic updation of KYC documents shall be carried out at least once in every two years for high risk customers, once in every eight years for medium risk customers and once in every ten years for low risk customers."
            Assistant:
            {
              "summary": "KYC documentation must be refreshed every 2 years for high-risk customers and every 10 years for low-risk customers.",
              "findings": [
                {
                  "finding": "High-risk customer KYC records require mandatory updation at least once every 2 years.",
                  "circularId": "RBI/2024-25/112",
                  "clauseRef": "Section 4.2(a)",
                  "confidence": 0.99
                },
                {
                  "finding": "Low-risk customer KYC records require updation at least once every 10 years.",
                  "circularId": "RBI/2024-25/112",
                  "clauseRef": "Section 4.2(a)",
                  "confidence": 0.99
                }
              ]
            }
            """;

    public List<Message> buildMessages(String userQuery, List<ComplianceClause> retrievedClauses) {
        List<Message> messages = new ArrayList<>();
        messages.add(Message.system(SYSTEM_PROMPT));

        StringBuilder sb = new StringBuilder();
        sb.append("QUESTION: ").append(userQuery.trim()).append("\n\n");
        sb.append("OFFICIAL REGULATORY CLAUSES:\n");

        if (retrievedClauses == null || retrievedClauses.isEmpty()) {
            sb.append("(No relevant regulatory clauses retrieved)\n");
        } else {
            for (int i = 0; i < retrievedClauses.size(); i++) {
                ComplianceClause c = retrievedClauses.get(i);
                sb.append(String.format("[%d] circularId: %s | clauseRef: %s | sectionTitle: %s | authority: %s\n\"%s\"\n\n",
                        i + 1, c.circularId(), c.clauseRef(), c.sectionTitle(), c.authority(), c.content()));
            }
        }

        messages.add(Message.user(sb.toString()));
        return messages;
    }
}
