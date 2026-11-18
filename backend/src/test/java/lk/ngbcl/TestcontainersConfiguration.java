package lk.ngbcl;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import lk.ngbcl.files.InMemoryObjectStorage;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Real PostgreSQL 16 (Docker required) and in-memory object storage for integration tests. */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));
    }

    /** R2 stand-in so upload tests run without network or MinIO. */
    @Bean
    @Primary
    InMemoryObjectStorage inMemoryObjectStorage() {
        return new InMemoryObjectStorage();
    }
}
