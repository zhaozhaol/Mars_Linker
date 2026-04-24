package com.mars.linker.broker.netty;

import java.util.Map;

/**
 * Session 持久化存储抽象。
 */
interface SessionStore {

    /**
     * 加载全部 session（实现需自行做版本/header 校验）。
     */
    Map<String, SessionService.Session> loadAll();

    /**
     * 持久化全部 session（实现需保证尽量原子替换）。
     */
    void persistAll(Map<String, SessionService.Session> sessions);

    /**
     * 删除持久化文件（仅测试辅助）。
     */
    void deleteIfExists();
}
