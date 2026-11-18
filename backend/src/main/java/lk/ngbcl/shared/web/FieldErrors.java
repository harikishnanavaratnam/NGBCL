package lk.ngbcl.shared.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Collects business-rule field errors and throws them together as VALIDATION_FAILED, in the
 * same {@code errors: [{field, message}]} shape as bean validation, so forms can mark fields.
 */
public final class FieldErrors {

    private final Map<String, String> errors = new LinkedHashMap<>();

    public FieldErrors add(String field, String message) {
        errors.putIfAbsent(field, message);
        return this;
    }

    public boolean has(String field) {
        return errors.containsKey(field);
    }

    public void throwIfAny() {
        if (!errors.isEmpty()) {
            throw new Exception(errors);
        }
    }

    public static final class Exception extends ApiException {

        private final List<Map<String, String>> errors;

        Exception(Map<String, String> errors) {
            super(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Some fields need attention.");
            List<Map<String, String>> list = new ArrayList<>();
            errors.forEach((f, m) -> list.add(Map.of("field", f, "message", m)));
            this.errors = List.copyOf(list);
        }

        public List<Map<String, String>> getErrors() {
            return errors;
        }
    }
}
