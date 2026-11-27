package com.quipux.playlist;

import java.util.Arrays;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PlaylistApplication {

    public static void main(String[] args) {
        if (Arrays.asList(args).contains(CatalogIndexApplication.COMMAND)) {
            System.exit(CatalogIndexApplication.run(args));
        }
        if (Arrays.asList(args).contains(RecommendationEvaluationApplication.COMMAND)) {
            System.exit(RecommendationEvaluationApplication.run(args));
        }
        SpringApplication.run(PlaylistApplication.class, args);
    }
}
