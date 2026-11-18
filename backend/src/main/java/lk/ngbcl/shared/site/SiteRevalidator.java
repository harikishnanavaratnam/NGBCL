package lk.ngbcl.shared.site;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestClient;

/**
 * Tells the public website which cached data changed (cache tags such as "teams", "news").
 * Sent once AFTER COMMIT, in the background, so a slow or offline website never affects the API.
 */
@Component
public class SiteRevalidator {

    /** Cache tags used by the frontend (src/lib/api/public.ts). */
    public static final String TOURNAMENT = "tournament";
    public static final String TEAMS = "teams";
    public static final String PLAYERS = "players";
    public static final String NEWS = "news";
    public static final String SPONSORS = "sponsors";
    public static final String COMMITTEE = "committee";

    private static final Logger log = LoggerFactory.getLogger(SiteRevalidator.class);
    private static final Object KEY = new Object();

    private final SiteProperties props;
    private final RestClient http;

    public SiteRevalidator(SiteProperties props, RestClient.Builder builder) {
        this.props = props;
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(Duration.ofSeconds(3));
        rf.setReadTimeout(Duration.ofSeconds(5));
        this.http = builder.requestFactory(rf).build();
    }

    public void touch(String... tags) {
        if (!props.enabled()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            send(Set.of(tags));
            return;
        }
        @SuppressWarnings("unchecked")
        Set<String> pending = (Set<String>) TransactionSynchronizationManager.getResource(KEY);
        if (pending == null) {
            Set<String> fresh = new LinkedHashSet<>();
            TransactionSynchronizationManager.bindResource(KEY, fresh);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(Set.copyOf(fresh));
                }

                @Override
                public void afterCompletion(int status) {
                    TransactionSynchronizationManager.unbindResourceIfPossible(KEY);
                }
            });
            pending = fresh;
        }
        pending.addAll(Set.of(tags));
    }

    private void send(Set<String> tags) {
        Thread.ofVirtual().start(() -> {
            try {
                http.post().uri(props.revalidateUrl()).contentType(MediaType.APPLICATION_JSON)
                        .header("X-Revalidate-Secret", props.revalidateSecret())
                        .body(Map.of("tags", tags)).retrieve().toBodilessEntity();
            } catch (RuntimeException e) {
                log.debug("Site revalidation failed for {}: {}", tags, e.getMessage());
            }
        });
    }
}
