package com.example.balance.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record TransferRequest(@NotBlank String sourceAccountId, @NotBlank String destinationAccountId, @Min(1) long amount, @NotBlank String transactionId){}
