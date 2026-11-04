package app.panyaya.ui.common;

import app.panyaya.AppContext;
import app.panyaya.model.user.Permission;
import javafx.stage.Window;

/**
 * Shared behaviour of screen controllers: access to services, permission checks for buttons and
 * one place that turns exceptions into friendly dialogs.
 */
public abstract class BaseController implements ViewController {

    protected AppContext ctx;
    protected Shell shell;

    @Override
    public final void init(AppContext context, Shell shellWindow) {
        this.ctx = context;
        this.shell = shellWindow;
        setUp();
    }

    /** Called once after the FXML fields are injected and the services are available. */
    protected abstract void setUp();

    protected Window window() {
        return shell.window();
    }

    protected boolean can(Permission permission) {
        return ctx.getSession().can(permission);
    }

    /**
     * Runs a short business action on the UI thread. On success the screen is refreshed; on failure
     * an explanation is shown and the user's input stays where it was.
     */
    protected boolean attempt(UiAction action) {
        try {
            action.run();
            refresh();
            return true;
        } catch (Exception e) {
            Dialogs.error(window(), e);
            return false;
        }
    }
}
