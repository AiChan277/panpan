package app.panyaya.service;

/**
 * Receives progress of a long operation (backup, restore, export). The UI implements it with a
 * JavaFX Task's {@code updateProgress}/{@code updateMessage}; tests pass {@link #NONE}.
 */
@FunctionalInterface
public interface ProgressListener {

    ProgressListener NONE = (fraction, message) -> {
    };

    void update(double fraction, String message);
}
