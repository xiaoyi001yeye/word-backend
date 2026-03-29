package com.example.words.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateTagRequest {

    @NotBlank(message = "标签名称不能为空")
    private String name;

    private Long parentId;

    @Min(value = 1, message = "排序值最小为1")
    private Integer sortOrder = 1;
}
