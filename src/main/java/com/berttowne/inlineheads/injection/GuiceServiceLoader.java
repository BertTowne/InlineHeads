package com.berttowne.inlineheads.injection;

import com.google.inject.Injector;
import org.jetbrains.annotations.Contract;

import javax.annotation.Nonnull;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.*;

/**
 * Copy of {@linkplain ServiceLoader} that supports Guice injected services.
 *
 * @see ServiceLoader
 */
@SuppressWarnings({"removal", "unused"})
public class GuiceServiceLoader<S> implements Iterable<S> {

    private static final String PREFIX = "META-INF/services/";

    private static Injector globalInjector;

    private final Injector injector;

    /**
     * The class or interface representing the service being loaded
     */
    private final Class<S> service;

    /**
     * The class loader used to locate, load, and instantiate providers
     */
    private final ClassLoader loader;

    /**
     * The access control context taken when the ServiceLoader is created
     */
    private final AccessControlContext acc;

    /**
     * Cached providers, in instantiation order
     */
    private final LinkedHashMap<String, S> providers = new LinkedHashMap<>();

    /**
     * The current lazy-lookup iterator
     */
    private LazyIterator lookupIterator;

    /**
     * Clear this loader's provider cache so that all providers will be
     * reloaded.
     *
     * <p> After invoking this method, subsequent invocations of the {@link
     * #iterator() iterator} method will lazily look up and instantiate
     * providers from scratch, just as is done by a newly-created loader.
     *
     * <p> This method is intended for use in situations in which new providers
     * can be installed into a running Java virtual machine.
     */
    public void reload() {
        providers.clear();
        lookupIterator = new LazyIterator(service, loader);
    }

    private GuiceServiceLoader(Class<S> svc, Injector injector, ClassLoader cl) {
        this.injector = Objects.requireNonNull(injector, "Injector cannot be null.");
        service = Objects.requireNonNull(svc, "Service interface cannot be null");
        loader = (cl == null) ? ClassLoader.getSystemClassLoader() : cl;
        acc = (System.getSecurityManager() != null) ? AccessController.getContext() : null;
        reload();
    }

    private static void fail(@Nonnull Class<?> service, String msg, Throwable cause) throws ServiceConfigurationError {
        throw new ServiceConfigurationError(service.getName() + ": " + msg, cause);
    }

    private static void fail(@Nonnull Class<?> service, String msg) throws ServiceConfigurationError {
        throw new ServiceConfigurationError(service.getName() + ": " + msg);
    }

    private static void fail(Class<?> service, URL u, int line, String msg) throws ServiceConfigurationError {
        fail(service, u + ":" + line + ": " + msg);
    }

    /**
     * Parse a single line from the given configuration file, adding the name
     * on the line to the names list.
     */
    private int parseLine(Class<?> service, URL u, @Nonnull BufferedReader r, int lc, List<String> names) throws IOException, ServiceConfigurationError {
        String ln = r.readLine();
        if (ln == null) {
            return -1;
        }
        int ci = ln.indexOf('#');
        if (ci >= 0) ln = ln.substring(0, ci);
        ln = ln.trim();
        int n = ln.length();
        if (n != 0) {
            if ((ln.indexOf(' ') >= 0) || (ln.indexOf('\t') >= 0))
                fail(service, u, lc, "Illegal configuration-file syntax");
            int cp = ln.codePointAt(0);
            if (!Character.isJavaIdentifierStart(cp))
                fail(service, u, lc, "Illegal provider-class name: " + ln);
            for (int i = Character.charCount(cp); i < n; i += Character.charCount(cp)) {
                cp = ln.codePointAt(i);
                if (!Character.isJavaIdentifierPart(cp) && (cp != '.'))
                    fail(service, u, lc, "Illegal provider-class name: " + ln);
            }
            if (!providers.containsKey(ln) && !names.contains(ln))
                names.add(ln);
        }
        return lc + 1;
    }

