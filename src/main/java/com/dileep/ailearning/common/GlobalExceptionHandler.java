package com.dileep.ailearning.common;

import com.dileep.ailearning.invoice.ExtractionFailedException;
import com.dileep.ailearning.ollama.OllamaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps our domain failures to meaningful HTTP responses using RFC-7807
 * {@link ProblemDetail}. Distinguishing the failure modes is the point:
 *
 * <ul>
 *   <li>bad/garbage model output that we refused to persist → <b>422</b></li>
 *   <li>the model backend being unreachable/erroring → <b>502</b> (a dependency problem)</li>
 *   <li>a bad client request → <b>400</b></li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** The guardrail tripped: the model never produced acceptable output. */
    @ExceptionHandler(ExtractionFailedException.class)
    public ProblemDetail handleExtractionFailed(ExtractionFailedException ex) {
        log.warn("extraction rejected after {} attempt(s): {}", ex.getAttempts(), ex.getProblems());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        pd.setTitle("Invoice extraction failed");
        pd.setProperty("attempts", ex.getAttempts());
        pd.setProperty("problems", ex.getProblems());
        // Echo the raw model output so the caller can see exactly what went wrong.
        pd.setProperty("rawModelOutput", ex.getRawModelOutput());
        return pd;
    }

    /** Downstream model backend is the problem, not us. */
    @ExceptionHandler(OllamaException.class)
    public ProblemDetail handleOllamaDown(OllamaException ex) {
        log.error("ollama call failed", ex);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, ex.getMessage());
        pd.setTitle("Model backend unavailable");
        return pd;
    }

    /** Bad input from the caller. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleBadInput(IllegalArgumentException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        pd.setTitle("Bad request");
        return pd;
    }
}
