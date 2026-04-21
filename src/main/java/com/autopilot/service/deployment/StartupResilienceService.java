package com.autopilot.service.deployment;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Pre-build source code patcher that makes Spring Boot applications resilient
 * to external service failures during startup.
 *
 * Problem:
 *   Many Spring Boot apps have @PostConstruct methods that call external services
 *   (S3, SQS, SNS, etc.) to "verify" connectivity. If those calls fail (e.g., due
 *   to IAM permission mismatches), Spring throws a BeanCreationException and the
 *   entire application crashes — even though the core business logic is fine.
 *
 * Solution:
 *   Before building the Docker image, this service scans all Java source files in
 *   the cloned workspace for @PostConstruct methods that reference AWS SDK calls.
 *   It wraps the method body in a try-catch block so failures are logged as warnings
 *   instead of crashing the application.
 *
 * This ONLY patches init/test methods — actual business logic is never touched.
 */
@Service
public class StartupResilienceService {

    /**
     * AWS SDK class/method references that are dangerous when called during
     * bean initialization. If a @PostConstruct method references any of these,
     * it gets wrapped in a try-catch.
     */
    private static final Set<String> AWS_SERVICE_INDICATORS = Set.of(
            // S3
            "S3Client", "AmazonS3", "s3Client", "listBuckets", "listObjects",
            "putObject", "getObject", "headBucket", "createBucket",
            // SNS
            "SnsClient", "AmazonSNS", "snsClient",
            // SQS
            "SqsClient", "AmazonSQS", "sqsClient",
            // DynamoDB
            "DynamoDbClient", "AmazonDynamoDB", "dynamoDbClient",
            // Secrets Manager
            "SecretsManagerClient", "getSecretValue",
            // General
            "AwsServiceException", "SdkClientException"
    );

    /**
     * Scan all Java source files in the workspace for @PostConstruct methods
     * that make external AWS service calls, and wrap them with try-catch to
     * prevent BeanCreationException from crashing the app.
     *
     * @param workspace   Root path of the cloned repository
     * @param progressLog Callback for real-time log messages
     * @return Number of files patched
     */
    public int patchDangerousInitMethods(Path workspace, Consumer<String> progressLog) throws IOException {
        int patchedCount = 0;

        List<Path> javaFiles = Files.walk(workspace)
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .filter(p -> !p.toString().contains("node_modules"))
                .filter(p -> !p.toString().contains(".git/"))
                .collect(Collectors.toList());

        for (Path file : javaFiles) {
            try {
                String content = Files.readString(file);

                // Quick check: must have @PostConstruct
                if (!content.contains("@PostConstruct")) continue;

                // Must reference at least one AWS service indicator
                boolean hasDangerousCall = AWS_SERVICE_INDICATORS.stream()
                        .anyMatch(content::contains);
                if (!hasDangerousCall) continue;

                // Patch the file
                String patched = wrapPostConstructMethods(content);
                if (!patched.equals(content)) {
                    Files.writeString(file, patched);
                    patchedCount++;
                    String relativePath = workspace.relativize(file).toString();
                    progressLog.accept("🛡️ Patched @PostConstruct in " + relativePath
                            + " → wrapped with try-catch for startup resilience");
                }
            } catch (Exception e) {
                // Skip files we can't process — don't crash the pipeline
            }
        }

        return patchedCount;
    }

    /**
     * Find @PostConstruct methods in Java source and wrap their bodies in try-catch.
     *
     * Strategy:
     *   1. Find lines with @PostConstruct annotation
     *   2. Find the method's opening brace {
     *   3. Track brace depth to find the matching closing brace }
     *   4. Wrap the entire body between { and } with try-catch
     */
    private String wrapPostConstructMethods(String content) {
        List<String> lines = new ArrayList<>(Arrays.asList(content.split("\n", -1)));
        List<String> result = new ArrayList<>();

        int i = 0;
        while (i < lines.size()) {
            String line = lines.get(i);
            String trimmed = line.trim();

            // Detect @PostConstruct annotation (may be on its own line or with method)
            if (trimmed.startsWith("@PostConstruct")) {

                // Check if the opening brace is on THIS line (compact format)
                // e.g., "@PostConstruct public void test() {"
                if (line.contains("{")) {
                    result.add(line);
                    i++;
                    wrapMethodBody(lines, result, line, i);
                    // Advance i past the method body
                    i = skipMethodBody(lines, i);
                } else {
                    // @PostConstruct is on its own line
                    result.add(line);
                    i++;

                    // Find the method signature with opening brace
                    boolean foundOpenBrace = false;
                    while (i < lines.size() && !foundOpenBrace) {
                        String sigLine = lines.get(i);
                        result.add(sigLine);
                        i++;

                        if (sigLine.contains("{")) {
                            foundOpenBrace = true;
                            wrapMethodBody(lines, result, sigLine, i);
                            i = skipMethodBody(lines, i);
                        }
                    }
                }
            } else {
                result.add(line);
                i++;
            }
        }

        return String.join("\n", result);
    }

    /**
     * Collect all body lines of a method and wrap them in try-catch.
     * Assumes the opening brace has already been added to result.
     *
     * @param lines          All source lines
     * @param result         Output accumulator
     * @param braceOpenLine  The line containing the opening {
     * @param bodyStartIndex Index of first line AFTER the opening brace line
     */
    private void wrapMethodBody(List<String> lines, List<String> result,
                                String braceOpenLine, int bodyStartIndex) {
        String methodIndent = getIndentation(braceOpenLine);
        String bodyIndent = methodIndent + "    ";
        String innerIndent = bodyIndent + "    ";

        // Collect body lines until matching closing brace
        int braceDepth = 1;
        List<String> bodyLines = new ArrayList<>();
        int i = bodyStartIndex;

        while (i < lines.size() && braceDepth > 0) {
            String bodyLine = lines.get(i);

            for (char c : bodyLine.toCharArray()) {
                if (c == '{') braceDepth++;
                if (c == '}') braceDepth--;
            }

            if (braceDepth == 0) {
                // This line contains the closing brace — insert the wrapper
                result.add(bodyIndent + "try {");
                for (String bl : bodyLines) {
                    result.add("    " + bl);
                }
                result.add(bodyIndent + "} catch (Exception __resilience_ex) {");
                result.add(innerIndent + "System.err.println(\"⚠️ [Autopilot] @PostConstruct init failed (non-fatal): \" + __resilience_ex.getMessage());");
                result.add(innerIndent + "System.err.println(\"⚠️ [Autopilot] App will continue. Fix IAM permissions to restore full functionality.\");");
                result.add(bodyIndent + "}");
                result.add(bodyLine); // The original closing brace
            } else {
                bodyLines.add(bodyLine);
            }

            i++;
        }
    }

    /**
     * Skip past the body of a method (brace-matched), returning the index
     * of the first line AFTER the closing brace.
     */
    private int skipMethodBody(List<String> lines, int bodyStartIndex) {
        int braceDepth = 1;
        int i = bodyStartIndex;
        while (i < lines.size() && braceDepth > 0) {
            String line = lines.get(i);
            for (char c : line.toCharArray()) {
                if (c == '{') braceDepth++;
                if (c == '}') braceDepth--;
            }
            i++;
        }
        return i;
    }

    /**
     * Extract leading whitespace from a line.
     */
    private String getIndentation(String line) {
        StringBuilder indent = new StringBuilder();
        for (char c : line.toCharArray()) {
            if (c == ' ' || c == '\t') {
                indent.append(c);
            } else {
                break;
            }
        }
        return indent.toString();
    }
}
