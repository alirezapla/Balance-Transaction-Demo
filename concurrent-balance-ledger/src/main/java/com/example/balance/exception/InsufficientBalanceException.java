package com.example.balance.exception;
public class InsufficientBalanceException extends RuntimeException { public InsufficientBalanceException(String id){super("Insufficient balance for account: "+id);} }
