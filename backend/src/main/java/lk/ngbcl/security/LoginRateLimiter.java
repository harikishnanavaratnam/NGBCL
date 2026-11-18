package lk.ngbcl.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Sliding-window limiter for failed sign-ins (SRS §12 "rate limits on login attempts").
 * In-memory is enough for a single API instance; swap for Bucket4j + Redis if we scale out.
 */
@Component
public class LoginRateLimiter {

    static final int MAX_PER_ACCOUNT = 5;
    static final int MAX_PER_IP = 30;
    static final Duration WINDOW = Duration.ofMinutes(15);

    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();
    private final Clock clock;

    public LoginRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public boolean isBlocked(String ipHash, String email) {
        Instant cutoff = clock.instant().minus(WINDOW);
        return count(accountKey(ipHash, email), cutoff) >= MAX_PER_ACCOUNT || count("ip:" + ipHash, cutoff) >= MAX_PER_IP;
    }

    public void recordFailure(String ipHash, String email) {
        Instant now = clock.instant();
        add(accountKey(ipHash, email), now);
        add("ip:" + ipHash, now);
    }

    public void reset(String ipHash, String email) {
        failures.remove(accountKey(ipHash, email));
    }

    private static String accountKey(String ipHash, String email) {
        return "acct:" + ipHash + ":" + (email == null ? "" : email.toLowerCase(Locale.ROOT));
    }

    private void add(String key, Instant at) {
        failures.compute(key, (k, q) -> {
            Deque<Instant> d = q == null ? new ArrayDeque<>() : q;
            d.addLast(at);
            while (d.size() > MAX_PER_IP) {
                d.removeFirst();
            }
            return d;
        });
    }

    private int count(String key, Instant cutoff) {
        Deque<Instant> d = failures.get(key);
        if (d == null) {
            return 0;
        }
        synchronized (d) {
            d.removeIf(t -> t.isBefore(cutoff));
            return d.size();
        }
    }
}
