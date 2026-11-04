package app.panyaya.ui.common;

import app.panyaya.service.ProgressListener;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import javafx.scene.Scene;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * Runs slow work (loading, backups, reports, CSV files, password hashing) on a background thread so
 * the window never freezes.
 *
 * <p>Course topic <b>Multithreading</b>: a JavaFX {@link Task} runs {@code call()} on a worker thread
 * from an {@link ExecutorService}. When it finishes, JavaFX calls {@code onSucceeded}/{@code onFailed}
 * back on the JavaFX Application Thread, which is the only thread allowed to touch the UI.</p>
 */
public class TaskRunner {

    /** Work that reports progress (backup, restore). */
    @FunctionalInterface
    public interface ProgressWork<T> {
        T run(ProgressListener progress) throws Exception;
    }

    private final ExecutorService executor;
    private Consumer<Boolean> busyIndicator = busy -> {
    };

    public TaskRunner(ExecutorService executor) {
        this.executor = executor;
    }

    public void setBusyIndicator(Consumer<Boolean> indicator) {
        this.busyIndicator = indicator;
    }

    /** Runs {@code work} in the background; {@code onSuccess} and errors are handled on the UI thread. */
    public <T> void run(Window owner, Callable<T> work, Consumer<T> onSuccess) {
        run(work, onSuccess, error -> Dialogs.error(owner, error));
    }

    public <T> void run(Callable<T> work, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        Task<T> task = new Task<T>() {
            @Override
            protected T call() throws Exception {
                return work.call(); // background thread: no UI access here
            }
        };
        task.setOnSucceeded(event -> {
            busyIndicator.accept(false);
            onSuccess.accept(task.getValue());
        });
        task.setOnFailed(event -> {
            busyIndicator.accept(false);
            onError.accept(task.getException());
        });
        busyIndicator.accept(true);
        executor.submit(task);
    }

    /**
     * Runs work with a modal progress window (the rest of the window is blocked meanwhile, which is
     * required for restore).
     */
    public <T> void runWithProgress(Window owner, String title, ProgressWork<T> work, Consumer<T> onSuccess) {
        Stage progressStage = new Stage(StageStyle.UTILITY);
        progressStage.initOwner(owner);
        progressStage.initModality(Modality.WINDOW_MODAL);
        progressStage.setTitle(title);
        progressStage.setOnCloseRequest(javafx.event.Event::consume); // cannot be closed while running
        Label message = new Label("Starting...");
        ProgressBar bar = new ProgressBar(0);
        bar.setPrefWidth(360);
        VBox box = new VBox(12, message, bar);
        box.setPadding(new Insets(20));
        progressStage.setScene(new Scene(box));

        Task<T> task = new Task<T>() {
            @Override
            protected T call() throws Exception {
                // updateProgress/updateMessage are safe to call from the worker thread
                return work.run((fraction, text) -> {
                    updateProgress(fraction, 1.0);
                    updateMessage(text);
                });
            }
        };
        bar.progressProperty().bind(task.progressProperty());
        message.textProperty().bind(task.messageProperty());
        task.setOnSucceeded(event -> {
            progressStage.close();
            onSuccess.accept(task.getValue());
        });
        task.setOnFailed(event -> {
            progressStage.close();
            Dialogs.error(owner, task.getException());
        });
        progressStage.show();
        executor.submit(task);
    }

    /** Runs something on the UI thread, now if we are already on it. */
    public static void onUiThread(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }
}
