package com.example.words.controller;

import com.example.words.dto.AiConfigResponse;
import com.example.words.dto.AiConfigTestResponse;
import com.example.words.dto.CreateAiConfigRequest;
import com.example.words.dto.UpdateAiConfigRequest;
import com.example.words.dto.UpdateAiConfigStatusRequest;
import com.example.words.model.AiConfigStatus;
import com.example.words.service.AiConfigService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai-configs")
@PreAuthorize("hasAnyRole('ADMIN','TEACHER','STUDENT')")
public class AiConfigController {

    private final AiConfigService aiConfigService;

    public AiConfigController(AiConfigService aiConfigService) {
        this.aiConfigService = aiConfigService;
    }

    @GetMapping
    public ResponseEntity<List<AiConfigResponse>> list(
            @RequestParam(required = false) AiConfigStatus status,
            @RequestParam(required = false) String providerName) {
        return ResponseEntity.ok(aiConfigService.listMyConfigs(status, providerName));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AiConfigResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(aiConfigService.getMyConfigResponse(id));
    }

    @PostMapping
    public ResponseEntity<AiConfigResponse> create(@Valid @RequestBody CreateAiConfigRequest request) {
        return ResponseEntity.ok(aiConfigService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AiConfigResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateAiConfigRequest request) {
        return ResponseEntity.ok(aiConfigService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        aiConfigService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<AiConfigResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateAiConfigStatusRequest request) {
        return ResponseEntity.ok(aiConfigService.updateStatus(id, request));
    }

    @PatchMapping("/{id}/default")
    public ResponseEntity<AiConfigResponse> setDefault(@PathVariable Long id) {
        return ResponseEntity.ok(aiConfigService.setDefault(id));
    }

    @PostMapping("/{id}/test")
    public ResponseEntity<AiConfigTestResponse> test(@PathVariable Long id) {
        return ResponseEntity.ok(aiConfigService.test(id));
    }
}
