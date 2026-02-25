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
public class PartOfSpeechDto {
    @NotBlank(message = "词性不能为空")
    @Size(max = 50, message = "词性长度不能超过50个字符")
    private String pos;
    
    private List<DefinitionDto> definitions;
    private InflectionDto inflection;
    private List<String> synonyms;
    private List<String> antonyms;
}