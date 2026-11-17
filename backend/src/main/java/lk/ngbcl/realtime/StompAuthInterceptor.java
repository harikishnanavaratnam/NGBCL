package lk.ngbcl.realtime;

import org.springframework.core.convert.converter.Converter;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.stereotype.Component;

/**
 * - CONNECT: optional "Authorization: Bearer …" header; if present it must be valid.
 * - SEND: always rejected (clients never send commands over the socket).
 * - SUBSCRIBE: /topic/public/** for everyone; /topic/staff/** and /user/** need a signed-in user.
 */
@Component
public class StompAuthInterceptor implements ChannelInterceptor {

    private final JwtDecoder decoder;
    private final Converter<Jwt, AbstractAuthenticationToken> converter;

    public StompAuthInterceptor(JwtDecoder decoder, JwtAuthenticationConverter converter) {
        this.decoder = decoder;
        this.converter = converter;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        StompCommand command = accessor.getCommand();
        if (command == StompCommand.CONNECT) {
            String header = accessor.getFirstNativeHeader("Authorization");
            if (header != null && header.startsWith("Bearer ")) {
                try {
                    accessor.setUser(converter.convert(decoder.decode(header.substring(7))));
                } catch (JwtException e) {
                    throw new MessageDeliveryException("Invalid access token");
                }
            }
        } else if (command == StompCommand.SEND) {
            throw new MessageDeliveryException("Commands are not accepted over WebSocket; use the REST API");
        } else if (command == StompCommand.SUBSCRIBE) {
            String dest = accessor.getDestination();
            boolean restricted = dest == null || dest.startsWith("/topic/staff/") || dest.startsWith("/user/")
                    || dest.startsWith("/queue/");
            if (restricted && accessor.getUser() == null) {
                throw new MessageDeliveryException("Sign in to subscribe to " + dest);
            }
            if (dest != null && !dest.startsWith("/topic/") && !dest.startsWith("/user/") && !dest.startsWith("/queue/")) {
                throw new MessageDeliveryException("Unknown destination");
            }
        }
        return message;
    }
}
