package com.commercecore.testsupport;

import com.commercecore.common.exception.NotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * TEST-ONLY controller. Exists purely so
 * {@link com.commercecore.common.web.GlobalExceptionHandler} can be
 * exercised end-to-end (validation error, not-found, unexpected error)
 * without shipping fake/debug endpoints in the production API. Lives
 * under {@code src/test/java} and is never packaged in the application
 * jar.
 */
@RestController
public class ErrorDemoTestController {

    @PostMapping("/__test/validate")
    public String validate(@Valid @RequestBody Payload payload) {
        return "ok";
    }

    @GetMapping("/__test/not-found")
    public String notFound() {
        throw new NotFoundException("Demo resource not found.");
    }

    @GetMapping("/__test/boom")
    public String boom() {
        throw new IllegalStateException("Demo unexpected failure.");
    }

    public record Payload(
            @NotBlank(message = "name must not be blank") String name,
            @Email(message = "email must be a valid email address") String email
    ) {
    }
}
