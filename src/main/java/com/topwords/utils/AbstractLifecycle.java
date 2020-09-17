package com.topwords.utils;

import com.google.common.base.Throwables;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
public abstract class AbstractLifecycle implements SmartLifecycle {
    private volatile boolean running;

    public AbstractLifecycle() {
    }

    public boolean isAutoStartup() {
        return true;
    }

    public void start() {
        if (!this.running) {
            try {
                this.onStart();
                this.running = true;
            } catch (Throwable var2) {
                Throwables.throwIfUnchecked(var2);
                throw new RuntimeException(var2);
            }
        }
    }

    public void stop() {
    }

    public void stop(Runnable callback) {
        if (this.running) {
            try {
                this.onStop();
            } catch (Throwable var11) {
                LoggerFactory.getLogger(this.getClass()).error("", var11);
            } finally {
                this.running = false;

                try {
                    callback.run();
                } catch (Throwable var10) {
                    LoggerFactory.getLogger(this.getClass()).error("", var10);
                }
            }
        }
    }

    public boolean isRunning() {
        return this.running;
    }

    public int getPhase() {
        return 0;
    }

    public abstract void onStart() throws Exception;

    public abstract void onStop() throws Exception;
}
