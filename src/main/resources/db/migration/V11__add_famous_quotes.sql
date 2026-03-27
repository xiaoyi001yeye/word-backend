CREATE TABLE IF NOT EXISTS famous_quotes (
    id BIGSERIAL PRIMARY KEY,
    quote_text TEXT NOT NULL,
    author VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_famous_quotes_text_author UNIQUE (quote_text, author)
);

INSERT INTO famous_quotes (quote_text, author)
VALUES
    ('The future belongs to those who believe in the beauty of their dreams.', 'Eleanor Roosevelt'),
    ('Success is not final, failure is not fatal: it is the courage to continue that counts.', 'Winston Churchill'),
    ('It always seems impossible until it is done.', 'Nelson Mandela'),
    ('The only way to do great work is to love what you do.', 'Steve Jobs'),
    ('In the middle of difficulty lies opportunity.', 'Albert Einstein'),
    ('Learning never exhausts the mind.', 'Leonardo da Vinci'),
    ('I hear and I forget. I see and I remember. I do and I understand.', 'Confucius'),
    ('Well done is better than well said.', 'Benjamin Franklin'),
    ('The secret of getting ahead is getting started.', 'Mark Twain'),
    ('What we think, we become.', 'Buddha')
ON CONFLICT (quote_text, author) DO NOTHING;
