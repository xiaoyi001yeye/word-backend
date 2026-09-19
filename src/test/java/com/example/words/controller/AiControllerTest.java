package com.example.words.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.example.words.service.AiConversationService;
import com.example.words.service.AiGenerationService;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AiControllerTest {

    @Test
    void unpublishedWordDetailsV2EndpointShouldNotBeExposed() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AiController(
                mock(AiConversationService.class),
                mock(AiGenerationService.class)
        )).build();

        mockMvc.perform(post("/api/ai/generate-word-details-v2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"word\":\"ability\"}"))
                .andExpect(status().isNotFound());
    }
}
