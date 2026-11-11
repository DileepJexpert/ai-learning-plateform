package com.dileep.ailearning.compliance.model;

import java.util.List;

/**
 * Auditor-grade regulatory answer.
 *
 * @param summary          executive summary for compliance officers
 * @param auditedFindings  granular findings linked to specific circular clauses
 * @param verifiedCitations list of source clauses providing legal grounding
 * @param model            model tag that performed synthesis
 * @param candidateCount   number of candidate clauses evaluated in the RRF hybrid pool
 * @param latencyMs        end-to-end response time in milliseconds
 */
public record ComplianceAnswer(
        String summary,
        List<ClauseStatement> auditedFindings,
        List<AuditCitation> verifiedCitations,
        String model,
        int candidateCount,
        long latencyMs
) {
}
