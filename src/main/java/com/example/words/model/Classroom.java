package com.example.words.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "classrooms")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Classroom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "teacher_id", nullable = false)
    private Long teacherId;

    @Column(name = "companion_video_id")
    private Long companionVideoId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "companion_image_urls_json", columnDefinition = "jsonb")
    private List<String> companionImageUrls;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "companion_tags_json", columnDefinition = "jsonb")
    private List<String> companionTags;

    @Column(name = "companion_comment_count", nullable = false)
    private long companionCommentCount = 0;

    @Column(name = "companion_like_count", nullable = false)
    private long companionLikeCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ClassroomStatus status = ClassroomStatus.ACTIVE;

    @Column(name = "archived_at")
    private LocalDateTime archivedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
