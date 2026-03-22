package com.example.words.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import jakarta.validation.constraints.Size;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InflectionDto {
    @Size(max = 100, message = "复数形式长度不能超过100个字符")
    private String plural;
    
    @Size(max = 100, message = "过去式长度不能超过100个字符")
    private String past;
    
    @Size(max = 100, message = "过去分词长度不能超过100个字符")
    private String pastParticiple;
    
    @Size(max = 100, message = "现在分词长度不能超过100个字符")
    private String presentParticiple;
    
    @Size(max = 100, message = "第三人称单数长度不能超过100个字符")
    private String thirdPersonSingular;
    
    @Size(max = 100, message = "比较级长度不能超过100个字符")
    private String comparative;
    
    @Size(max = 100, message = "最高级长度不能超过100个字符")
    private String superlative;
}