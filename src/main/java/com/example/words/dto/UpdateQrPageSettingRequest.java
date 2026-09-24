package com.example.words.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateQrPageSettingRequest(
        @NotBlank(message = "伴读社区文本不能为空")
        @Size(max = 100, message = "伴读社区文本不能超过100个字符")
        String title,
        @Size(max = 500, message = "背景图地址不能超过500个字符")
        String backgroundImageUrl) {
}
