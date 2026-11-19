package com.example.balance.controller;

import com.example.balance.dto.BalanceResponse;
import com.example.balance.dto.CreateRequest;
import com.example.balance.dto.OperationResponse;
import com.example.balance.dto.ReconciliationReport;
import com.example.balance.dto.MoneyRequest;
import com.example.balance.dto.TransferRequest;
import com.example.balance.service.BalanceService;
import com.example.balance.reconciliation.ReconciliationRunner;
import com.example.balance.reconciliation.ReconciliationHistoryService;
import com.example.balance.dto.ReconciliationRunDtos.RunDetails;
import com.example.balance.dto.ReconciliationRunDtos.RunSummary;
import java.util.List;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;

@RestController
@RequestMapping("/api")
public class BalanceController {
    private final BalanceService service;
    private final ReconciliationRunner reconciliationRunner;
    private final ReconciliationHistoryService reconciliationHistory;
    public BalanceController(BalanceService service, ReconciliationRunner reconciliationRunner, ReconciliationHistoryService reconciliationHistory){
        this.service=service;
        this.reconciliationRunner=reconciliationRunner;
        this.reconciliationHistory=reconciliationHistory;
    }

    @PostMapping("/accounts")
    public ResponseEntity<OperationResponse> create(@Valid @RequestBody CreateRequest request){
        service.createAccount(request.accountId(),request.initialBalance());
        return ResponseEntity.created(URI.create("/api/accounts/"+request.accountId()+"/balance"))
                .body(new OperationResponse("Account created",null));
    }
    @GetMapping("/accounts/{id}/balance")
    public ResponseEntity<BalanceResponse> balance(@PathVariable String id){
        return ResponseEntity.ok(new BalanceResponse(id,service.balance(id)));}

    @PostMapping("/transactions/credit")
    public ResponseEntity<OperationResponse> credit(@Valid @RequestBody MoneyRequest r){
        service.credit(r.accountId(),r.amount(),r.transactionId());
        return ResponseEntity.ok(new OperationResponse("Credit processed",r.transactionId()));}

    @PostMapping("/transactions/debit")
    public ResponseEntity<OperationResponse> debit(@Valid @RequestBody MoneyRequest r){
        service.debit(r.accountId(),r.amount(),r.transactionId());
        return ResponseEntity.ok(new OperationResponse("Debit processed",r.transactionId()));}

    @PostMapping("/transactions/transfer")
    public ResponseEntity<OperationResponse> transfer(@Valid @RequestBody TransferRequest r){
        service.transfer(r.sourceAccountId(),r.destinationAccountId(),r.amount(),r.transactionId());
        return ResponseEntity.ok(new OperationResponse("Transfer processed",r.transactionId()));}

    @GetMapping("/reconciliation")
    public ResponseEntity<ReconciliationReport> reconcile(){
        return ResponseEntity.ok(service.reconcile());}

    @PostMapping("/reconciliation/runs")
    public ResponseEntity<RunDetails> runReconciliationNow() {
        Long runId = reconciliationRunner.runNow();
        return ResponseEntity.ok(reconciliationHistory.details(runId));
    }

    @GetMapping("/reconciliation/runs")
    public ResponseEntity<List<RunSummary>> reconciliationHistory() {
        return ResponseEntity.ok(reconciliationHistory.history());
    }

    @GetMapping("/reconciliation/runs/{id}")
    public ResponseEntity<RunDetails> reconciliationRun(@PathVariable Long id) {
        return ResponseEntity.ok(reconciliationHistory.details(id));
    }
}
