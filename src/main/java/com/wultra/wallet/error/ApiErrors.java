package com.wultra.wallet.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.io.IOException;

@Slf4j
@RestControllerAdvice
public class ApiErrors extends ResponseEntityExceptionHandler {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @ExceptionHandler(WalletException.class)
    ResponseEntity<ProblemDetail> walletError(WalletException exception) {
        log.error("Wallet exception occurred.", exception);
        ProblemDetail problem = getProblem(exception.getStatus(), exception.getCode(), exception.getMessage());
        return ResponseEntity.status(exception.getStatus()).body(problem);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> generalException(Exception exception) {
        log.error("Exception occurred.", exception);
        return ResponseEntity.internalServerError()
                .body(getProblem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "The request could not be completed."));
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body, HttpHeaders headers,
                                                             HttpStatusCode status, WebRequest request) {
        log.error("Internal exception occurred.", exception);
        ProblemDetail problem = getProblem(HttpStatus.valueOf(status.value()), "INVALID_REQUEST", "The request is invalid or unsupported.");
        return super.handleExceptionInternal(exception, problem, headers, status, request);
    }

    public static ProblemDetail getProblem(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        return problem;
    }

    public static void writeToResponse(HttpServletResponse response, HttpStatus status, String code, String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/problem+json");
        OBJECT_MAPPER.writeValue(response.getOutputStream(), getProblem(status, code, detail));
    }
}
