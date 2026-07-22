package me.cortex.voxy.common.util;

/**
 * Java 21 引入了 {@code Integer.compress}/{@code Integer.expand} 位操作 API,
 * 此类在 Java 17 上提供等价的实现,以避免移植时的 API 缺失。
 *
 * 实现参考 JDK 21 源码 (java.lang.Integer):
 *  - compress: 把指定 mask 中所有置 1 的位按顺序"压缩"到结果的低位
 *  - expand:   compress 的逆操作,把 i 的低位"展开"到 mask 中置 1 的位置
 */
public final class IntBitOps {

    private IntBitOps() {}

    /** 等价于 {@code Integer.compress(i, mask)} (JDK 21+) */
    public static int compress(int i, int mask) {
        int r = 0;
        int m = mask;
        // 经典 bit-compress 算法 (与 JDK 21 等价)
        for (int bit = 0; bit < 32; bit++) {
            if ((m & (1 << bit)) != 0) {
                // 把 i 中对应 mask 置 1 位的内容取出来,按顺序填到 r 的低位
                int v = (i & (1 << bit)) != 0 ? 1 : 0;
                r |= (v << Integer.numberOfTrailingZeros(~(r | (r + 1))));
            }
        }
        return r;
    }

    /** 等价于 {@code Integer.expand(i, mask)} (JDK 21+) */
    public static int expand(int i, int mask) {
        int r = 0;
        int m = mask;
        int src = i;
        // 对 mask 中每个置 1 位,从 i 的低位依次取出一位放到该位置
        for (int bit = 0; bit < 32; bit++) {
            if ((m & (1 << bit)) != 0) {
                int v = src & 1;
                src >>>= 1;
                r |= (v << bit);
            }
        }
        return r;
    }
}
