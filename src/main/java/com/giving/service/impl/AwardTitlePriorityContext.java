package com.giving.service.impl;

final class AwardTitlePriorityContext {
    private static final ThreadLocal<Runnable> AFTER_BATCH_CALLBACK = new ThreadLocal<>();

    private AwardTitlePriorityContext() {
    }

    static void setAfterBatchCallback(Runnable callback) {
        AFTER_BATCH_CALLBACK.set(callback);
    }

    static Runnable removeAfterBatchCallback() {
        Runnable callback = AFTER_BATCH_CALLBACK.get();
        AFTER_BATCH_CALLBACK.remove();
        return callback;
    }

    static void restoreAfterBatchCallback(Runnable callback) {
        if (callback == null) {
            AFTER_BATCH_CALLBACK.remove();
        } else {
            AFTER_BATCH_CALLBACK.set(callback);
        }
    }

    static void clearAfterBatchCallback() {
        AFTER_BATCH_CALLBACK.remove();
    }

    static void runAfterBatchCallback() {
        Runnable callback = AFTER_BATCH_CALLBACK.get();
        if (callback != null) {
            callback.run();
        }
    }
}
