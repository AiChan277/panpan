package app.panyaya.model;

import java.time.Instant;

/** One line of the audit log: who did what and when. Append-only. */
public class AuditEntry extends BaseEntity {

    private Instant occurredAt;
    private long userId;
    private String username;
    private String action;
    private String details;

    private AuditEntry() {
    }

    public AuditEntry(long id, Instant occurredAt, long userId, String username, String action, String details) {
        super(id);
        this.occurredAt = occurredAt;
        this.userId = userId;
        this.username = username;
        this.action = action;
        this.details = details;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public long getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getAction() {
        return action;
    }

    public String getDetails() {
        return details;
    }
}
