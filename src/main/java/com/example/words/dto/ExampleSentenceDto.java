package com.example.words.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExampleSentenceDto {
    @NotBlank(message = "例句不能为空")
    @Size(max = 500, message = "例句长度不能超过500个字符")
    private String sentence;
    
    @Size(max = 500, message = "例句翻译长度不能超过500个字符")
    private String translation;
}