package com.example.words.controller;

import com.example.words.dto.CompanionImageUploadResponse;
import com.example.words.service.CompanionImageStorageService;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/classrooms")
public class CompanionImageController {

    private final CompanionImageStorageService imageStorageService;

    public CompanionImageController(CompanionImageStorageService imageStorageService) {
        this.imageStorageService = imageStorageService;
    }

    @PostMapping(value = "/companion-images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<CompanionImageUploadResponse> upload(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(imageStorageService.store(file));
    }

    @GetMapping("/companion-images/{fileName:.+}")
    public ResponseEntity<Resource> get(@PathVariable String fileName) {
        return imageStorageService.load(fileName)
                .map(resource -> ResponseEntity.ok()
                        .contentType(MediaTypeFactory.getMediaType(resource).orElse(MediaType.APPLICATION_OCTET_STREAM))
                        .body(resource))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
