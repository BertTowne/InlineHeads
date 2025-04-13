package com.berttowne.inlineheads;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("UnstableApiUsage")
public class InlineHeadsLoader implements PluginLoader {

    /**
     * This method is called to add dependencies to the classloader, removing the need for shading.
     *
     * @param classpathBuilder The classpath builder to add dependencies to.
     */
    @Override
    public void classloader(@NotNull PluginClasspathBuilder classpathBuilder) {
        MavenLibraryResolver resolver = new MavenLibraryResolver();

        resolver.addRepository(new RemoteRepository.Builder("central", "default", "https://repo1.maven.org/maven2/").build());

        resolver.addDependency(new Dependency(new DefaultArtifact("com.google.auto.service:auto-service:1.1.1"), null));
        resolver.addDependency(new Dependency(new DefaultArtifact("com.google.auto.service:auto-service-annotations:1.1.1"), null));
        resolver.addDependency(new Dependency(new DefaultArtifact("com.google.inject:guice:7.0.0"), null));

        classpathBuilder.addLibrary(resolver);
    }

}