package lk.ngbcl.shared.web;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Every error leaves the API as problem+json with a stable {@code code} (see frontend ApiError). */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public static ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(status.getReasonPhrase());
        pd.setProperty("code", code);
        return pd;
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> handleApi(ApiException ex) {
        return ResponseEntity.status(ex.getStatus()).body(problem(ex.getStatus(), ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handleInvalid(MethodArgumentNotValidException ex) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::toError)
                .toList();
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Some fields need attention.");
        pd.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(pd);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ProblemDetail> handleMethodValidation(HandlerMethodValidationException ex) {
        return ResponseEntity.badRequest()
                .body(problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Some parameters are invalid."));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> handleUnreadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest()
                .body(problem(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "The request body could not be read."));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> handleDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(problem(HttpStatus.FORBIDDEN, "FORBIDDEN", "You don't have permission to do that."));
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ProblemDetail> handleAuth(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(problem(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Please sign in again."));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ProblemDetail> handleOptimistic(OptimisticLockingFailureException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem(HttpStatus.CONFLICT, "STALE_DATA",
                "Someone else changed this record. Reload and try again."));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> handleIntegrity(DataIntegrityViolationException ex) {
        log.warn("Integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem(HttpStatus.CONFLICT, "CONSTRAINT_VIOLATION",
                "This change conflicts with existing data."));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ProblemDetail> handleNoResource(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(problem(HttpStatus.NOT_FOUND, "NOT_FOUND", "Not found."));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        log.error("Unhandled error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem(HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR", "Something went wrong on our side. Please try again."));
    }

    private static Map<String, String> toError(FieldError fe) {
        return Map.of("field", fe.getField(), "message",
                fe.getDefaultMessage() == null ? "Invalid value" : fe.getDefaultMessage());
    }
}
