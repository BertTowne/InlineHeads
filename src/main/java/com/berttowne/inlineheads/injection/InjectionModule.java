package com.berttowne.inlineheads.injection;

import com.berttowne.inlineheads.InlineHeadsPlugin;
import com.google.gson.Gson;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import org.bukkit.Bukkit;
import org.bukkit.Server;

public class InjectionModule extends AbstractModule {

    private final InlineHeadsPlugin plugin;

    public InjectionModule(InlineHeadsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    protected void configure() {
        AppInjector.getServices(Module.class).forEach(this::install);

        bind(InlineHeadsPlugin.class).toInstance(this.plugin);
        bind(Server.class).toInstance(Bukkit.getServer());
        bind(Gson.class).toInstance(new Gson());
    }

}