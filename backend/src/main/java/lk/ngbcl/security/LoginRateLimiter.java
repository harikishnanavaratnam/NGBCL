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
 * Three keys: account+IP, IP, and the account on its own — the last stops a password-guessing run
 * spread over many (or spoofed) client addresses. In-memory is enough for a single API instance.
 */
@Component
public class LoginRateLimiter {

    static final int MAX_PER_ACCOUNT = 5;
    static final int MAX_PER_IP = 30;
    /** Any IP: a distributed guessing run on one account is stopped here. */
    static final int MAX_PER_EMAIL = 20;
    /** Keeps memory bounded if someone sprays failures from countless addresses. */
    static final int MAX_KEYS = 20_000;
    static final Duration WINDOW = Duration.ofMinutes(15);

    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();
    private final Clock clock;

    public LoginRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public boolean isBlocked(String ipHash, String email) {
        Instant cutoff = clock.instant().minus(WINDOW);
        return count(accountKey(ipHash, email), cutoff) >= MAX_PER_ACCOUNT || count("ip:" + ipHash, cutoff) >= MAX_PER_IP
                || count(emailKey(email), cutoff) >= MAX_PER_EMAIL;
    }

    public void recordFailure(String ipHash, String email) {
        Instant now = clock.instant();
        if (failures.size() > MAX_KEYS) {
            prune(now.minus(WINDOW));
        }
        add(accountKey(ipHash, email), now);
        add("ip:" + ipHash, now);
        add(emailKey(email), now);
    }

    private void prune(Instant cutoff) {
        failures.entrySet().removeIf(e -> {
            Deque<Instant> d = e.getValue();
            synchronized (d) {
                return d.isEmpty() || d.peekLast().isBefore(cutoff);
            }
        });
    }

    private static String emailKey(String email) {
        return "email:" + (email == null ? "" : email.toLowerCase(Locale.ROOT));
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
