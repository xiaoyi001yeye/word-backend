package com.example.words.service;

import com.example.words.dto.QuoteResponse;
import com.example.words.repository.FamousQuoteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FamousQuoteService {

    private static final QuoteResponse DEFAULT_QUOTE = new QuoteResponse(
            "Learning never exhausts the mind.",
            "学习从不会使头脑疲惫。",
            "Leonardo da Vinci"
    );

    private final FamousQuoteRepository famousQuoteRepository;

    public FamousQuoteService(FamousQuoteRepository famousQuoteRepository) {
        this.famousQuoteRepository = famousQuoteRepository;
    }

    @Transactional(readOnly = true)
    public QuoteResponse getRandomQuote() {
        return famousQuoteRepository.findRandomQuote()
                .map(QuoteResponse::from)
                .orElse(DEFAULT_QUOTE);
    }
}
