-- Migrate existing data from old flat structure to new JSONB structure
-- V5__migrate_data_to_jsonb.sql

-- Migrate simple phonetic to phonetic_detail JSONB structure
UPDATE meta_words 
SET phonetic_detail = jsonb_build_object(
    'uk', phonetic,
    'us', phonetic
)
WHERE phonetic IS NOT NULL AND phonetic != '';

-- Migrate simple structure to part_of_speech_detail JSONB structure
UPDATE meta_words 
SET part_of_speech_detail = jsonb_build_array(
    jsonb_build_object(
        'pos', COALESCE(part_of_speech, ''),
        'definitions', jsonb_build_array(
            jsonb_build_object(
                'definition', COALESCE(definition, ''),
                'translation', COALESCE(translation, ''),
                'exampleSentences', CASE 
                    WHEN example_sentence IS NOT NULL AND example_sentence != '' THEN 
                        jsonb_build_array(
                            jsonb_build_object(
                                'sentence', example_sentence,
                                'translation', COALESCE(translation, '')
                            )
                        )
                    ELSE '[]'::jsonb
                END
            )
        ),
        'inflection', '{}'::jsonb,
        'synonyms', '[]'::jsonb,
        'antonyms', '[]'::jsonb
    )
)
WHERE word IS NOT NULL;

-- Validate the migration by checking some sample records
-- SELECT word, phonetic, phonetic_detail, part_of_speech, part_of_speech_detail 
-- FROM meta_words 
-- WHERE phonetic_detail IS NOT NULL 
-- LIMIT 5;