package com.autopilot;

import com.autopilot.service.deployment.runtime.dependency.*;
import com.autopilot.service.infrastructure.ec2.StatefulWaitEngine;
import com.autopilot.service.infrastructure.ec2.ObservedState;
import com.autopilot.service.infrastructure.ec2.RuntimeStateObserver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.*;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("Deployrix V5.10 — Dependency Provider Architecture Hardening Test")
public class DependencyProviderHardeningTest {

    @Test
    @DisplayName("DependencyProviderFactory creates correct providers and returns correct commands and connection info")
    void testDependencyProviderContractAndCommands() {
        // MySQL
        DependencyProvider mysql = DependencyProviderFactory.create("mysql");
        assertNotNull(mysql);
        assertEquals("autopilot-mysql", mysql.start().value());
        StartupResult mysqlStartup = mysql.waitUntilReady();
        assertTrue(mysqlStartup.success());
        assertTrue(mysqlStartup.commands().stream().anyMatch(c -> c.contains("mysql:8")));
        assertTrue(mysqlStartup.commands().stream().anyMatch(c -> c.contains("DEBUG: Starting Startup Negotiation Engine")));
        ConnectionInfo mysqlConn = mysql.connectionInfo();
        assertEquals("autopilot-mysql", mysqlConn.host());
        assertEquals(3306, mysqlConn.port());
        assertEquals("autopilot", mysqlConn.username());
        assertEquals("autopilotdb", mysqlConn.database());
        assertTrue(mysqlConn.uri().contains("jdbc:mysql://"));

        // PostgreSQL
        DependencyProvider postgres = DependencyProviderFactory.create("postgres");
        assertNotNull(postgres);
        assertEquals("autopilot-postgres", postgres.start().value());
        StartupResult postgresStartup = postgres.waitUntilReady();
        assertTrue(postgresStartup.success());
        assertTrue(postgresStartup.commands().stream().anyMatch(c -> c.contains("postgres:15")));
        ConnectionInfo postgresConn = postgres.connectionInfo();
        assertEquals("autopilot-postgres", postgresConn.host());
        assertEquals(5432, postgresConn.port());

        // MongoDB
        DependencyProvider mongo = DependencyProviderFactory.create("mongodb");
        assertNotNull(mongo);
        assertEquals("autopilot-mongo", mongo.start().value());
        StartupResult mongoStartup = mongo.waitUntilReady();
        assertTrue(mongoStartup.success());
        assertTrue(mongoStartup.commands().stream().anyMatch(c -> c.contains("mongo:6")));
        ConnectionInfo mongoConn = mongo.connectionInfo();
        assertEquals("autopilot-mongo", mongoConn.host());
        assertEquals(27017, mongoConn.port());

        // Redis
        DependencyProvider redis = DependencyProviderFactory.create("redis");
        assertNotNull(redis);
        assertEquals("autopilot-redis", redis.start().value());
        StartupResult redisStartup = redis.waitUntilReady();
        assertTrue(redisStartup.success());
        assertTrue(redisStartup.commands().stream().anyMatch(c -> c.contains("redis:7-alpine")));
        ConnectionInfo redisConn = redis.connectionInfo();
        assertEquals("autopilot-redis", redisConn.host());
        assertEquals(6379, redisConn.port());
    }

    @Test
    @DisplayName("StatefulWaitEngine terminates wait loop immediately for ANY exit code (including 0)")
    void testStatefulWaitEngineExitCodeZeroFailure() {
        SsmClient ssmClient = mock(SsmClient.class);
        SendCommandResponse sendResp = SendCommandResponse.builder()
                .command(Command.builder().commandId("cmd-exit-zero").build())
                .build();
        when(ssmClient.sendCommand(any(SendCommandRequest.class))).thenReturn(sendResp);

        // Simulate container exited with code 0: running=false, exitCode=0, restartCount=1
        GetCommandInvocationResponse exitInspect = GetCommandInvocationResponse.builder()
                .status(CommandInvocationStatus.FAILED)
                .standardOutputContent(
                        "CRITICAL: Container exited with code 0 (OOMKilled=false)\n" +
                        "=== CONTAINER EXIT DIAGNOSTIC SNAPSHOT ===\n" +
                        "Container 'container-exit-zero' exited with code 0\n" +
                        "OOMKilled=false\n" +
                        "RestartCount=1\n" +
                        "DockerHealth=none\n" +
                        "Restart Count:   1\n" +
                        "Docker Health:   none\n" +
                        "Exit Code:       0\n" +
                        "Classification: ContainerExited"
                )
                .build();

        when(ssmClient.getCommandInvocation(any(GetCommandInvocationRequest.class))).thenReturn(exitInspect);

        List<String> progressLogs = new ArrayList<>();
        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            StatefulWaitEngine.executeWait(
                    ssmClient,
                    "i-exit-zero",
                    "container-exit-zero",
                    3306,
                    3306,
                    "/",
                    "TCP",
                    List.of(200),
                    "mysql",
                    5,
                    progressLogs::add
            );
        });

        // The exception must contain container exit code information
        assertTrue(ex.getMessage().contains("Container 'container-exit-zero' exited with code 0"));
        assertTrue(progressLogs.stream().anyMatch(l -> l.contains("CONTAINER EXIT DIAGNOSTIC SNAPSHOT")));
        assertTrue(progressLogs.stream().anyMatch(l -> l.contains("Exit Code:       0")));
    }
}
