package io.github.intisy.docker;

import io.github.intisy.docker.model.CreateContainerResponse;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for running multiple Docker provider instances simultaneously.
 *
 * @author Finn Birich
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MultiInstanceTest {
    private static final Logger log = LoggerFactory.getLogger(MultiInstanceTest.class);

    @Test
    @Order(1)
    @DisplayName("Multiple providers have unique instance IDs")
    void testUniqueInstanceIds() {
        DockerProvider provider1 = DockerProvider.get();
        DockerProvider provider2 = DockerProvider.get();
        DockerProvider provider3 = DockerProvider.get();

        assertNotEquals(provider1.getInstanceId(), provider2.getInstanceId(), 
                "Provider 1 and 2 should have different instance IDs");
        assertNotEquals(provider2.getInstanceId(), provider3.getInstanceId(), 
                "Provider 2 and 3 should have different instance IDs");
        assertNotEquals(provider1.getInstanceId(), provider3.getInstanceId(), 
                "Provider 1 and 3 should have different instance IDs");

        log.info("Instance IDs: {}, {}, {}", 
                provider1.getInstanceId(), 
                provider2.getInstanceId(), 
                provider3.getInstanceId());
    }

    @Test
    @Order(2)
    @DisplayName("Two providers can run simultaneously")
    @Timeout(120)
    void testTwoProvidersSimultaneously() throws Exception {
        DockerProvider provider1 = DockerProvider.get();
        DockerProvider provider2 = DockerProvider.get();

        try {
            log.info("Starting provider 1 (instance: {})...", provider1.getInstanceId());
            provider1.start();
            
            log.info("Starting provider 2 (instance: {})...", provider2.getInstanceId());
            provider2.start();

            DockerClient client1 = provider1.getClient();
            DockerClient client2 = provider2.getClient();

            assertTrue(client1.ping().exec(), "Client 1 should be able to ping");
            assertTrue(client2.ping().exec(), "Client 2 should be able to ping");

            String containerName1 = "multi-test-1-" + System.currentTimeMillis();
            String containerName2 = "multi-test-2-" + System.currentTimeMillis();

            client1.pullImage("alpine:latest").exec(5, java.util.concurrent.TimeUnit.MINUTES);

            CreateContainerResponse response1 = client1.createContainer("alpine:latest")
                    .withName(containerName1)
                    .withCmd("sleep", "10")
                    .exec();
            
            CreateContainerResponse response2 = client2.createContainer("alpine:latest")
                    .withName(containerName2)
                    .withCmd("sleep", "10")
                    .exec();

            assertNotNull(response1.getId(), "Container 1 should be created");
            assertNotNull(response2.getId(), "Container 2 should be created");

            log.info("Created container 1: {} on provider {}", response1.getId().substring(0, 12), provider1.getInstanceId());
            log.info("Created container 2: {} on provider {}", response2.getId().substring(0, 12), provider2.getInstanceId());

            client1.removeContainer(response1.getId()).withForce(true).exec();
            client2.removeContainer(response2.getId()).withForce(true).exec();

        } finally {
            provider1.stop();
            provider2.stop();
        }
    }
}
