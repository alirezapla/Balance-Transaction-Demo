package com.example.balance.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record MoneyRequest(@NotBlank String accountId, @Min(1) long amount, @NotBlank String transactionId){}
