package app.panyaya.service;

import app.panyaya.exception.BusinessException;
import app.panyaya.exception.PersistenceException;
import app.panyaya.exception.ValidationException;
import app.panyaya.model.BakeryData;
import app.panyaya.model.user.Permission;
import app.panyaya.model.user.Role;
import app.panyaya.model.user.User;
import app.panyaya.persistence.DataStore;
import app.panyaya.persistence.EntityTypes;
import app.panyaya.util.TextUtil;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Owner-only management of staff accounts. */
public class UserService extends BaseService {

    private final PasswordHasher hasher;

    public UserService(DataStore store, Session session, Clock clock, PasswordHasher hasher) {
        super(store, session, clock);
        this.hasher = hasher;
    }

    public List<User> getUsers() {
        return new ArrayList<>(data().getUsers());
    }

    /** New users must change the temporary password at their first sign-in. */
    public User createUser(String username, String displayName, Role role, char[] temporaryPassword)
            throws BusinessException, PersistenceException {
        session.require(Permission.MANAGE_USERS);
        AuthService.validateNewPassword(username, temporaryPassword);
        String hash = hasher.hash(temporaryPassword);
        PasswordHasher.wipe(temporaryPassword);
        return store.execute("CREATE_USER", data -> {
            String clean = TextUtil.normalize(username).toLowerCase(Locale.ROOT);
            if (AuthService.findByUsername(data, clean) != null) {
                throw new ValidationException("Username \"" + clean + "\" is already taken.");
            }
            User user = new User(data.nextId(EntityTypes.USER), clean, displayName, role, hash, now());
            user.changePasswordHash(hash, true);
            data.getUsers().add(user);
            audit(data, "CREATE_USER", clean + " as " + role);
            return user;
        });
    }

    public User updateUser(long userId, String displayName, Role role) throws BusinessException, PersistenceException {
        session.require(Permission.MANAGE_USERS);
        return store.execute("UPDATE_USER", data -> {
            User user = BakeryData.require(data.getUsers(), userId, "User");
            if (user.getRole() == Role.OWNER && role != Role.OWNER && user.isActive() && countActiveOwners(data) <= 1) {
                throw new ValidationException("The last active Owner cannot be given another role.");
            }
            user.update(displayName, role);
            audit(data, "UPDATE_USER", user.getUsername() + " -> " + role);
            return user;
        });
    }

    public void setActive(long userId, boolean active) throws BusinessException, PersistenceException {
        session.require(Permission.MANAGE_USERS);
        store.execute(active ? "ACTIVATE_USER" : "DEACTIVATE_USER", data -> {
            User user = BakeryData.require(data.getUsers(), userId, "User");
            if (!active) {
                if (user.getId() == session.getUserId()) {
                    throw new ValidationException("You cannot deactivate your own account.");
                }
                if (user.getRole() == Role.OWNER && countActiveOwners(data) <= 1) {
                    throw new ValidationException("The last active Owner cannot be deactivated.");
                }
            }
            user.setActive(active);
            audit(data, active ? "ACTIVATE_USER" : "DEACTIVATE_USER", user.getUsername());
            return null;
        });
    }

    public void resetPassword(long userId, char[] temporaryPassword) throws BusinessException, PersistenceException {
        session.require(Permission.MANAGE_USERS);
        User target = BakeryData.require(data().getUsers(), userId, "User");
        AuthService.validateNewPassword(target.getUsername(), temporaryPassword);
        String hash = hasher.hash(temporaryPassword);
        PasswordHasher.wipe(temporaryPassword);
        store.execute("RESET_PASSWORD", data -> {
            User user = BakeryData.require(data.getUsers(), userId, "User");
            user.changePasswordHash(hash, true);
            audit(data, "RESET_PASSWORD", user.getUsername());
            return null;
        });
    }

    /**
     * Mengubah pertanyaan dan jawaban keamanan milik user yang sedang login.
     * Dipakai di Settings agar user bisa menyiapkan "Lupa Kata Sandi".
     */
    public void setSecurityQA(String question, String answer) throws BusinessException, PersistenceException {
        User me = session.getUser();
        if (me == null) {
            throw new ValidationException("Please sign in first.");
        }
        final long id = me.getId();
        store.execute("SET_SECURITY_QA", data -> {
            BakeryData.findById(data.getUsers(), id).setSecurityQA(question, answer);
            audit(data, "SET_SECURITY_QA", me.getUsername());
            return null;
        });
    }

    /**
     * Mengubah pertanyaan dan jawaban keamanan untuk user tertentu (oleh Owner).
     * Dipakai di UsersController saat membuat atau mengedit akun user.
     */
    public void setSecurityQAForUser(long userId, String question, String answer)
            throws BusinessException, PersistenceException {
        session.require(Permission.MANAGE_USERS);
        store.execute("SET_SECURITY_QA", data -> {
            User user = BakeryData.require(data.getUsers(), userId, "User");
            user.setSecurityQA(question, answer);
            audit(data, "SET_SECURITY_QA", user.getUsername() + " (by owner)");
            return null;
        });
    }

    /**
     * Mengembalikan pertanyaan keamanan untuk username tertentu tanpa perlu sesi.
     * Hanya mengembalikan pertanyaannya (bukan jawaban) sehingga aman ditampilkan.
     * Mengembalikan string kosong jika tidak ada.
     */
    public String getSecurityQuestion(String username) {
        String cleanName = TextUtil.normalize(username).toLowerCase(java.util.Locale.ROOT);
        User user = AuthService.findByUsername(data(), cleanName);
        if (user == null || !user.isActive()) {
            return "";
        }
        return user.getSecurityQuestion();
    }

    private static int countActiveOwners(BakeryData data) {
        int count = 0;
        for (User user : data.getUsers()) {
            if (user.isActive() && user.getRole() == Role.OWNER) {
                count++;
            }
        }
        return count;
    }
}
