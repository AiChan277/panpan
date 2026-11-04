package app.panyaya.service;

import app.panyaya.exception.BusinessException;
import app.panyaya.exception.PersistenceException;
import app.panyaya.exception.ValidationException;
import app.panyaya.model.BakeryData;
import app.panyaya.model.product.ProductCategory;
import app.panyaya.model.supplier.Supplier;
import app.panyaya.model.user.Role;
import app.panyaya.model.user.User;
import app.panyaya.persistence.DataStore;
import app.panyaya.persistence.EntityTypes;
import app.panyaya.util.TextUtil;

import java.time.Clock;
import java.time.Duration;
import java.util.Locale;

/** First-run setup, sign-in, sign-out and changing one's own password. */
public class AuthService extends BaseService {

    /** Same message for "unknown user" and "wrong password", so attackers learn nothing. */
    public static final String GENERIC_LOGIN_ERROR = "Wrong username or password.";

    public static final String[] DEFAULT_CATEGORIES = {"Bread", "Pastry", "Cake", "Snack", "Drinks"};
    public static final String MARKET_SUPPLIER = "Market / Cash (Pasar / Tunai)";

    private final PasswordHasher hasher;

    public AuthService(DataStore store, Session session, Clock clock, PasswordHasher hasher) {
        super(store, session, clock);
        this.hasher = hasher;
    }

    /** True when no data file exists yet: the setup wizard must run. */
    public boolean isSetupRequired() {
        return !store.isLoaded();
    }

    /**
     * Creates the data file with the bakery profile, the Owner account, default categories and the
     * generic "Market / Cash" supplier. No default username or password ships with the app.
     */
    public User setupNewBakery(String bakeryName, String username, String displayName, char[] password)
            throws BusinessException, PersistenceException {
        if (store.isLoaded()) {
            throw new ValidationException("The bakery has already been set up.");
        }
        validateNewPassword(username, password);

        BakeryData fresh = new BakeryData();
        fresh.getSettings().updateProfile(bakeryName, "", "", fresh.getSettings().getReceiptFooter());
        for (String name : DEFAULT_CATEGORIES) {
            fresh.getCategories().add(new ProductCategory(fresh.nextId(EntityTypes.CATEGORY), name));
        }
        fresh.getSuppliers().add(new Supplier(fresh.nextId(EntityTypes.SUPPLIER), MARKET_SUPPLIER, "", "", "", "",
                "Use for purchases at the market without a regular supplier."));
        User owner = new User(fresh.nextId(EntityTypes.USER), username, displayName, Role.OWNER,
                hasher.hash(password), now());
        fresh.getUsers().add(owner);
        PasswordHasher.wipe(password);
        audit(fresh, "SETUP", "Bakery \"" + fresh.getSettings().getBakeryName() + "\" created, owner "
                + owner.getUsername());

        store.initialize(fresh);
        session.signIn(owner);
        return owner;
    }

    /** Signs in. Five wrong passwords lock the account for five minutes. */
    public User login(String username, char[] password) throws BusinessException, PersistenceException {
        String cleanName = TextUtil.normalize(username).toLowerCase(Locale.ROOT);
        User user = findByUsername(data(), cleanName);
        if (user == null || !user.isActive()) {
            PasswordHasher.wipe(password);
            throw new ValidationException(GENERIC_LOGIN_ERROR);
        }
        if (user.isLockedAt(now())) {
            long minutes = Math.max(1, Duration.between(now(), user.getLockedUntil()).toMinutes() + 1);
            throw new ValidationException("Too many failed attempts. Try again in " + minutes + " minute(s).");
        }
        boolean correct = hasher.verify(password, user.getPasswordHash());
        PasswordHasher.wipe(password);
        final long id = user.getId();
        if (!correct) {
            store.execute("LOGIN_FAILED", data -> {
                BakeryData.findById(data.getUsers(), id).recordFailedLogin(now());
                return null;
            });
            throw new ValidationException(GENERIC_LOGIN_ERROR);
        }
        session.signIn(user);
        try {
            return store.execute("LOGIN", data -> {
                User signedIn = BakeryData.findById(data.getUsers(), id);
                signedIn.recordSuccessfulLogin();
                audit(data, "LOGIN", signedIn.getUsername());
                return signedIn;
            });
        } catch (BusinessException | PersistenceException e) {
            session.signOut();
            throw e;
        }
    }

    public void logout() {
        session.signOut();
    }

    public void changeOwnPassword(char[] currentPassword, char[] newPassword)
            throws BusinessException, PersistenceException {
        User user = session.getUser();
        if (user == null) {
            throw new ValidationException("Please sign in first.");
        }
        if (!hasher.verify(currentPassword, user.getPasswordHash())) {
            throw new ValidationException("The current password is wrong.");
        }
        validateNewPassword(user.getUsername(), newPassword);
        String newHash = hasher.hash(newPassword);
        PasswordHasher.wipe(currentPassword);
        PasswordHasher.wipe(newPassword);
        final long id = user.getId();
        store.execute("CHANGE_PASSWORD", data -> {
            BakeryData.findById(data.getUsers(), id).changePasswordHash(newHash, false);
            audit(data, "CHANGE_PASSWORD", "own password changed");
            return null;
        });
    }

    static void validateNewPassword(String username, char[] password) throws ValidationException {
        if (password == null || password.length < User.MIN_PASSWORD_LENGTH) {
            throw new ValidationException("Password must have at least " + User.MIN_PASSWORD_LENGTH + " characters.");
        }
        if (new String(password).equalsIgnoreCase(TextUtil.normalize(username))) {
            throw new ValidationException("Password must be different from the username.");
        }
    }

    static User findByUsername(BakeryData data, String username) {
        for (User user : data.getUsers()) {
            if (user.getUsername().equals(username)) {
                return user;
            }
        }
        return null;
    }

    PasswordHasher getHasher() {
        return hasher;
    }
}
