package lk.ngbcl.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import lk.ngbcl.security.SecurityProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Client IP (as a salted hash — never stored raw) and user agent for the current request. */
@Component
public class RequestInfo {

    private final String salt;

    public RequestInfo(SecurityProperties props) {
        this.salt = props.ipHashSalt();
    }

    public String clientIp() {
        HttpServletRequest req = current();
        // forward-headers-strategy=framework already resolves X-Forwarded-For into remoteAddr
        return req == null ? "unknown" : req.getRemoteAddr();
    }

    public String ipHash() {
        return sha256Hex(salt + "|" + clientIp());
    }

    public String userAgent() {
        HttpServletRequest req = current();
        String ua = req == null ? null : req.getHeader("User-Agent");
        return ua == null ? null : ua.substring(0, Math.min(ua.length(), 255));
    }

    public static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static HttpServletRequest current() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs
                ? attrs.getRequest()
                : null;
    }
}
