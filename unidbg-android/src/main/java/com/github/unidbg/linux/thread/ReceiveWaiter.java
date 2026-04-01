package com.github.unidbg.linux.thread;

import com.github.unidbg.Emulator;
import com.github.unidbg.arm.backend.Backend;
import com.github.unidbg.file.FileIO;
import com.github.unidbg.thread.AbstractWaiter;
import com.sun.jna.Pointer;
import unicorn.Arm64Const;
import unicorn.ArmConst;

/**
 * recvfrom 系统调用的等待器
 * 用于在 threadDispatcher 模式下正确处理阻塞的 socket 接收
 */
public class ReceiveWaiter extends AbstractWaiter {

    private final Thread thread;
    private volatile int ret;  // 修复：添加 volatile 保证线程可见性

    public ReceiveWaiter(FileIO file, Backend backend, Pointer buf, int len, int flags,
                         Pointer src_addr, Pointer addrlen) {
        this.thread = new Thread(() -> {
            ret = file.recvfrom(backend, buf, len, flags, src_addr, addrlen);
        }, "ReceiveWaiter");
        this.thread.setDaemon(true);
        this.thread.start();
    }

    @Override
    public void onContinueRun(Emulator<?> emulator) {
        emulator.getBackend().reg_write(
                emulator.is32Bit() ? ArmConst.UC_ARM_REG_R0 : Arm64Const.UC_ARM64_REG_X0,
                ret);
    }

    @Override
    public boolean canDispatch() {
        if (this.thread.getState() == Thread.State.TERMINATED) {
            return true;
        }
        Thread.yield();
        return false;
    }
}
