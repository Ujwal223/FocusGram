package com.google.android.play.core.tasks;
public abstract class Task<TResult> {
    public abstract boolean isComplete();
    public abstract boolean isSuccessful();
    public abstract TResult getResult();
    public abstract Exception getException();
    public abstract Task<TResult> addOnSuccessListener(OnSuccessListener<? super TResult> listener);
    public abstract Task<TResult> addOnFailureListener(OnFailureListener listener);
}
