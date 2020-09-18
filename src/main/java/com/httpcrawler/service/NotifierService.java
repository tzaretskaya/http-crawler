package com.httpcrawler.service;

import com.httpcrawler.data.Root;
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

    private final ConcurrentMap<Root, LongAdder> pending;
    private final ConcurrentMap<Root, Object> locks;

    public NotifierService() {
        this.pending = new ConcurrentHashMap<>();
        this.locks = new ConcurrentHashMap<>();
    }

    public void prepareForRoot(Root root) {
        pending.putIfAbsent(root, new LongAdder());
        locks.putIfAbsent(root, new Object());
    }

    public void increment(Root root) {
        pending.get(root).increment();
    }

    public void decrementPending(Root root) {
        pending.get(root).decrement();
        if (pending.get(root).longValue() == 0L) {
            Object lock = locks.get(root);
            synchronized (lock) {
                lock.notifyAll();
            }
        }
    }

    public void waitCrawlFinish(Root root) {
        Object lock = locks.get(root);
        synchronized (lock) {
            try {
                lock.wait();
            } catch (InterruptedException e) {
                LOGGER.error("Join was interrupted. Result not finished");
            }
        }
    }

    public void cleanForRoot(Root obj) {
        locks.remove(obj);
        pending.remove(obj);
    }
}