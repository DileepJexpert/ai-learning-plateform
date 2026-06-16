package com.dileep.ailearning.agent;

/**
 * Thrown by a {@link Tool} when the call is well-formed but can't be satisfied
 * (e.g. "no customer with that id"). The agent catches this and feeds the message
 * back to the model as the tool result, so the model can recover (try a different
 * id, ask the user, or explain) rather than the whole request failing.
 */
public class ToolExecutionException extends RuntimeException {

    public ToolExecutionException(String message) {
        super(message);
    }
}
