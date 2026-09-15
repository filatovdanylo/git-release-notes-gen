package me.automatedgitdiffnotesgenerator;

import me.automatedgitdiffnotesgenerator.security.config.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(JwtProperties.class)
public class AutomatedGitDiffNotesGeneratorApplication {

    public static void main(String[] args) {
        SpringApplication.run(AutomatedGitDiffNotesGeneratorApplication.class, args);
    }

}
