package com.example.words.dto;

import com.example.words.model.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateUserRequest {

    public CreateUserRequest(
            String username,
            String password,
            String displayName,
            String email,
            String phone,
            UserRole role) {
        this.username = username;
        this.password = password;
        this.displayName = displayName;
        this.email = email;
        this.phone = phone;
        this.role = role;
    }

    @NotBlank(message = "username is required")
    private String username;

    @NotBlank(message = "password is required")
    @Size(min = 6, message = "password must be at least 6 characters")
    private String password;

    @NotBlank(message = "displayName is required")
    private String displayName;

    private String email;

    private String phone;

    @NotNull(message = "role is required")
    private UserRole role;

    private String avatarKey;

    private String gender;

    private String schoolName;

    private String grade;

    private List<String> interestTags;

    private String teachingStage;

    private List<String> expertiseTags;
}
