package com.helmifr.library.common;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Maps exceptions to RFC 7807 problem responses without leaking internals. */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ProblemDetail noResource(NoResourceFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Not found");
    }

    @ExceptionHandler(ConflictException.class)
    ProblemDetail conflict(ConflictException e) {
        return problem(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(FieldValidationException.class)
    ProblemDetail fieldValidation(FieldValidationException e) {
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, e.getMessage());
        pd.setProperty("errors", e.getFieldErrors());
        return pd;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalid(MethodArgumentNotValidException e) {
        Map<String, String> errors = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(fe -> errors.putIfAbsent(uiFieldName(fe.getField()), fe.getDefaultMessage()));
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "Validation failed");
        pd.setProperty("errors", errors);
        return pd;
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ProblemDetail unreadable(Exception e) {
        return problem(HttpStatus.BAD_REQUEST, "Malformed request");
    }

    /** e.g. ?sort=unknownField */
    @ExceptionHandler({PropertyReferenceException.class, InvalidDataAccessApiUsageException.class})
    ProblemDetail invalidQuery(Exception e) {
        log.debug("Rejected query parameters: {}", e.getMessage());
        return problem(HttpStatus.BAD_REQUEST, "Invalid sort or query parameter");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail integrity(DataIntegrityViolationException e) {
        log.warn("Data integrity violation: {}", e.getMostSpecificCause().getMessage());
        return problem(HttpStatus.CONFLICT, "The change conflicts with existing data");
    }

    private static ProblemDetail problem(HttpStatus status, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(status.getReasonPhrase());
        return pd;
    }

    /**
     * Cross-field checks are boolean methods such as isDueDateAfterBorrowDate(); report them
     * against the field the UI shows ("dueDate").
     */
    private static String uiFieldName(String field) {
        if ("bookIdOrCopyBarcodeGiven".equals(field)) {
            return "bookId";
        }
        return field.endsWith("AfterBorrowDate") ? field.substring(0, field.length() - "AfterBorrowDate".length()) : field;
    }
}
