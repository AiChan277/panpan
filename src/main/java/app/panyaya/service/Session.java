package app.panyaya.service;

import app.panyaya.exception.AuthorizationException;
import app.panyaya.model.BakeryData;
import app.panyaya.model.user.Permission;
import app.panyaya.model.user.User;
import app.panyaya.persistence.DataStore;

/**
 * Who is signed in. Every service checks permissions here, so hiding a button in the UI is only a
 * convenience - the real protection is {@link #require}.
 *
 * <p>Only the user id is kept: after a rollback the data objects are rebuilt from JSON, so holding
 * on to an old {@code User} object would be unsafe.</p>
 */
public class Session {

    private final DataStore store;
    private long userId;

    public Session(DataStore store) {
        this.store = store;
    }

    public void signIn(User user) {
        this.userId = user.getId();
    }

    public void signOut() {
        this.userId = 0;
    }

    public boolean isSignedIn() {
        return userId > 0 && getUser() != null;
    }

    public long getUserId() {
        return userId;
    }

    public User getUser() {
        if (userId <= 0 || !store.isLoaded()) {
            return null;
        }
        return BakeryData.findById(store.getData().getUsers(), userId);
    }

    public boolean can(Permission permission) {
        User user = getUser();
        return user != null && user.hasPermission(permission);
    }

    public void require(Permission permission) throws AuthorizationException {
        User user = getUser();
        if (user == null) {
            throw new AuthorizationException("Please sign in first.");
        }
        if (!user.hasPermission(permission)) {
            throw new AuthorizationException("Your role (" + user.getRole() + ") is not allowed to do this.");
        }
    }

    /** Display name for audit entries; "system" before anyone signed in (first-run setup). */
    public String getUsername() {
        User user = getUser();
        return user == null ? "system" : user.getUsername();
    }
}
