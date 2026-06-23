package com.example.words.config;

import com.example.words.service.SparkJunior1600SeedService;
import com.example.words.service.SparkJunior1600SeedService.SeedResult;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class SparkJunior1600SeedRunner implements ApplicationRunner {

    private static final String SEED_OPTION = "seed.spark-junior-1600";

    private final SparkJunior1600SeedService seedService;
    private final ConfigurableApplicationContext applicationContext;

    public SparkJunior1600SeedRunner(
            SparkJunior1600SeedService seedService,
            ConfigurableApplicationContext applicationContext) {
        this.seedService = seedService;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!isEnabled(args)) {
            return;
        }

        SeedResult result = seedService.reset();
        log.info(
                "Seeded Spark junior 1600 data: teacherId={}, studentIds={}, classroomId={}, "
                        + "dictionaryId={}, studyPlanId={}",
                result.teacherId(),
                result.studentIds(),
                result.classroomId(),
                result.dictionaryId(),
                result.studyPlanId()
        );
        SpringApplication.exit(applicationContext, () -> 0);
    }

    private boolean isEnabled(ApplicationArguments args) {
        if (!args.containsOption(SEED_OPTION)) {
            return false;
        }
        List<String> values = args.getOptionValues(SEED_OPTION);
        return values == null || values.isEmpty() || Boolean.parseBoolean(values.get(0));
    }
}
