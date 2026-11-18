package lk.ngbcl.registration.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lk.ngbcl.shared.web.ApiException;
import lk.ngbcl.shared.web.RequestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Cloudflare Turnstile check for public form submissions (NFR-009). */
@Component
public class TurnstileVerifier {

    private static final Logger log = LoggerFactory.getLogger(TurnstileVerifier.class);
    private static final String SITEVERIFY = "https://challenges.cloudflare.com/turnstile/v0/siteverify";

    private final RegistrationProperties props;
    private final RequestInfo requestInfo;
    private final RestClient http;

    public TurnstileVerifier(RegistrationProperties props, RequestInfo requestInfo, RestClient.Builder builder) {
        this.props = props;
        this.requestInfo = requestInfo;
        this.http = builder.build();
        if (!props.turnstileEnabled()) {
            log.warn("ngbcl.registration.turnstile-secret is empty — bot check on public forms is OFF");
        }
    }

    record SiteverifyResponse(boolean success, @JsonProperty("error-codes") List<String> errorCodes) {
    }

    public void verify(String token) {
        if (!props.turnstileEnabled()) {
            return;
        }
        if (token == null || token.isBlank() || token.length() > 2048) {
            throw ApiException.badRequest("BOT_CHECK_REQUIRED", "Complete the security check and submit again.");
        }
        var form = new LinkedMultiValueMap<String, String>();
        form.add("secret", props.turnstileSecret());
        form.add("response", token);
        form.add("remoteip", requestInfo.clientIp());
        SiteverifyResponse res;
        try {
            res = http.post().uri(SITEVERIFY).contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form)
                    .retrieve().body(SiteverifyResponse.class);
        } catch (RestClientException e) {
            log.warn("Turnstile verification failed to run: {}", e.getMessage());
            throw new ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "BOT_CHECK_UNAVAILABLE",
                    "We couldn't run the security check. Please try again in a minute.");
        }
        if (res == null || !res.success()) {
            log.debug("Turnstile rejected: {}", res == null ? "no body" : res.errorCodes());
            throw ApiException.badRequest("BOT_CHECK_FAILED", "The security check expired or failed. Please try again.");
        }
    }
}
