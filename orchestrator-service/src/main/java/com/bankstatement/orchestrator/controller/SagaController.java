package com.bankstatement.orchestrator.controller;

import com.bankstatement.common.dto.SagaStatusResponse;
import com.bankstatement.common.dto.TransactionRequest;
import com.bankstatement.orchestrator.model.SagaInstance;
import com.bankstatement.orchestrator.repository.SagaInstanceRepository;
import com.bankstatement.orchestrator.saga.TransactionSagaOrchestrator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/saga")
@CrossOrigin(origins = "*")
public class SagaController {

    private final TransactionSagaOrchestrator orchestrator;
    private final SagaInstanceRepository sagaRepository;

    public SagaController(TransactionSagaOrchestrator orchestrator,
                          SagaInstanceRepository sagaRepository) {
        this.orchestrator = orchestrator;
        this.sagaRepository = sagaRepository;
    }

    @PostMapping("/transaction")
    public ResponseEntity<Map<String, Object>> startTransaction(@RequestBody TransactionRequest request) {
        SagaInstance saga = orchestrator.startSaga(request);
        Map<String, Object> response = new HashMap<>();
        response.put("sagaId", saga.getSagaId());
        response.put("status", saga.getCurrentStatus());
        response.put("message", "Saga started successfully");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/status/{sagaId}")
    public ResponseEntity<SagaStatusResponse> getSagaStatus(@PathVariable String sagaId) {
        return sagaRepository.findById(sagaId)
                .map(saga -> {
                    SagaStatusResponse response = new SagaStatusResponse();
                    response.setSagaId(saga.getSagaId());
                    response.setCurrentStatus(saga.getCurrentStatus());
                    response.setStartedAt(saga.getStartedAt());
                    response.setCompletedAt(saga.getCompletedAt());
                    response.setSteps(saga.getSteps().stream().map(step -> {
                        SagaStatusResponse.SagaStep s = new SagaStatusResponse.SagaStep();
                        s.setStepName(step.getStepName());
                        s.setStatus(step.getStatus());
                        s.setService(step.getService());
                        s.setTimestamp(step.getTimestamp());
                        s.setErrorMessage(step.getErrorMessage());
                        return s;
                    }).collect(Collectors.toList()));
                    return ResponseEntity.ok(response);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/list")
    public ResponseEntity<List<SagaStatusResponse>> listAllSagas() {
        List<SagaStatusResponse> sagas = sagaRepository.findAll().stream().map(saga -> {
            SagaStatusResponse response = new SagaStatusResponse();
            response.setSagaId(saga.getSagaId());
            response.setCurrentStatus(saga.getCurrentStatus());
            response.setStartedAt(saga.getStartedAt());
            response.setCompletedAt(saga.getCompletedAt());
            response.setSteps(saga.getSteps().stream().map(step -> {
                SagaStatusResponse.SagaStep s = new SagaStatusResponse.SagaStep();
                s.setStepName(step.getStepName());
                s.setStatus(step.getStatus());
                s.setService(step.getService());
                s.setTimestamp(step.getTimestamp());
                s.setErrorMessage(step.getErrorMessage());
                return s;
            }).collect(Collectors.toList()));
            return response;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(sagas);
    }
}
