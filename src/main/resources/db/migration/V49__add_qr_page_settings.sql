CREATE TABLE qr_page_settings (
    id SMALLINT PRIMARY KEY,
    title VARCHAR(100) NOT NULL,
    background_image_url VARCHAR(500),
    updated_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_qr_page_settings_updated_by FOREIGN KEY (updated_by) REFERENCES users (id)
);

INSERT INTO qr_page_settings (id, title, background_image_url)
VALUES (1, '伴读社区', '/qr-background.png');
