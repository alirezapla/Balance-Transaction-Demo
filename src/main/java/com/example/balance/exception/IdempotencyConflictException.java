package com.example.balance.exception;
public class IdempotencyConflictException extends RuntimeException { public IdempotencyConflictException(String id){super("transactionId already used with a different request: "+id);} }
