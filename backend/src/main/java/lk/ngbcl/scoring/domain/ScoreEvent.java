package lk.ngbcl.scoring.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lk.ngbcl.shared.domain.UuidEntity;

/** Append-only score history (SRS score_history). Undo marks the point voided, never deletes it. */
@Entity
@Table(name = "score_events")
public class ScoreEvent extends UuidEntity {

    @Column(name = "match_id", nullable = false)
    private UUID matchId;

    @Column(name = "seq", nullable = false)
    private int seq;

    @Column(name = "client_event_id", nullable = false, length = 80)
    private String clientEventId;

    @Column(name = "type", nullable = false, length = 16)
    private String type;

    @Column(name = "side", length = 1)
    private String side;

    @Column(name = "game_number")
    private Integer gameNumber;

    @Column(name = "score_a")
    private Integer scoreA;

    @Column(name = "score_b")
    private Integer scoreB;

    @Column(name = "voided", nullable = false)
    private boolean voided;

    @Column(name = "note", length = 300)
    private String note;

    @Column(name = "recorded_by")
    private UUID recordedBy;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected ScoreEvent() {
    }

    public ScoreEvent(UUID matchId, int seq, String clientEventId, String type, String side, Integer gameNumber,
            Integer scoreA, Integer scoreB, String note, UUID recordedBy, Instant recordedAt) {
        this.matchId = matchId;
        this.seq = seq;
        this.clientEventId = clientEventId;
        this.type = type;
        this.side = side;
        this.gameNumber = gameNumber;
        this.scoreA = scoreA;
        this.scoreB = scoreB;
        this.note = note;
        this.recordedBy = recordedBy;
        this.recordedAt = recordedAt;
    }

    public void voidIt() {
        this.voided = true;
    }

    public UUID getMatchId() {
        return matchId;
    }

    public int getSeq() {
        return seq;
    }

    public String getClientEventId() {
        return clientEventId;
    }

    public String getType() {
        return type;
    }

    public String getSide() {
        return side;
    }

    public Integer getGameNumber() {
        return gameNumber;
    }

    public Integer getScoreA() {
        return scoreA;
    }

    public Integer getScoreB() {
        return scoreB;
    }

    public boolean isVoided() {
        return voided;
    }

    public String getNote() {
        return note;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }
}
