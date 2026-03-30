package com.github.unidbg.arm.backend;

import com.github.unidbg.Emulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

public abstract class BackendFactory {

    private static final Logger log = LoggerFactory.getLogger(BackendFactory.class);

    private final boolean fallbackUnicorn;

    protected BackendFactory(boolean fallbackUnicorn) {
        this.fallbackUnicorn = fallbackUnicorn;
    }

    private Backend newBackend(Emulator<?> emulator, boolean is64Bit) {
        try {
            return newBackendInternal(emulator, is64Bit);
        } catch (Throwable e) {
            log.trace("newBackend failed", e);
            if (fallbackUnicorn) {
                return null;
            } else {
                throw e;
            }
        }
    }

    protected abstract Backend newBackendInternal(Emulator<?> emulator, boolean is64Bit);

    public static Backend createBackend(Emulator<?> emulator, boolean is64Bit, Collection<BackendFactory> backendFactories) {
        log.trace("create backend: is64Bit={}, backendFactories={}", is64Bit, backendFactories);
        if (backendFactories != null) {
            for (BackendFactory factory : backendFactories) {
                Backend backend = factory.newBackend(emulator, is64Bit);
                if (backend != null) {
                    return backend;
                }
            }
        }
        // Unicorn1存在太多问题了，比如不支持armv8.2，这在某些样本中会无限debugger；这里换成Unicorn2就行了；
        log.info("如果出现各种问题，如因为armv8.2指令而无限debugger，请切换到Unicorn2 backend");
        return new UnicornBackend(emulator, is64Bit);
    }

}
