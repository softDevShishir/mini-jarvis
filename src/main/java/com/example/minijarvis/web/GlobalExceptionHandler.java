package com.example.minijarvis.web;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.example.minijarvis.agent.AgentNotFoundException;
import com.example.minijarvis.agent.AgentRunFailedException;
import com.example.minijarvis.agent.InvalidAdviceException;
import com.example.minijarvis.ai.AiProviderException;
import com.example.minijarvis.ai.DailyLimitExceededException;
import com.example.minijarvis.ai.ProviderUnavailableException;
import com.example.minijarvis.recommendation.RecommendationAlreadyDecidedException;
import com.example.minijarvis.recommendation.RecommendationNotFoundException;
import com.example.minijarvis.security.AuthController;

import jakarta.servlet.http.HttpServletRequest;

/** Maps every failure to the same JSON error shape. Messages are ours; raw SDK errors are only logged. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler({ ProviderUnavailableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class, HttpMessageNotReadableException.class })
    ResponseEntity<ApiError> badRequest(Exception e, HttpServletRequest req) {
        String message = e instanceof ProviderUnavailableException ? e.getMessage()
                : e instanceof MethodArgumentTypeMismatchException m
                        ? "Invalid value for parameter '" + m.getName() + "'"
                        : e instanceof HttpMessageNotReadableException ? "Malformed JSON request body" : e.getMessage();
        return build(HttpStatus.BAD_REQUEST, message, req, null);
    }

    @ExceptionHandler({ MethodArgumentNotValidException.class, HandlerMethodValidationException.class })
    ResponseEntity<ApiError> validation(Exception e, HttpServletRequest req) {
        String message = "Validation failed";
        if (e instanceof MethodArgumentNotValidException m && m.getBindingResult().getFieldError() != null) {
            var fe = m.getBindingResult().getFieldError();
            message = fe.getField() + " " + fe.getDefaultMessage();
        }
        return build(HttpStatus.BAD_REQUEST, message, req, null);
    }

    @ExceptionHandler(AuthController.BadCredentialsException.class)
    ResponseEntity<ApiError> unauthorized(Exception e, HttpServletRequest req) {
        return build(HttpStatus.UNAUTHORIZED, e.getMessage(), req, null);
    }

    @ExceptionHandler({ AgentNotFoundException.class, RecommendationNotFoundException.class,
            NoResourceFoundException.class })
    ResponseEntity<ApiError> notFound(Exception e, HttpServletRequest req) {
        String message = e instanceof NoResourceFoundException ? "Not found" : e.getMessage();
        return build(HttpStatus.NOT_FOUND, message, req, null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> methodNotAllowed(Exception e, HttpServletRequest req) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, e.getMessage(), req, null);
    }

    @ExceptionHandler(RecommendationAlreadyDecidedException.class)
    ResponseEntity<ApiError> conflict(Exception e, HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, e.getMessage(), req, null);
    }

    @ExceptionHandler(DailyLimitExceededException.class)
    ResponseEntity<ApiError> dailyLimit(Exception e, HttpServletRequest req) {
        return build(HttpStatus.TOO_MANY_REQUESTS, e.getMessage(), req, null);
    }

    @ExceptionHandler(AiProviderException.class)
    ResponseEntity<ApiError> aiFailure(AiProviderException e, HttpServletRequest req) {
        log.warn("AI provider failure: {}", e.getMessage(), e.getCause());
        return build(HttpStatus.BAD_GATEWAY, e.getMessage(), req, null);
    }

    @ExceptionHandler(AgentRunFailedException.class)
    ResponseEntity<ApiError> runFailed(AgentRunFailedException e, HttpServletRequest req) {
        Throwable cause = e.getCause();
        if (cause instanceof AiProviderException ai) {
            log.warn("Agent run {} failed: {}", e.getRunId(), ai.getMessage(), ai.getCause());
            return build(HttpStatus.BAD_GATEWAY, ai.getMessage(), req, e.getRunId());
        }
        if (cause instanceof InvalidAdviceException) {
            return build(HttpStatus.BAD_GATEWAY, "AI provider returned unusable output: " + cause.getMessage(), req,
                    e.getRunId());
        }
        if (cause instanceof DailyLimitExceededException) {
            return build(HttpStatus.TOO_MANY_REQUESTS, cause.getMessage(), req, e.getRunId());
        }
        log.error("Agent run {} failed unexpectedly", e.getRunId(), cause);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Agent run failed unexpectedly", req, e.getRunId());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception e, HttpServletRequest req) {
        log.error("Unhandled error on {}", req.getRequestURI(), e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error", req, null);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message, HttpServletRequest req, Long runId) {
        return ResponseEntity.status(status).body(
                new ApiError(clock.instant(), status.value(), status.getReasonPhrase(), message, req.getRequestURI(), runId));
    }
}
