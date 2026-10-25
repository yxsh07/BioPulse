package org.biopulse.exception;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.dao.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
@RestControllerAdvice
public class ExceptionHandlerAdvice {
 @ExceptionHandler(ApiException.class) public ProblemDetail api(ApiException ex){return ProblemDetail.forStatusAndDetail(ex.status(),ex.getMessage());}
 @ExceptionHandler({MethodArgumentNotValidException.class,IllegalArgumentException.class,org.springframework.web.method.annotation.HandlerMethodValidationException.class,org.springframework.http.converter.HttpMessageNotReadableException.class})
 public ProblemDetail invalid(Exception ex){return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,"Invalid request fields or parameters");}
 @ExceptionHandler(DataIntegrityViolationException.class) public ProblemDetail conflict(Exception ex){return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,"Duplicate identity, invalid reference, or occupied resource");}
 @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class) public ProblemDetail denied(Exception ex){return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,"Access denied");}
 @ExceptionHandler(Exception.class) public ProblemDetail unexpected(Exception ex){org.slf4j.LoggerFactory.getLogger(getClass()).error("Request failed",ex);return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,"Request failed");}
}