package com.learn.mybatismini.plugin;

import com.learn.mybatismini.exceptions.PersistenceException;

/**
 * 插件机制专属异常（对应 MyBatis 的 {@code PluginException}）。
 */
public class PluginException extends PersistenceException {

    public PluginException(String message) {
        super(message);
    }

    public PluginException(String message, Throwable cause) {
        super(message, cause);
    }
}
