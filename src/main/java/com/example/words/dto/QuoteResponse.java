package com.example.words.dto;

import com.example.words.model.FamousQuote;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuoteResponse {

    private String text;
    private String translation;
    private String author;

    public static QuoteResponse from(FamousQuote famousQuote) {
        return new QuoteResponse(
                famousQuote.getQuoteText(),
                famousQuote.getQuoteTranslation(),
                famousQuote.getAuthor()
        );
    }
}
