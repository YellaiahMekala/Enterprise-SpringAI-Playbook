package com.springai.explore.service;

/** An overlapping stateful turn must not read or modify this caller's conversation. */
public class ConversationBusyException extends RuntimeException {

    public ConversationBusyException() {
        super("A conversation request is already in progress. Retry after it completes.");
    }
}
