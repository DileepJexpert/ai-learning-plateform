package com.dileep.ailearning.ollama;

/**
 * Thrown when a call to Ollama fails — connection refused, timeout, or a non-2xx
 * HTTP status. Treating "the model backend is down" as a distinct, typed failure
 * lets the web layer map it to a 502/503 (a dependency problem) rather than a
 * generic 500. (Reliability thinking — your home turf, see Module 7.)
 */
public class OllamaException extends RuntimeException {

    public OllamaException(String message) {
        super(message);
    }

    public OllamaException(String message, Throwable cause) {
        super(message, cause);
    }
}
