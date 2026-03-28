package com.example.words.dto;

import com.example.words.model.TagType;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TagTreeNodeResponse {

    private Long id;
    private String name;
    private TagType type;
    private Integer sortOrder;
    private String pathName;
    private List<TagTreeNodeResponse> children = new ArrayList<>();
}
