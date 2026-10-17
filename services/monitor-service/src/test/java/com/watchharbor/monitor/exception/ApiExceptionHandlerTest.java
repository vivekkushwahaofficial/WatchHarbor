package com.watchharbor.monitor.exception;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void handleValidationErrorReturnsFieldSpecificErrors() {
        BindingResult bindingResult
                = new BeanPropertyBindingResult(new Object(), "request");

        bindingResult.addError(new FieldError(
                "request",
                "name",
                "Monitor name is required"
        ));

        bindingResult.addError(new FieldError(
                "request",
                "url",
                "Monitor URL is required"
        ));

        MethodParameter parameter = mock(MethodParameter.class);

        MethodArgumentNotValidException exception
                = new MethodArgumentNotValidException(
                        parameter,
                        bindingResult
                );

        ProblemDetail problem = handler.handleValidationError(exception);

        assertEquals(HttpStatus.BAD_REQUEST.value(), problem.getStatus());
        assertEquals("Validation failed", problem.getTitle());
        assertEquals("Request validation failed", problem.getDetail());
        assertEquals(
                URI.create("urn:watchharbor:problem:validation-error"),
                problem.getType()
        );

        @SuppressWarnings("unchecked")
        List<String> errors
                = (List<String>) problem.getProperties().get("errors");

        assertEquals(2, errors.size());
        assertTrue(errors.contains("name: Monitor name is required"));
        assertTrue(errors.contains("url: Monitor URL is required"));
    }
}
