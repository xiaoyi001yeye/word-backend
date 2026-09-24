package com.example.words.controller;

import com.example.words.dto.QrPageSettingResponse;
import com.example.words.dto.UpdateQrPageSettingRequest;
import com.example.words.service.CurrentUserService;
import com.example.words.service.QrPageSettingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/qr")
public class QrPageSettingController {

    private final QrPageSettingService qrPageSettingService;
    private final CurrentUserService currentUserService;

    public QrPageSettingController(
            QrPageSettingService qrPageSettingService,
            CurrentUserService currentUserService) {
        this.qrPageSettingService = qrPageSettingService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public ResponseEntity<QrPageSettingResponse> get() {
        return ResponseEntity.ok(qrPageSettingService.getSettings());
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<QrPageSettingResponse> update(
            @Valid @RequestBody UpdateQrPageSettingRequest request) {
        return ResponseEntity.ok(qrPageSettingService.update(request, currentUserService.getCurrentUser()));
    }
}
