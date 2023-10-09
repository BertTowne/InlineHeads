package com.berttowne.inlineheads;

import com.berttowne.inlineheads.injection.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.event.Listener;
import org.bukkit.plugin.SimplePluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;

@SuppressWarnings("removal")
public final class InlineHeadsPlugin extends JavaPlugin implements InjectionRoot {

    @Override
    public void onLoad() {
        AppInjector.registerInjectionRoot(this);
        AppInjector.registerRootModule(new InjectionModule(this));
    }

    @Override
    public void onEnable() {
        AppInjector.boot();

        this.saveDefaultConfig();

        // Register commands - may not work with custom PluginManager
        if (getServer().getPluginManager() instanceof SimplePluginManager) {
            try {
                Field commandMapField = getServer().getPluginManager().getClass().getDeclaredField("commandMap");
                commandMapField.setAccessible(true);

                Object commandMapObject = commandMapField.get(getServer().getPluginManager());

                if (commandMapObject instanceof CommandMap commandMap) {
                    GuiceServiceLoader.load(Command.class, getClassLoader()).forEach(command -> commandMap.register("gui", command));
                }
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }

        // Boot AutoServices
        GuiceServiceLoader.load(Service.class, getClassLoader()).forEach(Service::onLoad);
        GuiceServiceLoader.load(Listener.class, getClassLoader()).forEach(listener -> getServer().getPluginManager().registerEvents(listener, this));
        GuiceServiceLoader.load(Service.class, getClassLoader()).forEach(Service::onEnable);
    }

    @Override
    public void onDisable() {
        GuiceServiceLoader.load(Service.class, getClassLoader()).forEach(Service::onDisable);
    }

}