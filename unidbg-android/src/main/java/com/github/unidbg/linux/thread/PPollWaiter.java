package com.github.unidbg.linux.thread;

import com.github.unidbg.Emulator;
import com.github.unidbg.file.FileIO;
import com.github.unidbg.file.linux.AndroidFileIO;
import com.github.unidbg.thread.AbstractWaiter;
import com.sun.jna.Pointer;
import unicorn.Arm64Const;
import unicorn.ArmConst;

import java.util.Map;

/**
 * ppoll 系统调用的等待器
 * 用于在 threadDispatcher 模式下正确处理 IO 等待
 */
public class PPollWaiter extends AbstractWaiter {

    private final int nfds;
    private final Pointer fds;
    private final Map<Integer, AndroidFileIO> fdMap;
    private final long endWaitTimeInMillis;

    private static final short POLLIN = 0x0001;
    private static final short POLLOUT = 0x0004;

    public PPollWaiter(Emulator<?> emulator, Pointer fds, int nfds, Pointer tmo_p, Pointer sigmask,
                       Map<Integer, AndroidFileIO> fdMap) {
        this.fds = fds;
        this.nfds = nfds;
        this.fdMap = fdMap;
        
        // 修复：tmo_p 可能为 null，表示无限等待
        if (tmo_p != null) {
            long tv_sec = tmo_p.getLong(0);
            long tv_nsec = tmo_p.getLong(8);
            this.endWaitTimeInMillis = System.currentTimeMillis() + (tv_sec * 1000L + tv_nsec / 1000000L);
        } else {
            this.endWaitTimeInMillis = Long.MAX_VALUE;
        }
    }

    /**
     * 检查是否有 fd 就绪
     */
    private int checkFds() {
        int count = 0;
        for (int i = 0; i < nfds; i++) {
            Pointer pollfd = fds.share(i * 8L);
            int fd = pollfd.getInt(0);
            short events = pollfd.getShort(4);
            if (fd >= 0) {
                short revents = 0;
                FileIO io = fdMap.get(fd);
                if (io != null) {
                    if ((events & POLLOUT) != 0 && io.canWrite()) {
                        revents = POLLOUT;
                    } else if ((events & POLLIN) != 0 && io.canRead()) {
                        revents = POLLIN;
                    }
                }
                if (revents != 0) {
                    pollfd.setShort(6, revents);
                    count++;
                }
            }
        }
        return count;
    }

    @Override
    public boolean canDispatch() {
        int count = checkFds();
        return count > 0 || System.currentTimeMillis() > endWaitTimeInMillis;
    }

    @Override
    public void onContinueRun(Emulator<?> emulator) {
        int count = 0;
        for (int i = 0; i < nfds; i++) {
            Pointer pollfd = fds.share(i * 8L);
            int fd = pollfd.getInt(0);
            short events = pollfd.getShort(4);
            if (fd < 0) {
                pollfd.setShort(6, (short) 0);
            } else {
                short revents = 0;
                FileIO io = fdMap.get(fd);
                if (io != null) {
                    if ((events & POLLOUT) != 0 && io.canWrite()) {
                        revents = POLLOUT;
                    } else if ((events & POLLIN) != 0 && io.canRead()) {
                        revents = POLLIN;
                    }
                }
                if (revents != 0) {
                    pollfd.setShort(6, revents);
                    count++;
                }
            }
        }

        emulator.getBackend().reg_write(
                emulator.is32Bit() ? ArmConst.UC_ARM_REG_R0 : Arm64Const.UC_ARM64_REG_X0,
                count);
    }
}
