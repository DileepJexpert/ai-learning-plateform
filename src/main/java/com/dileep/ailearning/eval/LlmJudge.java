package com.dileep.ailearning.eval;

import com.dileep.ailearning.common.JsonSanitizer;
import com.dileep.ailearning.config.EvalProperties;
import com.dileep.ailearning.ollama.OllamaClient;
import com.dileep.ailearning.ollama.dto.ChatRequest;
import com.dileep.ailearning.ollama.dto.ChatResponse;
import com.dileep.ailearning.ollama.dto.Message;
import com.dileep.ailearning.ollama.dto.Options;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * LLM-as-judge (Module 6): use a model to score outputs that can't be checked by
 * exact match — e.g. is a RAG answer actually supported by its context?
 *
 * <p>Exact-match metrics (Module 6's evaluator) work for structured extraction,
 * but free-text answers are <i>fuzzy</i>: many wordings are equally correct. So we
 * ask a model to grade faithfulness on a 1–5 scale with a reason. The judge runs
 * at temperature 0 with {@code format: json} for a parseable, repeatable verdict.
 *
 * <p>Caveat (worth saying in an interview): an LLM judge is itself fallible and can
 * share the generator's blind spots. It's a useful signal, not ground truth —
 * best paired with a human-labelled set.
 */
@Service
public class LlmJudge {

    private static final Logger log = LoggerFactory.getLogger(LlmJudge.class);

    private final OllamaClient ollama;
    private final ObjectMapper objectMapper;
    private final EvalProperties config;

    public LlmJudge(OllamaClient ollama, ObjectMapper objectMapper, EvalProperties config) {
        this.ollama = ollama;
        this.objectMapper = objectMapper;
        this.config = config;
    }

    /**
     * Grade how well {@code answer} is supported by {@code context} and addresses
     * {@code question}.
     */
    public JudgeVerdict judgeFaithfulness(String question, String answer, String context) {
        String system = """
                You are a strict evaluator. Score, from 1 to 5, how well the ANSWER is
                supported by the CONTEXT and addresses the QUESTION.
                5 = fully grounded in the context and correct;
                3 = partially supported or incomplete;
                1 = unsupported, contradicted, or hallucinated.
                Respond ONLY with JSON: {"score": <1-5>, "reason": "<one sentence>"}.
                """;
        String user = """
                QUESTION:
                %s

                CONTEXT:
                %s

                ANSWER:
                %s
                """.formatted(question, context, answer);

        ChatResponse response = ollama.chat(ChatRequest.json(
                config.judgeModel(),
                List.of(Message.system(system), Message.user(user)),
                new Options(0.0, null, 42, 4096)));

        return parseVerdict(response.content());
    }

    JudgeVerdict parseVerdict(String rawContent) {
        try {
            JsonNode node = objectMapper.readTree(JsonSanitizer.extractJsonObject(rawContent));
            int score = clamp(node.path("score").asInt(0), 1, 5);
            String reason = node.path("reason").asText("");
            boolean pass = score >= config.judgePassScore();
            return new JudgeVerdict(score, pass, reason);
        } catch (JsonProcessingException e) {
            log.warn("judge returned unparseable verdict: {}", rawContent);
            // A judge we can't parse is a fail-closed: score 0, not a pass.
            return new JudgeVerdict(0, false, "Unparseable judge response.");
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
