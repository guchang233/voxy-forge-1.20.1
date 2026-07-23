package me.cortex.voxy.common.util;

import me.cortex.voxy.common.Logger;
import org.lwjgl.system.*;

//Platform specific code to assist in thread utilities
//NOTE: Forge 1.20.1 用的 LWJGL 3.3.1 中没有 org.lwjgl.system.windows.Kernel32 类 (3.3.2+ 才引入),
//因此本类直接用 SharedLibrary + JNI 调用 Kernel32 函数,等价行为。
public class ThreadUtils {
    public static final int WIN32_THREAD_PRIORITY_TIME_CRITICAL = 15;
    public static final int WIN32_THREAD_PRIORITY_LOWEST = -2;
    public static final int WIN32_THREAD_MODE_BACKGROUND_BEGIN = 0x00010000;
    public static final int WIN32_THREAD_MODE_BACKGROUND_END = 0x00020000;
    public static final boolean isWindows = Platform.get() == Platform.WINDOWS;
    public static final boolean isLinux = Platform.get() == Platform.LINUX;
    // Win32 GetCurrentThread() 总是返回 pseudo handle -1L (代表当前线程)
    private static final long CURRENT_THREAD_PSEUDO_HANDLE = -1L;
    private static final long SetThreadPriority;
    private static final long SetThreadSelectedCpuSetMasks;
    private static final long schedSetaffinity;
    static {
        if (isWindows) {
            SharedLibrary lib = null;
            try {
                lib = APIUtil.apiCreateLibrary("kernel32");
            } catch (Throwable t) {
                Logger.error("Failed to load kernel32", t);
            }
            SetThreadPriority = lib != null ? lib.getFunctionAddress("SetThreadPriority") : 0L;
            SetThreadSelectedCpuSetMasks = lib != null ? lib.getFunctionAddress("SetThreadSelectedCpuSetMasks") : 0L;
        } else {
            SetThreadPriority = 0;
            SetThreadSelectedCpuSetMasks = 0;
        }

        if (Platform.get() == Platform.LINUX) {
            long fn = 0;
            try {
                var libc = APIUtil.apiCreateLibrary("libc.so.6");
                fn = APIUtil.apiGetFunctionAddress(libc, "sched_setaffinity");
            } catch (Exception e) {
                Logger.error(e);
            }
            schedSetaffinity = fn;
        } else {
            schedSetaffinity = 0;
        }
    }

    public static boolean SetThreadSelectedCpuSetMasksWin32(long mask) {
        return SetThreadSelectedCpuSetMasksWin32(new long[]{mask}, new short[]{0});
    }

    public static boolean SetThreadSelectedCpuSetMasksWin32(long[] masks, short[] groups) {
        if (SetThreadSelectedCpuSetMasks == 0 || !isWindows) {
            return false;
        }

        if (masks == null) {
            // LWJGL 3.3.1 没有 invokePPCI,用 invokePPI(long,long,short,long) 代替
            int retVal = JNI.invokePPI(CURRENT_THREAD_PSEUDO_HANDLE, 0L, (short) 0, SetThreadSelectedCpuSetMasks);
            if (retVal == 0) {
                // 1.20.1 移植:CPU 亲和性设置失败不应中断游戏启动(可能权限不足或函数不可用)
                Logger.warn("SetThreadSelectedCpuSetMasks returned 0 (likely insufficient privilege), continuing without CPU set affinity");
                return false;
            }
            return true;
        }

        if (masks.length != groups.length) {
            throw new IllegalArgumentException();
        }
        try (var stack = MemoryStack.stackPush()) {
            long ptr = stack.ncalloc(16, masks.length, 16);
            MemoryUtil.memSet(ptr, 0, masks.length*16L);
            for (int i = 0; i < masks.length; i++) {
                MemoryUtil.memPutLong(ptr+i*16L, masks[i]);
                MemoryUtil.memPutShort(ptr+i*16L+8L, groups[i]);
            }

            int retVal = JNI.invokePPI(CURRENT_THREAD_PSEUDO_HANDLE, ptr, (short)masks.length, SetThreadSelectedCpuSetMasks);
            if (retVal == 0) {
                Logger.warn("SetThreadSelectedCpuSetMasks returned 0 (likely insufficient privilege), continuing without CPU set affinity");
                return false;
            }
            return true;
        }
    }

    public static boolean SetSelfThreadPriorityWin32(int priority) {
        if (SetThreadPriority == 0 || !isWindows) {
            return false;
        }
        if (JNI.invokePI(CURRENT_THREAD_PSEUDO_HANDLE, priority, SetThreadPriority)==0) {
            // 1.20.1 移植:线程优先级提升失败不应中断游戏启动(需要 SeIncreaseBasePriorityPrivilege)
            Logger.warn("SetThreadPriority returned 0 for priority " + priority + " (likely insufficient privilege), continuing with default priority");
            return false;
        }
        return true;
    }

    public static boolean schedSetaffinityLinux(long masks[]) {
        if (schedSetaffinity == 0 || isWindows) {
            return false;
        }
        try (var stack = MemoryStack.stackPush()) {
            long ptr = stack.ncalloc(8, masks.length, 8);
            for (int i=0; i<masks.length; i++) {
                MemoryUtil.memPutLong(ptr+i*8L, masks[i]);
            }

            int retVal = JNI.invokePPI(0, (long)masks.length*8, ptr, schedSetaffinity);
            if (retVal != 0) {
                Logger.warn("sched_setaffinity returned " + retVal + ", continuing without CPU affinity");
                return false;
            }
            return true;
        }
    }
}
