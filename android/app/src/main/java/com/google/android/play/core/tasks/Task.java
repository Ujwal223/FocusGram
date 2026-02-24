package com.google.android.play.core.tasks;

public abstract class Task<TResult> {
    public abstract boolean isSuccessful();
    public abstract TResult getResult();
}
