package lk.ngbcl.files.service;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Hourly: remove uploads that were started but never completed within 24 h. */
@Component
public class FileCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(FileCleanupJob.class);
    private final FileService files;

    public FileCleanupJob(FileService files) {
        this.files = files;
    }

    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT5M")
    public void run() {
        try {
            int n = files.purgeAbandoned(Duration.ofHours(24));
            if (n > 0) {
                log.info("Removed {} abandoned uploads", n);
            }
        } catch (RuntimeException e) {
            log.warn("Upload cleanup failed: {}", e.getMessage());
        }
    }
}
