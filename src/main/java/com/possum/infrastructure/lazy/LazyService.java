package com.possum.infrastructure.lazy;

import java.util.function.Supplier;

public class LazyService<T> {
    
    private volatile T instance;
    private final Supplier<T> initializer;
    
    public LazyService(Supplier<T> initializer) {
        this.initializer = initializer;
    }
    
    public T get() {
        if (instance == null) {
            synchronized (this) {
                if (instance == null) {
                    instance = initializer.get();
                }
            }
        }
        return instance;
    }
    
    public boolean isInitialized() {
        return instance != null;
    }
}
