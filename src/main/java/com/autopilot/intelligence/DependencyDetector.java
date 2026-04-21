package com.autopilot.intelligence;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Component 4: Dependency Detector
 *
 * Detects external service dependencies (databases, caches, queues)
 * by scanning ALL files for connection string patterns.
 * Language-agnostic — uses protocol/URL patterns only.
 */
@Component
public class DependencyDetector {

    /** Database detection patterns — match connection URIs across any language */
    private static final Map<String, List<Pattern>> DB_PATTERNS = Map.of(
            "mysql", List.of(
                    Pattern.compile("(?i)jdbc:mysql://"),
                    Pattern.compile("(?i)mysql://"),
                    Pattern.compile("(?i)mysql2?://"),
                    Pattern.compile("(?i)com\\.mysql\\."),
                    Pattern.compile("(?i)pymysql"),
                    Pattern.compile("(?i)mysql\\.connector")
            ),
            "postgres", List.of(
                    Pattern.compile("(?i)jdbc:postgresql://"),
                    Pattern.compile("(?i)postgres(ql)?://"),
                    Pattern.compile("(?i)psycopg2"),
                    Pattern.compile("(?i)org\\.postgresql\\.")
            ),
            "mongodb", List.of(
                    Pattern.compile("(?i)mongodb(\\+srv)?://"),
                    Pattern.compile("(?i)mongoose\\.connect"),
                    Pattern.compile("(?i)MongoClient")
            ),
            "sqlite", List.of(
                    Pattern.compile("(?i)sqlite3?://"),
                    Pattern.compile("(?i)jdbc:sqlite:")
            )
    );

    /** Cache/queue detection patterns */
    private static final Map<String, List<Pattern>> CACHE_PATTERNS = Map.of(
            "redis", List.of(
                    Pattern.compile("(?i)redis://"),
                    Pattern.compile("(?i)rediss://"),
                    Pattern.compile("(?i)spring\\.data\\.redis"),
                    Pattern.compile("(?i)ioredis"),
                    Pattern.compile("(?i)redis\\.createClient"),
                    Pattern.compile("(?i)jedis")
            ),
            "rabbitmq", List.of(
                    Pattern.compile("(?i)amqp(s)?://"),
                    Pattern.compile("(?i)spring\\.rabbitmq"),
                    Pattern.compile("(?i)pika\\."),
                    Pattern.compile("(?i)amqplib")
            ),
            "kafka", List.of(
                    Pattern.compile("(?i)kafka://"),
                    Pattern.compile("(?i)bootstrap\\.servers"),
                    Pattern.compile("(?i)kafkajs"),
                    Pattern.compile("(?i)confluent_kafka")
            ),
            "elasticsearch", List.of(
                    Pattern.compile("(?i)elasticsearch://"),
                    Pattern.compile("(?i)@elastic/elasticsearch"),
                    Pattern.compile("(?i)org\\.elasticsearch")
            )
    );

    public record DependencyResult(List<String> databases, List<String> caches) {}

    /**
     * Scan all files in workspace for dependency patterns.
     */
    public DependencyResult detect(Path workspace) {
        Set<String> databases = new LinkedHashSet<>();
        Set<String> caches = new LinkedHashSet<>();

        try {
            Files.walk(workspace)
                    .filter(Files::isRegularFile)
                    .filter(p -> !p.toString().contains("node_modules"))
                    .filter(p -> !p.toString().contains(".git/"))
                    .forEach(path -> {
                        try {
                            // Only scan text files under 1MB
                            if (Files.size(path) > 1_000_000) return;

                            String content = Files.readString(path);

                            for (var entry : DB_PATTERNS.entrySet()) {
                                for (Pattern p : entry.getValue()) {
                                    if (p.matcher(content).find()) {
                                        databases.add(entry.getKey());
                                        break;
                                    }
                                }
                            }

                            for (var entry : CACHE_PATTERNS.entrySet()) {
                                for (Pattern p : entry.getValue()) {
                                    if (p.matcher(content).find()) {
                                        caches.add(entry.getKey());
                                        break;
                                    }
                                }
                            }
                        } catch (IOException ignored) {}
                    });
        } catch (IOException e) {
            System.err.println("⚠️ DependencyDetector error: " + e.getMessage());
        }

        System.out.println("🔗 DependencyDetector: databases=" + databases + " caches=" + caches);

        return new DependencyResult(new ArrayList<>(databases), new ArrayList<>(caches));
    }
}
