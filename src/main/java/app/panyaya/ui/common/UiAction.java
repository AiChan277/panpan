package app.panyaya.ui.common;

/** A piece of UI work that may throw any exception (used by forms and buttons). */
@FunctionalInterface
public interface UiAction {

    void run() throws Exception;
}
