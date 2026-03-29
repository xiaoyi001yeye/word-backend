package com.example.words.service;

import com.example.words.dto.AiChatMessageRequest;
import com.example.words.dto.GenerateReadingRequest;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AiPromptService {

    public List<AiChatMessageRequest> buildReadingMessages(GenerateReadingRequest request) {
        String systemPrompt = "你是一名英语教学内容助手，请输出适合英语学习的阅读短文。";
        String userPrompt = """
                请根据以下要求生成一篇英语阅读短文：
                主题：%s
                字数：约 %d 词
                难度：%s
                输出格式：
                标题：...
                正文：...
                """.formatted(
                request.getTopic().trim(),
                request.getWordCount(),
                request.getDifficulty().trim()
        );

        return List.of(
                new AiChatMessageRequest("system", systemPrompt),
                new AiChatMessageRequest("user", userPrompt)
        );
    }
}
