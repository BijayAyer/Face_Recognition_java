package com.fras.ui;

import javafx.scene.Node;
import javafx.scene.control.ScrollPane;

/**
 * One screen in the shell.
 *
 * <p>Screens used to be methods on {@code MainApplication} that each built a
 * whole new {@link javafx.scene.Scene} and handed it to the stage. That is why
 * navigating anywhere reset the window size, dropped keyboard focus, and forced
 * the camera to be stopped from eleven different places - there was no object
 * whose job it was to know that a screen had gone away. A page is that object:
 * it is built once, kept, and told when it is shown and hidden.
 */
public abstract class Page {

    private Node view;

    /** Label in the navigation rail. */
    public abstract String title();

    /** Builds the screen. Called once, the first time it is shown. */
    protected abstract Node build();

    public final Node view() {
        if (view == null) {
            view = build();
        }
        return view;
    }

    /** True once {@link #build()} has run - nothing to reset before that. */
    public final boolean isBuilt() {
        return view != null;
    }

    /**
     * Called every time the page comes to the front. Refresh here rather than
     * in {@link #build()}, so a page that is returned to shows current data.
     */
    public void onShow() {
    }

    /**
     * Called when the page leaves. Release anything that must not keep running
     * in the background - a camera, a timer, a poll.
     */
    public void onHide() {
    }

    /** For pages whose content is taller than the window on a small screen. */
    protected static ScrollPane scroll(Node body) {
        ScrollPane scroller = new ScrollPane(body);
        scroller.setFitToWidth(true);
        scroller.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroller.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroller.setFocusTraversable(false);
        return scroller;
    }
}
