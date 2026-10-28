package com.springai.explore.web.error;

import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIServiceException;
import com.springai.explore.service.ModelResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * One consistent error contract (RFC 9457 {@code application/problem+json}) for every endpoint.
 * Validation, malformed-body, missing-parameter and upload-size errors are handled by the parent class;
 * this class maps the model-provider failures and makes sure nothing internal leaks to the client.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(OpenAIServiceException.class)
    ProblemDetail handleProviderError(OpenAIServiceException e) {
        // 401/403 mean our own credentials are wrong: an operator problem, not something to tell the caller.
        if (e.statusCode() == HttpStatus.TOO_MANY_REQUESTS.value()) {
            log.warn("Model provider rate limit hit");
            return problem(HttpStatus.TOO_MANY_REQUESTS, "The AI provider is rate limiting requests. Retry later.");
        }
        // The provider's message stays in the server log only; the client gets a generic detail.
        log.error("Model provider rejected the request with status {}: {}", e.statusCode(), e.getMessage());
        return problem(HttpStatus.BAD_GATEWAY, "The AI provider could not process the request.");
    }

    @ExceptionHandler(OpenAIIoException.class)
    ProblemDetail handleProviderTimeout(OpenAIIoException e) {
        log.error("Model provider unreachable or timed out: {}", e.getMessage());
        return problem(HttpStatus.GATEWAY_TIMEOUT, "The AI provider did not respond in time.");
    }

    @ExceptionHandler(ModelResponseException.class)
    ProblemDetail handleEmptyModelResponse(ModelResponseException e) {
        log.warn("Empty model response: {}", e.getMessage());
        return problem(HttpStatus.BAD_GATEWAY, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception e) {
        log.error("Unhandled exception", e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");
    }

    private static ProblemDetail problem(HttpStatus status, String detail) {
        return ProblemDetail.forStatusAndDetail(status, detail);
    }
}
