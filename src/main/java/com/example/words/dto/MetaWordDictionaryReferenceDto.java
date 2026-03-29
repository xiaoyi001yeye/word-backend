package com.example.words.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MetaWordDictionaryReferenceDto {

    private Long dictionaryId;
    private String dictionaryName;
    private List<MetaWordReferenceLocationDto> locations = new ArrayList<>();
}
