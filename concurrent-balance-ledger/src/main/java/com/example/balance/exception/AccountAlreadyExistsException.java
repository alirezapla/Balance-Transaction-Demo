package com.example.balance.exception;
public class AccountAlreadyExistsException extends RuntimeException { public AccountAlreadyExistsException(String id){super("Account already exists: "+id);} }
