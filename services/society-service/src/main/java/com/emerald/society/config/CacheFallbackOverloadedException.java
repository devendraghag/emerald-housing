package com.emerald.society.config;

/** Signals that the bounded database fallback capacity is full while Redis is bypassed. */
public class CacheFallbackOverloadedException extends RuntimeException {
    public CacheFallbackOverloadedException() {
        super("Society is limiting database reads while the cache is unavailable");
    }
}
