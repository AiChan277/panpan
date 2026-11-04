package app.panyaya.model.user;

import app.panyaya.exception.ValidationException;
import app.panyaya.model.BaseEntity;
import app.panyaya.util.TextUtil;

import java.time.Instant;
import java.util.Locale;

/**
 * A person who signs in to Pan-ya Ya. Only a salted password hash is stored, never the password.
 * Users are deactivated instead of deleted so that old sales still show who made them.
 */
public class User extends BaseEntity {

    public static final int MIN_PASSWORD_LENGTH = 8;
    public static final int MAX_FAILED_ATTEMPTS = 5;
    public static final long LOCK_MINUTES = 5;

    private String username;
    private String displayName;
    private Role role;
    private String passwordHash;
    private boolean active = true;
    private boolean mustChangePassword;
    private int failedAttempts;
    private Instant lockedUntil;
    private Instant createdAt;

    private User() {
    }

    public User(long id, String username, String displayName, Role role, String passwordHash, Instant createdAt)
            throws ValidationException {
        super(id);
        String cleanUsername = TextUtil.normalize(username).toLowerCase(Locale.ROOT);
        if (!cleanUsername.matches("[a-z0-9._-]{3,20}")) {
            throw new ValidationException("Username must be 3-20 characters: letters, digits, '.', '_' or '-'.");
        }
        this.username = cleanUsername;
        this.passwordHash = passwordHash;
        this.createdAt = createdAt;
        update(displayName, role);
    }

    public void update(String newDisplayName, Role newRole) throws ValidationException {
        String name = TextUtil.normalize(newDisplayName);
        if (name.isEmpty() || name.length() > 40) {
            throw new ValidationException("Display name must be 1-40 characters.");
        }
        if (newRole == null) {
            throw new ValidationException("Please choose a role.");
        }
        this.displayName = name;
        this.role = newRole;
    }

    public void changePasswordHash(String newHash, boolean forceChangeAtNextLogin) {
        this.passwordHash = newHash;
        this.mustChangePassword = forceChangeAtNextLogin;
        this.failedAttempts = 0;
        this.lockedUntil = null;
    }

    public boolean isLockedAt(Instant now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    /** Counts a failed sign-in; the account is locked for a few minutes after too many failures. */
    public void recordFailedLogin(Instant now) {
        failedAttempts++;
        if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
            lockedUntil = now.plusSeconds(LOCK_MINUTES * 60);
            failedAttempts = 0;
        }
    }

    public void recordSuccessfulLogin() {
        failedAttempts = 0;
        lockedUntil = null;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean hasPermission(Permission permission) {
        return active && role.hasPermission(permission);
    }

    public String getUsername() {
        return username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Role getRole() {
        return role;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public String toString() {
        return displayName + " (" + role + ")";
    }
}
