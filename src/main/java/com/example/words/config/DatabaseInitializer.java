package com.example.words.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import jakarta.annotation.PostConstruct;

@Configuration
@ConditionalOnProperty(name = "database.init.enabled", havingValue = "true", matchIfMissing = false)
public class DatabaseInitializer {

    private static final Logger log = LoggerFactory.getLogger(DatabaseInitializer.class);

    private final JdbcTemplate jdbcTemplate;

    @Value("${database.init.script:db/migration/V1__init_word_vocabulary.sql}")
    private String initScript;

    public DatabaseInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void initializeDatabase() {
        log.info("Initializing database with script: {}", initScript);
        try {
            ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
            populator.setContinueOnError(false);
            populator.addScript(new ClassPathResource(initScript));
            populator.populate(jdbcTemplate.getDataSource().getConnection());
            log.info("Database initialization completed successfully");
        } catch (Exception e) {
            log.error("Failed to initialize database: {}", e.getMessage());
        }
    }
}
