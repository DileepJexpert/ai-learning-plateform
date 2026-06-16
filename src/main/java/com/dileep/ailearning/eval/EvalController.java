package com.dileep.ailearning.eval;

import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Module 6 HTTP surface for evaluation.
 *
 * <pre>
 * # run the bundled extraction eval set and get accuracy metrics:
 * curl -s -X POST localhost:8080/api/eval/invoices | jq
 *
 * # LLM-as-judge for a fuzzy (free-text) answer:
 * curl -s localhost:8080/api/eval/judge -H 'Content-Type: application/json' -d '{
 *   "question":"What is the GST rate on electronics?",
 *   "context":"The 18% slab covers electronics and white goods.",
 *   "answer":"Electronics are taxed at 18% GST."}' | jq
 * </pre>
 */
@RestController
@RequestMapping("/api/eval")
public class EvalController {

    private final EvaluationService evaluationService;
    private final LlmJudge judge;

    public EvalController(EvaluationService evaluationService, LlmJudge judge) {
        this.evaluationService = evaluationService;
        this.judge = judge;
    }

    @PostMapping("/invoices")
    public EvaluationReport evaluateInvoices() {
        return evaluationService.evaluateDefaultDataset();
    }

    @PostMapping("/judge")
    public JudgeVerdict judge(@RequestBody JudgeRequest request) {
        return judge.judgeFaithfulness(request.question(), request.answer(), request.context());
    }

    public record JudgeRequest(@NotBlank String question, @NotBlank String answer, @NotBlank String context) {
    }
}
