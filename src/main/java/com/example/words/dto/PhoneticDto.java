package com.example.words.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import jakarta.validation.constraints.Size;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PhoneticDto {
    @Size(max = 100, message = "英式音标长度不能超过100个字符")
    private String uk;
    
    @Size(max = 100, message = "美式音标长度不能超过100个字符")
    private String us;
}