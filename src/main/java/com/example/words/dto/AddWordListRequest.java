package com.example.words.dto;

import lombok.Data;
import java.util.List;

@Data
public class AddWordListRequest {
    private List<String> words;
}