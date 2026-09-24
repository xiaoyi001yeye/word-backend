package com.example.words.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "qr_page_settings")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QrPageSetting {

    @Id
    private Short id;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(name = "background_image_url", length = 500)
    private String backgroundImageUrl;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
