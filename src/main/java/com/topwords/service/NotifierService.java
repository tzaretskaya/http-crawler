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
        LongAdder longAdder = pending.get(root);
        longAdder.increment();
//        System.out.println("pending inc "+ (longAdder.longValue()+1));
//        pending.get(root).increment();
    }

    public void decrementPending(Object root) {
        pending.get(root).decrement();
        long l = pending.get(root).longValue();
//        System.out.println("pending desc "+ l);
        if (l == 0L) {
            System.out.println("--------------------FINISHED!-------------------");
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
