package lk.ngbcl.realtime;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Broadcasts versioned state changes. If called inside a transaction, the message is sent
 * only AFTER COMMIT, so clients never see a bid or score that was rolled back.
 */
@Component
public class RealtimePublisher {

    /** Matches the frontend's Versioned<T> envelope (src/lib/realtime/useTopic.ts). */
    public record Envelope<T>(long stateVersion, String type, T payload) {
    }

    private final SimpMessagingTemplate template;

    public RealtimePublisher(SimpMessagingTemplate template) {
        this.template = template;
    }

    public <T> void publish(String destination, long stateVersion, String type, T payload) {
        Envelope<T> envelope = new Envelope<>(stateVersion, type, payload);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    template.convertAndSend(destination, envelope);
                }
            });
        } else {
            template.convertAndSend(destination, envelope);
        }
    }
}