    /**
     * Parse the content of the given URL as a provider-configuration file.
     *
     * @param service The service type for which providers are being sought;
     *                used to construct error detail strings
     * @param u       The URL naming the configuration file to be parsed
     * @return A (possibly empty) iterator that will yield the provider-class
     * names in the given configuration file that are not yet members
     * of the returned set
     * @throws ServiceConfigurationError If an I/O error occurs while reading from the given URL, or
     *                                   if a configuration-file format error is detected
     */
    @Nonnull
    @SuppressWarnings("StatementWithEmptyBody")
    private Iterator<String> parse(Class<?> service, @Nonnull URL u) throws ServiceConfigurationError {
        InputStream in = null;
        BufferedReader r = null;
        ArrayList<String> names = new ArrayList<>();
        try {
            in = u.openStream();
            r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            int lc = 1;
            while ((lc = parseLine(service, u, r, lc, names)) >= 0) ;
        } catch (IOException x) {
            fail(service, "Error reading configuration file", x);
        } finally {
            try {
                if (r != null) r.close();
                if (in != null) in.close();
            } catch (IOException y) {
                fail(service, "Error closing configuration file", y);
            }
        }
        return names.iterator();
    }

    /**
     * Private inner class implementing fully-lazy provider lookup
     */
    private class LazyIterator implements Iterator<S> {

        Class<S> service;
        ClassLoader loader;
        Enumeration<URL> configs = null;
        Iterator<String> pending = null;
        String nextName = null;

        private LazyIterator(Class<S> service, ClassLoader loader) {
            this.service = service;
            this.loader = loader;
        }

        private boolean hasNextService() {
            if (nextName != null) {
                return true;
            }
            if (configs == null) {
                try {
                    String fullName = PREFIX + service.getName();
                    if (loader == null)
                        configs = ClassLoader.getSystemResources(fullName);
                    else
                        configs = loader.getResources(fullName);
                } catch (IOException x) {
                    fail(service, "Error locating configuration files", x);
                }
            }
            while ((pending == null) || !pending.hasNext()) {
                if (!configs.hasMoreElements()) {
                    return false;
                }
                pending = parse(service, configs.nextElement());
            }
            nextName = pending.next();
            return true;
        }

        private S nextService() {
            if (!hasNextService())
                throw new NoSuchElementException();
            String cn = nextName;
            nextName = null;
            Class<?> c = null;
            try {
                c = Class.forName(cn, false, loader);
            } catch (ClassNotFoundException x) {
                fail(service, "Provider " + cn + " not found");
            }
            if (!service.isAssignableFrom(c)) {
                fail(service, "Provider " + cn + " not a subtype");
            }
            try {
                S p = service.cast(injector.getInstance(c));
                providers.put(cn, p);
                return p;
            } catch (Throwable x) {
                fail(service, "Provider " + cn + " could not be instantiated", x);
            }
            throw new Error();          // This cannot happen
        }

        public boolean hasNext() {
            if (acc == null) {
                return hasNextService();
            } else {
                return AccessController.doPrivileged((PrivilegedAction<Boolean>) this::hasNextService, acc);
            }
        }

        public S next() {
            if (acc == null) {
                return nextService();
            } else {
                return AccessController.doPrivileged((PrivilegedAction<S>) this::nextService, acc);
            }
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }

    }

    /**
     * @see ServiceLoader#iterator()
     */
    @Nonnull
    public Iterator<S> iterator() {
        return new Iterator<>() {

            final Iterator<Map.Entry<String, S>> knownProviders = providers.entrySet().iterator();

            public boolean hasNext() {
                if (knownProviders.hasNext())
                    return true;
                return lookupIterator.hasNext();
            }

            public S next() {
                if (knownProviders.hasNext())
                    return knownProviders.next().getValue();
                return lookupIterator.next();
            }

            public void remove() {
                throw new UnsupportedOperationException();
            }

        };
    }

    /**
     * Creates a new service loader for the given service type and class
     * loader.
     *
     * @param <S>      the class of the service type
     * @param service  The interface or abstract class representing the service
     * @param injector The injector used to create instances
     * @param loader   The class loader to be used to load provider-configuration files
     *                 and provider classes, or <tt>null</tt> if the system class
     *                 loader (or, failing that, the bootstrap class loader) is to be
     *                 used
     * @return A new service loader
     */
    @Nonnull
    @Contract("_, _, _ -> new")
    public static <S> GuiceServiceLoader<S> load(Class<S> service, Injector injector, ClassLoader loader) {
        return new GuiceServiceLoader<>(service, injector, loader);
    }

