package com.example.words.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AddWordListRequest {
    @NotEmpty(message = "单词列表不能为空")
    private List<MetaWordEntryDto> words;

    @JsonCreator
    public AddWordListRequest(@JsonProperty("words") List<MetaWordEntryDto> words) {
        this.words = words;
    }
}