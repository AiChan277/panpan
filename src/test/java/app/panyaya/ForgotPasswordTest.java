package app.panyaya;

import app.panyaya.exception.ValidationException;
import app.panyaya.model.user.Role;
import app.panyaya.model.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ForgotPasswordTest {

    @TempDir
    Path dir;

    @Test
    void ownerCanSetSecurityQAAndResetForgottenPassword() throws Exception {
        TestBakery bakery = new TestBakery(dir);
        bakery.ctx.users().setSecurityQA("Nama hewan peliharaan pertama?", "Mochi");
        bakery.ctx.auth().logout();

        assertEquals("Nama hewan peliharaan pertama?", bakery.ctx.users().getSecurityQuestion("owner"));
        assertNotNull(bakery.ctx.auth().verifySecurityAnswer("owner", "  mochi  "));

        bakery.ctx.auth().resetPasswordWithSecurityAnswer("owner", "MOCHI", "new-secret-123".toCharArray());

        assertThrows(ValidationException.class,
                () -> bakery.ctx.auth().login("owner", TestBakery.OWNER_PASSWORD.toCharArray()));

        User signedIn = bakery.ctx.auth().login("owner", "new-secret-123".toCharArray());
        assertEquals("owner", signedIn.getUsername());
    }

    @Test
    void ownerCanConfigureSecurityQAForStaffAndResetValidatesAnswerAndPassword() throws Exception {
        TestBakery bakery = new TestBakery(dir);
        User sari = bakery.ctx.users().createUser("sari", "Sari", Role.CASHIER, "temp-pass-1".toCharArray());
        bakery.ctx.users().setSecurityQAForUser(sari.getId(), "Kota kelahiran?", "Solo");
        bakery.ctx.auth().logout();

        assertThrows(ValidationException.class,
                () -> bakery.ctx.auth().verifySecurityAnswer("sari", "Bandung"));

        assertThrows(ValidationException.class,
                () -> bakery.ctx.auth().resetPasswordWithSecurityAnswer("sari", "Solo", "short".toCharArray()));

        bakery.ctx.auth().resetPasswordWithSecurityAnswer("sari", "solo", "sari-new-pass".toCharArray());
        User loggedIn = bakery.ctx.auth().login("sari", "sari-new-pass".toCharArray());
        assertEquals("sari", loggedIn.getUsername());
    }

    @Test
    void userWithoutSecurityQACannotResetFromLoginScreen() throws Exception {
        TestBakery bakery = new TestBakery(dir);
        bakery.ctx.users().createUser("budi", "Budi", Role.BAKER, "temp-pass-1".toCharArray());
        bakery.ctx.auth().logout();

        assertEquals("", bakery.ctx.users().getSecurityQuestion("budi"));
        assertThrows(ValidationException.class,
                () -> bakery.ctx.auth().verifySecurityAnswer("budi", "anything"));
    }
}
