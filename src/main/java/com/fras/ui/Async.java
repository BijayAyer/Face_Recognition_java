package com.fras.ui;

import com.fras.service.ApiService;
import javafx.application.Platform;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * One place for "do this off the JavaFX thread, then come back".
 *
 * <p>Every screen used to spawn a bare {@code new Thread(...)} and catch only
 * {@link ApiService.ApiException}. Anything else - a Jackson parse failure on
 * an unexpected payload, a null in a row mapper - escaped, killed the thread
 * without a word, and left the button disabled and the spinner turning
 * forever. Here the whole body is guarded, the failure is turned into a
 * sentence, and the callbacks always run on the FX thread exactly once.
 */
public final class Async {

    private static final ExecutorService POOL =
            Executors.newFixedThreadPool(4, daemonFactory());

    private Async() {
    }

    private static ThreadFactory daemonFactory() {
        AtomicInteger counter = new AtomicInteger(1);
        return runnable -> {
            Thread thread = new Thread(runnable, "fras-ui-" + counter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };
    }

    /** Work that produces a value. */
    public static <T> void run(Callable<T> work,
                               Consumer<T> onSuccess,
                               Consumer<String> onFailure) {
        POOL.submit(() -> {
            try {
                T value = work.call();
                Platform.runLater(() -> onSuccess.accept(value));
            } catch (Throwable failure) {
                String message = describe(failure);
                Platform.runLater(() -> onFailure.accept(message));
            }
        });
    }

    /** Work that only has an effect. */
    public static void run(Job work, Runnable onSuccess, Consumer<String> onFailure) {
        run(() -> {
            work.run();
            return Boolean.TRUE;
        }, ignored -> onSuccess.run(), onFailure);
    }

    /**
     * Turns anything thrown into something worth showing a person. The
     * server's own wording is preferred when there is one, because
     * GlobalExceptionHandler already writes messages for people.
     */
    public static String describe(Throwable failure) {
        if (failure instanceof ApiService.ApiException api) {
            return api.getMessage();
        }
        if (failure instanceof InterruptedException) {
            Thread.currentThread().interrupt();
            return "That was cancelled before it finished.";
        }
        if (failure instanceof java.io.IOException) {
            String message = failure.getMessage();
            return message == null || message.isBlank()
                    ? "The server's reply could not be read."
                    : message;
        }
        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            return "Something went wrong: " + failure.getClass().getSimpleName();
        }
        return message;
    }

    /** A body that may throw anything, unlike {@link Runnable}. */
    @FunctionalInterface
    public interface Job {
        void run() throws Exception;
    }
}
