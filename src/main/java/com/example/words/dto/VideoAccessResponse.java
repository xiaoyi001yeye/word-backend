package com.example.words.dto;

import com.example.words.model.VideoAccessMode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VideoAccessResponse {

    private Long videoId;
    private VideoAccessMode mode;
    private String url;
    private String coverUrl;
}
