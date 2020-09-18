package com.topwords.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.LongAdder;

@Service
@ParametersAreNonnullByDefault
public class NotifierService {
    private static final Logger LOGGER = LoggerFactory.getLogger(NotifierService.class);

    private final ConcurrentMap<Object, LongAdder> pending;

    public NotifierService() {
        this.pending = new ConcurrentHashMap<>();
    }

    public void prepareForRoot(Object root) {
        pending.putIfAbsent(root, new LongAdder());
    }

    public void increment(Object root) {
        pending.get(root).increment();
    }

    public void decrementPending(Object root) {
        pending.get(root).decrement();
        if (pending.get(root).longValue() == 0L) {
            synchronized (root) {
                root.notify();
            }
        }
    }

    public void waitCrawlFinish(Object root) {
        synchronized (root) {
            try {
                root.wait();
            } catch (InterruptedException e) {
                LOGGER.error("join was interrupted. Return not finished map");
            }
        }
    }

    public void cleanForRoot(Object obj) {
        pending.remove(obj);
    }
}