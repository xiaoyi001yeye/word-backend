package com.example.words.service;

import com.example.words.dto.QrPageSettingResponse;
import com.example.words.dto.UpdateQrPageSettingRequest;
import com.example.words.exception.ResourceNotFoundException;
import com.example.words.model.AppUser;
import com.example.words.model.QrPageSetting;
import com.example.words.repository.QrPageSettingRepository;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.Base64;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QrPageSettingService {

    private static final short SETTINGS_ID = 1;
    private static final String SHARE_URL = "http://124.174.44.175";

    private final QrPageSettingRepository repository;

    public QrPageSettingService(QrPageSettingRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public QrPageSettingResponse getSettings() {
        QrPageSetting setting = repository.findById(SETTINGS_ID)
                .orElseThrow(() -> new ResourceNotFoundException("二维码页面配置不存在"));
        return toResponse(setting);
    }

    @Transactional
    public QrPageSettingResponse update(UpdateQrPageSettingRequest request, AppUser actor) {
        QrPageSetting setting = repository.findById(SETTINGS_ID)
                .orElseThrow(() -> new ResourceNotFoundException("二维码页面配置不存在"));
        setting.setTitle(request.title().trim());
        setting.setBackgroundImageUrl(blankToNull(request.backgroundImageUrl()));
        setting.setUpdatedBy(actor.getId());
        setting.setUpdatedAt(LocalDateTime.now());
        return toResponse(repository.save(setting));
    }

    private QrPageSettingResponse toResponse(QrPageSetting setting) {
        return new QrPageSettingResponse(
                setting.getTitle(),
                setting.getBackgroundImageUrl(),
                SHARE_URL,
                generateQrDataUrl(SHARE_URL));
    }

    private String generateQrDataUrl(String content) {
        try {
            var matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 520, 520);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", output);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (Exception exception) {
            throw new IllegalStateException("生成二维码失败", exception);
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
