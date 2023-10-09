package com.berttowne.inlineheads.injection;

import com.google.common.collect.Sets;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;

import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Stream;

public class AppInjector {

    private static Injector injector;

    private static final Set<InjectionRoot> injectionRoots = Sets.newHashSet();
    private static final Set<ClassLoader> rootClassLoaders = Sets.newHashSet();
    private static final Set<Module> rootModules = Sets.newHashSet();

    public static void registerInjectionRoot(InjectionRoot injectionRoot) {
        if (injector != null) {
            throw new IllegalStateException("AppInjector already running!");
        }

        injectionRoots.add(injectionRoot);
        rootClassLoaders.add(injectionRoot.getClass().getClassLoader());
    }

    public static void registerRootModule(Module module) {
        if (injector != null) {
            throw new IllegalStateException("AppInjector already running!");
        }

        rootModules.add(module);
    }

    public static void boot() {
        if (injector != null) {
            return;
        }

        rootClassLoaders.forEach(classLoader -> ServiceLoader.load(Module.class, classLoader).forEach(rootModules::add));
        injector = Guice.createInjector(rootModules);
        GuiceServiceLoader.setGlobalInjector(injector);

        injectionRoots.forEach(injectionRoot -> injector.injectMembers(injectionRoot));
    }

    public static <T> Stream<T> getServices(Class<T> serviceType) {
        return rootClassLoaders.stream().map(root -> ServiceLoader.load(serviceType, root)).flatMap(ServiceLoader::stream)
                .map(ServiceLoader.Provider::get).peek(service -> {
                    if (injector != null) {
                        injector.injectMembers(service);
                    }
                });
    }

}