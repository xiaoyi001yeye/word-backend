package com.example.words.config;

import java.sql.Connection;
import java.util.Arrays;

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
@ConditionalOnProperty(name = "database.init.enabled", havingValue = "true", matchIfMissing = true)
public class DatabaseInitializer {

    private static final Logger log = LoggerFactory.getLogger(DatabaseInitializer.class);

    private final JdbcTemplate jdbcTemplate;

    @Value("${database.init.scripts:db/migration/V1__init_word_vocabulary.sql,db/migration/V2__create_dictionary_tables.sql,db/migration/V3__add_dictionary_creation_type.sql,db/migration/V4__add_jsonb_fields_to_meta_words.sql,db/migration/V5__migrate_data_to_jsonb.sql,db/migration/V7__ensure_dictionary_words_unique_constraint.sql,db/migration/V7__create_exam_tables.sql,db/migration/V8__add_selected_option_to_exam_questions.sql,db/migration/V9__add_user_role_support.sql,db/migration/V10__add_classroom_support.sql,db/migration/V11__add_famous_quotes.sql,db/migration/V13__add_famous_quote_translations.sql}")
    private String[] initScripts;

    public DatabaseInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void initializeDatabase() {
        log.info("Initializing database with scripts: {}", Arrays.toString(initScripts));
        try {
            ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
            populator.setContinueOnError(true);

            for (String initScript : initScripts) {
                populator.addScript(new ClassPathResource(initScript.trim()));
            }

            try (Connection connection = jdbcTemplate.getDataSource().getConnection()) {
                populator.populate(connection);
            }
            log.info("Database initialization completed successfully");
        } catch (Exception e) {
            log.error("Failed to initialize database", e);
        }
    }
}
