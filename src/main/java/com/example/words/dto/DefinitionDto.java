package com.example.words.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DefinitionDto {
    @NotBlank(message = "定义不能为空")
    @Size(max = 1000, message = "定义长度不能超过1000个字符")
    private String definition;
    
    @Size(max = 500, message = "翻译长度不能超过500个字符")
    private String translation;
    
    private List<ExampleSentenceDto> exampleSentences;
}