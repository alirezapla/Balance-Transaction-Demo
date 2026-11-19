package com.example.balance.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CreateRequest(@NotBlank String accountId, @Min(0) long initialBalance){}