    /**
     * Creates a new service loader for the given service type and class
     * loader.
     *
     * @param <S>      the class of the service type
     * @param service  The interface or abstract class representing the service
     * @param injector The injector used to create instances
     * @return A new service loader
     */
    @Nonnull
    public static <S> GuiceServiceLoader<S> load(Class<S> service, Injector injector) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        return GuiceServiceLoader.load(service, injector, cl);
    }

    /**
     * Creates a new service loader for the given service type and class
     * loader.
     *
     * @param <S>     the class of the service type
     * @param service The interface or abstract class representing the service
     * @param loader  The class loader to be used to load provider-configuration files
     *                and provider classes, or <tt>null</tt> if the system class
     *                loader (or, failing that, the bootstrap class loader) is to be
     *                used
     * @return A new service loader
     */
    @Nonnull
    @Contract("_, _ -> new")
    public static <S> GuiceServiceLoader<S> load(Class<S> service, ClassLoader loader) {
        return GuiceServiceLoader.load(service, Objects.requireNonNull(globalInjector, "You must pass an injector or set the global injector!"), loader);
    }

    /**
     * Creates a new service loader for the given service type, using the
     * global injector and the current thread's {@linkplain Thread#getContextClassLoader
     * context class loader}.
     *
     * <p> An invocation of this convenience method of the form
     *
     * <blockquote><pre>
     * ServiceLoader.load(<i>service</i>)</pre></blockquote>
     * <p>
     * is equivalent to
     *
     * <blockquote><pre>
     * ServiceLoader.load(<i>service</i>, GuiceServiceLoader.getGlobalInjector(),
     *                    Thread.currentThread().getContextClassLoader())</pre></blockquote>
     *
     * @param <S>     the class of the service type
     * @param service The interface or abstract class representing the service
     * @return A new service loader
     */
    @Nonnull
    public static <S> GuiceServiceLoader<S> load(Class<S> service) {
        return GuiceServiceLoader.load(service, Objects.requireNonNull(globalInjector, "You must pass an injector or set the global injector!"));
    }

    /**
     * Creates a new service loader for the given service type, using the
     * extension class loader.
     *
     * <p> This convenience method simply locates the extension class loader,
     * call it <tt><i>extClassLoader</i></tt>, and then returns
     *
     * <blockquote><pre>
     * ServiceLoader.load(<i>service</i>, Guice.getGlobalInjector(), <i>extClassLoader</i>)</pre></blockquote>
     *
     * <p> If the extension class loader cannot be found then the system class
     * loader is used; if there is no system class loader then the bootstrap
     * class loader is used.
     *
     * <p> This method is intended for use when only installed providers are
     * desired.  The resulting service will only find and load providers that
     * have been installed into the current Java virtual machine; providers on
     * the application's class path will be ignored.
     *
     * @param <S>     the class of the service type
     * @param service The interface or abstract class representing the service
     * @return A new service loader
     */
    @Nonnull
    public static <S> GuiceServiceLoader<S> loadInstalled(Class<S> service) {
        ClassLoader cl = ClassLoader.getSystemClassLoader();
        ClassLoader prev = null;
        while (cl != null) {
            prev = cl;
            cl = cl.getParent();
        }
        return GuiceServiceLoader.load(service, globalInjector, prev);
    }

    /**
     * @return The current global injector
     */
    public static Injector getGlobalInjector() {
        return globalInjector;
    }

    /**
     * Sets the injector used when no injector is specified in the load methods.
     * <p>
     * Useful to set an app-level injector for others to use.
     *
     * @param globalInjector The default inject to use
     */
    public static void setGlobalInjector(Injector globalInjector) {
        GuiceServiceLoader.globalInjector = globalInjector;
    }

    /**
     * Returns a string describing this service.
     *
     * @return A descriptive string
     */
    public String toString() {
        return getClass().getCanonicalName() + "[" + service.getName() + "]";
    }

}