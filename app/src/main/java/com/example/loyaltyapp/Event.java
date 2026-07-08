package com.example.loyaltyapp;

import androidx.annotation.Nullable;

/**
 * Wraps a value for use as a one-time LiveData signal. {@link
 * #getContentIfNotHandled()} returns the value exactly once; later observers
 * (e.g. after a configuration change re-subscribes) get {@code null}, so the
 * event does not re-fire. Replaces manual reset-to-null of MutableLiveData.
 */
public final class Event<T> {
    private final T content;
    private boolean handled;

    public Event(T content) {
        this.content = content;
    }

    @Nullable
    public T getContentIfNotHandled() {
        if (handled) return null;
        handled = true;
        return content;
    }
}
