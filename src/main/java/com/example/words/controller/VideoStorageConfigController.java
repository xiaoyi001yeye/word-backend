package com.example.words.controller;

import com.example.words.dto.CreateVideoStorageConfigRequest;
import com.example.words.dto.UpdateVideoStorageConfigRequest;
import com.example.words.dto.UpdateVideoStorageConfigStatusRequest;
import com.example.words.dto.VideoStorageConfigResponse;
import com.example.words.dto.VideoStorageConfigTestResponse;
import com.example.words.model.VideoStorageConfigStatus;
import com.example.words.service.VideoStorageConfigService;
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
@RequestMapping("/api/video-storage-configs")
@PreAuthorize("hasRole('ADMIN')")
public class VideoStorageConfigController {

    private final VideoStorageConfigService videoStorageConfigService;

    public VideoStorageConfigController(VideoStorageConfigService videoStorageConfigService) {
        this.videoStorageConfigService = videoStorageConfigService;
    }

    @GetMapping
    public ResponseEntity<List<VideoStorageConfigResponse>> list(
            @RequestParam(required = false) VideoStorageConfigStatus status) {
        return ResponseEntity.ok(videoStorageConfigService.listConfigs(status));
    }

    @GetMapping("/{id}")
    public ResponseEntity<VideoStorageConfigResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(videoStorageConfigService.getConfigResponse(id));
    }

    @PostMapping
    public ResponseEntity<VideoStorageConfigResponse> create(
            @Valid @RequestBody CreateVideoStorageConfigRequest request) {
        return ResponseEntity.ok(videoStorageConfigService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<VideoStorageConfigResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateVideoStorageConfigRequest request) {
        return ResponseEntity.ok(videoStorageConfigService.update(id, request));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<VideoStorageConfigResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateVideoStorageConfigStatusRequest request) {
        return ResponseEntity.ok(videoStorageConfigService.updateStatus(id, request));
    }

    @PatchMapping("/{id}/default")
    public ResponseEntity<VideoStorageConfigResponse> setDefault(@PathVariable Long id) {
        return ResponseEntity.ok(videoStorageConfigService.setDefault(id));
    }

    @PostMapping("/{id}/test")
    public ResponseEntity<VideoStorageConfigTestResponse> test(@PathVariable Long id) {
        return ResponseEntity.ok(videoStorageConfigService.test(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        videoStorageConfigService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
