package lk.ngbcl.files.storage;

import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class StorageConfig {

    @Bean
    ObjectStorage objectStorage(StorageProperties props) {
        if (!props.configured()) {
            LoggerFactory.getLogger(StorageConfig.class)
                    .warn("ngbcl.storage.endpoint is not set — uploads are disabled (STORAGE_NOT_CONFIGURED)");
            return new UnconfiguredObjectStorage();
        }
        return new S3ObjectStorage(props);
    }
}
