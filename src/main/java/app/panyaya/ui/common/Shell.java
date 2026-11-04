package app.panyaya.ui.common;

import javafx.stage.Window;

/** What a screen may ask of the main window. Implemented by the MainController. */
public interface Shell {

    TaskRunner tasks();

    Window window();

    /** Switches to another screen, e.g. from a dashboard alert to Ingredients. */
    void showView(String viewId);

    /** Short message in the status bar. */
    void setStatus(String message);

    /** Returns to the sign-in screen (after a restore the user list may have changed). */
    void signOutWithoutAsking();
}
