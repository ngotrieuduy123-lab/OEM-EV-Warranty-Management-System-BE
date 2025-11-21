package com.mega.warrantymanagementsystem.exception;


import com.mega.warrantymanagementsystem.exception.exception.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class APIException {

    //chạy mỗi khi dính lỗi

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity handleBadRequest(MethodArgumentNotValidException exception){
        String message = "";

        for(var fieldError: exception.getBindingResult().getFieldErrors()){
            message += fieldError.getField() + ": "+fieldError.getDefaultMessage()+"\n";
        }

        return ResponseEntity.badRequest().body(message);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity handleBadCredentialsException(BadCredentialsException exception){
        String msg = exception.getMessage();
        // Nếu muốn phân biệt rõ: nếu message chứa "disabled" -> 403 (forbidden)
        if (msg != null && msg.toLowerCase().contains("disabled")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(msg);
        }
        // Mặc định: 401 với message gốc (hoặc bạn vẫn muốn giữ "Invalid username or password" cho an toàn)
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(msg != null ? msg : "Invalid username or password");
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity handleResourceNotFoundException(ResourceNotFoundException exception){
        return ResponseEntity.status(404).body(exception.getMessage());
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity handleDuplicateResourceException(DuplicateResourceException exception){
        return ResponseEntity.status(409).body(exception.getMessage());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity handleAuthenticationException(AuthenticationException exception){
        return ResponseEntity.status(401).body(exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity handleIllegalArgumentException(IllegalArgumentException exception){
        return ResponseEntity.status(400).body(exception.getMessage());
    }

    @ExceptionHandler(InvalidOperationException.class)
    public ResponseEntity<String> handleInvalidOperation(InvalidOperationException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(exception.getMessage());
    }

    @ExceptionHandler(BusinessLogicException.class)
    public ResponseEntity<String> handleBusinessLogicException(BusinessLogicException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }

    @ExceptionHandler(DisabledPartException.class)
    public ResponseEntity<String> handleDisabledPartException(DisabledPartException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(exception.getMessage());
    }

    @ExceptionHandler(DateTimeParseException.class)
    public ResponseEntity<String> handleDateTimeParse(DateTimeParseException ex) {
        return ResponseEntity.status(400).body(ex.getMessage());
    }
}
