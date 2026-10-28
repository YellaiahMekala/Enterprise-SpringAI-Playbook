package com.springai.explore.service;

/** The model provider answered, but with nothing usable. Mapped to 502 Bad Gateway. */
public class ModelResponseException extends RuntimeException {

    public ModelResponseException(String message) {
        super(message);
    }
}
