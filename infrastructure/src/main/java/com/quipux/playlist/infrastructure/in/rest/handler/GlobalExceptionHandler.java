package com.quipux.playlist.infrastructure.in.rest.handler;

import com.quipux.playlist.domain.exception.BusinessException;
import com.quipux.playlist.domain.model.enums.ResponseCodeEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.result.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String CODE_PROPERTY = "code";

    @ExceptionHandler(BusinessException.class)
    public ProblemDetail handleBusinessException(BusinessException exception) {
        return problem(statusOf(exception.responseCode()), exception.responseCode(), exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpectedException(Exception exception) {
        LOG.error("Unexpected error", exception);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, ResponseCodeEnum.UNEXPECTED_ERROR,
                "An unexpected error occurred");
    }

    /**
     * Errors detected by the framework. Only the bad requests carry a code of this API.
     */
    @Override
    protected Mono<ResponseEntity<Object>> handleExceptionInternal(
            Exception exception, Object body, HttpHeaders headers, HttpStatusCode status, ServerWebExchange exchange) {
        return super.handleExceptionInternal(exception, body, headers, status, exchange)
                .doOnNext(response -> {
                    if (response.getBody() instanceof ProblemDetail detail
                            && detail.getStatus() == HttpStatus.BAD_REQUEST.value()) {
                        detail.setProperty(CODE_PROPERTY, ResponseCodeEnum.INVALID_REQUEST.code());
                    }
                });
    }

    private static HttpStatus statusOf(ResponseCodeEnum responseCode) {
        return switch (responseCode) {
            case INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
            case PLAYLIST_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case PLAYLIST_ALREADY_EXISTS -> HttpStatus.CONFLICT;
            case UNEXPECTED_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    private static ProblemDetail problem(HttpStatus status, ResponseCodeEnum responseCode, String message) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, message);
        detail.setProperty(CODE_PROPERTY, responseCode.code());
        return detail;
    }
}
