package com.berttowne.inlineheads.injection;

public interface Service {

    /**
     * Called when the service is loaded, but before it is enabled.
     */
    default void onLoad() { }

    /**
     * Called when the service is enabled, but after it is loaded.
     */
    default void onEnable() { }

    /**
     * Called when the service is disabled.
     */
    default void onDisable() { }

}