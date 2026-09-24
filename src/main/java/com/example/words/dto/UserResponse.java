package com.example.words.dto;

import com.example.words.model.AppUser;
import com.example.words.model.UserRole;
import com.example.words.model.UserStatus;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

    private Long id;
    private String username;
    private String displayName;
    private String email;
    private String phone;
    private String avatarKey;
    private String gender;
    private String schoolName;
    private String grade;
    private List<String> interestTags;
    private String teachingStage;
    private List<String> expertiseTags;
    private UserRole role;
    private UserStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastLoginAt;

    public static UserResponse from(AppUser user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getEmail(),
                user.getPhone(),
                user.getAvatarKey(),
                user.getGender(),
                user.getSchoolName(),
                user.getGrade(),
                user.getInterestTags(),
                user.getTeachingStage(),
                user.getExpertiseTags(),
                user.getRole(),
                user.getStatus(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                user.getLastLoginAt()
        );
    }
}
