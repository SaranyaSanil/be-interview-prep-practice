package com.interviewprep.common.exception;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Translates exceptions into RFC 9457 {@link ProblemDetail} responses so every error has the same shape.
 * Standard Spring MVC errors (unsupported method, missing parameter, etc.) are handled by the base class.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Resource not found");
        return problem;
    }

    @ExceptionHandler(FieldValidationException.class)
    public ProblemDetail handleFieldValidation(FieldValidationException ex) {
        return validationProblem(List.of(new FieldErrorDetail(ex.getField(), ex.getMessage())));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldErrorDetail> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldErrorDetail(error.getField(), String.valueOf(error.getDefaultMessage())))
                .toList();
        return handleExceptionInternal(ex, validationProblem(fieldErrors), headers, status, request);
    }

    /** Request body is unreadable: report the offending field when it is a bad value (e.g. unknown enum, bad date). */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem;
        if (ex.getCause() instanceof InvalidFormatException invalidFormat && !invalidFormat.getPath().isEmpty()) {
            String field = invalidFormat.getPath().stream()
                    .map(JsonMappingException.Reference::getFieldName)
                    .collect(Collectors.joining("."));
            problem = validationProblem(List.of(
                    new FieldErrorDetail(field, invalidValueMessage(invalidFormat.getTargetType()))));
        } else {
            problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request body is missing or malformed");
            problem.setTitle("Malformed request");
        }
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    /** Path or query parameter cannot be converted, e.g. {@code ?status=UNKNOWN} or a non-numeric id. */
    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = validationProblem(List.of(
                new FieldErrorDetail(ex.getPropertyName(), invalidValueMessage(ex.getRequiredType()))));
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    /** Last-resort handler: log the cause server-side, never leak internals to the client. */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        problem.setTitle("Internal server error");
        return problem;
    }

    private static ProblemDetail validationProblem(List<FieldErrorDetail> fieldErrors) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed");
        problem.setTitle("Validation failed");
        problem.setProperty("errors", fieldErrors);
        return problem;
    }

    /** Client-friendly message for an unconvertible value; never exposes Java type names. */
    private static String invalidValueMessage(Class<?> targetType) {
        if (targetType == null) {
            return "has an invalid value";
        }
        if (targetType.isEnum()) {
            return "must be one of " + Arrays.toString(targetType.getEnumConstants());
        }
        if (LocalDate.class.equals(targetType)) {
            return "must be a date in the format yyyy-MM-dd";
        }
        if (Number.class.isAssignableFrom(ClassUtils.resolvePrimitiveIfNecessary(targetType))) {
            return "must be a number";
        }
        return "has an invalid value";
    }

    record FieldErrorDetail(String field, String message) {
    }
}
