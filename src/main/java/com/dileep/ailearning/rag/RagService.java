package com.dileep.ailearning.rag;

import com.dileep.ailearning.config.RagProperties;
import com.dileep.ailearning.embedding.ChunkRepository.Candidate;
import com.dileep.ailearning.embedding.EmbeddingService;
import com.dileep.ailearning.ollama.OllamaClient;
import com.dileep.ailearning.ollama.dto.ChatRequest;
import com.dileep.ailearning.ollama.dto.ChatResponse;
import com.dileep.ailearning.ollama.dto.Message;
import com.dileep.ailearning.ollama.dto.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Module 4 — Retrieval-Augmented Generation.
 *
 * <p>The full pipeline, building on Module 3's retrieval:
 * <ol>
 *   <li><b>Retrieve</b> a candidate pool of chunks from pgvector (over-fetch)</li>
 *   <li><b>Re-rank</b> with MMR for relevance + diversity, down to top-k</li>
 *   <li><b>Budget</b> the context to fit the window (drop chunks past the char budget)</li>
 *   <li><b>Ground</b>: stuff the numbered chunks into the prompt as the only allowed source</li>
 *   <li><b>Generate</b> an answer with [n] citations, or "I don't know" if unsupported</li>
 * </ol>
 *
 * <p>Retrieval + grounding is the core hallucination-reduction technique: the model
 * answers from supplied facts, and every answer is traceable to its source chunks.
 */
@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);
    private static final String NO_ANSWER = "I don't know based on the provided documents.";
    private static final int SNIPPET_LEN = 160;

    private final EmbeddingService embeddingService;
    private final OllamaClient ollama;
    private final RagPromptFactory prompts;
    private final RagProperties config;

    public RagService(EmbeddingService embeddingService, OllamaClient ollama,
                      RagPromptFactory prompts, RagProperties config) {
        this.embeddingService = embeddingService;
        this.ollama = ollama;
        this.prompts = prompts;
        this.config = config;
    }

    public RagAnswer answer(String question, Integer topKOverride) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("question must not be blank");
        }
        int topK = topKOverride != null ? topKOverride : config.topK();
        long startNanos = System.nanoTime();

        // 1. Retrieve a candidate pool (over-fetch so re-ranking has room to work).
        List<Candidate> candidates = embeddingService.retrieve(question, config.fetchK());
        if (candidates.isEmpty()) {
            // Nothing in the corpus — don't even call the model; we can't ground an answer.
            log.info("rag: no chunks retrieved for question; returning no-answer");
            return new RagAnswer(NO_ANSWER, List.of(), config.chatModel(), 0, 0, false,
                    elapsedMs(startNanos), null, null);
        }

        // 2. Re-rank (MMR) down to top-k, or just take the top-k by similarity.
        List<Candidate> ranked = config.rerank()
                ? Mmr.rerank(candidates, Candidate::similarity, Candidate::embedding, topK, config.mmrLambda())
                : candidates.subList(0, Math.min(topK, candidates.size()));

        // 3. Budget the context to fit the window.
        List<Candidate> used = applyBudget(ranked, config.maxContextChars());

        // 4. Ground: build the numbered context and prompt.
        String numberedContext = buildContext(used);
        List<Message> messages = List.of(
                Message.system(prompts.systemPrompt()),
                Message.user(prompts.userPrompt(numberedContext, question)));

        // 5. Generate (free-form text answer, low temperature for faithfulness).
        Options options = new Options(config.temperature(), null, config.seed(), config.numCtx());
        ChatResponse response = ollama.chat(ChatRequest.text(config.chatModel(), messages, options));

        long durationMs = elapsedMs(startNanos);
        log.info("rag: retrieved={} reranked={} used={} model={} {}ms",
                candidates.size(), config.rerank(), used.size(), config.chatModel(), durationMs);

        return new RagAnswer(response.content().strip(), toCitations(used), config.chatModel(),
                candidates.size(), used.size(), config.rerank(), durationMs,
                response.promptEvalCount(), response.evalCount());
    }

    /**
     * Keep chunks until the character budget is exhausted (a stand-in for token
     * budgeting — context windows are finite, so you can't stuff everything).
     * Always keeps at least the single best chunk.
     */
    private List<Candidate> applyBudget(List<Candidate> ranked, int maxChars) {
        List<Candidate> kept = new ArrayList<>();
        int total = 0;
        for (Candidate c : ranked) {
            int len = c.content().length();
            if (!kept.isEmpty() && total + len > maxChars) {
                break;
            }
            kept.add(c);
            total += len;
        }
        return kept;
    }

    /** Format chunks as "[1] (doc) text\n\n[2] (doc) text...". */
    private String buildContext(List<Candidate> used) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < used.size(); i++) {
            Candidate c = used.get(i);
            sb.append('[').append(i + 1).append("] (").append(c.docName()).append(") ")
                    .append(c.content().strip()).append("\n\n");
        }
        return sb.toString().strip();
    }

    private List<RagAnswer.Citation> toCitations(List<Candidate> used) {
        List<RagAnswer.Citation> citations = new ArrayList<>(used.size());
        for (int i = 0; i < used.size(); i++) {
            Candidate c = used.get(i);
            citations.add(new RagAnswer.Citation(i + 1, c.docName(), c.chunkIndex(),
                    c.similarity(), snippet(c.content())));
        }
        return citations;
    }

    private static String snippet(String content) {
        String s = content.strip().replaceAll("\\s+", " ");
        return s.length() <= SNIPPET_LEN ? s : s.substring(0, SNIPPET_LEN) + "…";
    }

    private static long elapsedMs(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }
}
