package cn.jens.mybatis.cache;

/** 二级缓存达到容量上限时的淘汰顺序。 */
public enum EvictionPolicy {

    /** 淘汰最久未访问的条目；读取和覆盖写入都会更新访问顺序。 */
    LRU,

    /** 淘汰最早写入的条目；读取和覆盖写入不改变插入顺序。 */
    FIFO
}
