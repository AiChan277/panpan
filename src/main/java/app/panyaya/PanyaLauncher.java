package app.panyaya;

import javafx.application.Application;

/**
 * Program entry point. A plain class (not extending Application) is required when JavaFX runs
 * from the class path, as it does in the packaged application.
 *
 * <p>{@code --self-test} runs a quick check without a window (used by the packaging script).</p>
 */
public final class PanyaLauncher {

    private PanyaLauncher() {
    }

    public static void main(String[] args) {
        for (String arg : args) {
            if ("--self-test".equals(arg)) {
                System.exit(SelfTest.run());
            }
        }
        // day/month/year in date pickers (as used in Indonesia), English texts
        java.util.Locale.setDefault(java.util.Locale.UK);
        Application.launch(PanyaApp.class, args);
    }
}
