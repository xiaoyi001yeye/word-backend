package com.example.words.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Pattern;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MetaWordEntryDto {

    @NotBlank(message = "单词不能为空")
    @Size(min = 1, max = 100, message = "单词长度必须在1到100个字符之间")
    // @Pattern(regexp = "^[a-zA-Z0-9\\s\\-_']+$", message = "单词只能包含字母、数字、空格、连字符、下划线和撇号")
    private String word;

    @Size(max = 200, message = "音标长度不能超过200个字符")
    private String phonetic;

    @Size(max = 2000, message = "定义长度不能超过2000个字符")
    private String definition;

    @Size(max = 50, message = "词性长度不能超过50个字符")
    private String partOfSpeech;

    @Size(max = 1000, message = "例句长度不能超过1000个字符")
    private String exampleSentence;

    @Size(max = 500, message = "翻译长度不能超过500个字符")
    private String translation;

    @Min(value = 1, message = "难度最小值为1")
    @Max(value = 5, message = "难度最大值为5")
    private Integer difficulty = 2;
}