package com.example.balance.controller;

import com.example.balance.exception.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.stream.Collectors;

@RestControllerAdvice
public class ApiExceptionHandler {
    public record ApiError(Instant timestamp,int status,String error,String message,String path){}
    private ResponseEntity<ApiError> response(HttpStatus status,String message,HttpServletRequest request){
        return ResponseEntity.status(status).body(new ApiError(Instant.now(),status.value(),status.getReasonPhrase(),message,request.getRequestURI()));
    }
    @ExceptionHandler({AccountNotFoundException.class,ReconciliationRunNotFoundException.class})
    public ResponseEntity<ApiError> notFound(RuntimeException ex,HttpServletRequest req){return response(HttpStatus.NOT_FOUND,ex.getMessage(),req);}
    @ExceptionHandler({InsufficientBalanceException.class,IdempotencyConflictException.class,AccountAlreadyExistsException.class,DataIntegrityViolationException.class})
    public ResponseEntity<ApiError> conflict(Exception ex,HttpServletRequest req){return response(HttpStatus.CONFLICT,ex.getMessage(),req);}
    @ExceptionHandler(InvalidOperationException.class)
    public ResponseEntity<ApiError> badRequest(InvalidOperationException ex,HttpServletRequest req){return response(HttpStatus.BAD_REQUEST,ex.getMessage(),req);}
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validation(MethodArgumentNotValidException ex,HttpServletRequest req){
        String message=ex.getBindingResult().getFieldErrors().stream().map(e->e.getField()+": "+e.getDefaultMessage()).collect(Collectors.joining(", "));
        return response(HttpStatus.BAD_REQUEST,message,req);
    }
    @ExceptionHandler(ArithmeticException.class)
    public ResponseEntity<ApiError> arithmetic(ArithmeticException ex,HttpServletRequest req){return response(HttpStatus.CONFLICT,"Balance overflow",req);}
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception ex,HttpServletRequest req){return response(HttpStatus.INTERNAL_SERVER_ERROR,"Unexpected server error",req);}
}
