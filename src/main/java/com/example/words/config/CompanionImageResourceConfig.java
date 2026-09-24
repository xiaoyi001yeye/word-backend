package com.example.words.config;

import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CompanionImageResourceConfig implements WebMvcConfigurer {

    private final String imageDirectory;

    public CompanionImageResourceConfig(
            @Value("${companion.image-directory:uploads/companion-classrooms}") String imageDirectory) {
        this.imageDirectory = Path.of(imageDirectory).toAbsolutePath().normalize().toUri().toString();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/companion-classrooms/**")
                .addResourceLocations(imageDirectory);
    }
}
