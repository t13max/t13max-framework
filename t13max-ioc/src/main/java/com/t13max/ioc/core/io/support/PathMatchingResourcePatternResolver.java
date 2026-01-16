package com.t13max.ioc.core.io.support;

import com.t13max.ioc.core.io.Resource;
import com.t13max.ioc.core.io.ResourceLoader;
import com.t13max.ioc.core.io.UrlResource;
import com.t13max.ioc.utils.Assert;
import com.t13max.ioc.utils.ResourceUtils;
import com.t13max.ioc.utils.StringUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReader;
import java.lang.module.ResolvedModule;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipException;

/**
 * @Author: t13max
 * @Since: 21:45 2026/1/15
 */
public class PathMatchingResourcePatternResolver implements ResourcePatternResolver{

    private static final Resource[] EMPTY_RESOURCE_ARRAY = {};

    private static final Logger logger = LogManager.getLogger(PathMatchingResourcePatternResolver.class);    
    private static final Set<String> systemModuleNames = NativeDetector.inNativeImage() ? Collections.emptySet() :
            ModuleFinder.ofSystem().findAll().stream()
                    .map(moduleReference -> moduleReference.descriptor().name())
                    .collect(Collectors.toSet());    
    private static final Predicate<ResolvedModule> isNotSystemModule =
            resolvedModule -> !systemModuleNames.contains(resolvedModule.name());

    private static  Method equinoxResolveMethod;

    static {
        try {
            // Detect Equinox OSGi (for example, on WebSphere 6.1)
            Class<?> fileLocatorClass = ClassUtils.forName("org.eclipse.core.runtime.FileLocator",
                    PathMatchingResourcePatternResolver.class.getClassLoader());
            equinoxResolveMethod = fileLocatorClass.getMethod("resolve", URL.class);
            logger.trace("Found Equinox FileLocator for OSGi bundle URL resolution");
        }
        catch (Throwable ex) {
            equinoxResolveMethod = null;
        }
    }


    private final ResourceLoader resourceLoader;

    private PathMatcher pathMatcher = new AntPathMatcher();

    private boolean useCaches = true;

    private final Map<String, Resource[]> rootDirCache = new ConcurrentHashMap<>();

    private final Map<String, NavigableSet<String>> jarEntriesCache = new ConcurrentHashMap<>();

    private volatile  Set<ClassPathManifestEntry> manifestEntriesCache;
    
    public PathMatchingResourcePatternResolver() {
        this.resourceLoader = new DefaultResourceLoader();
    }    
    public PathMatchingResourcePatternResolver(ResourceLoader resourceLoader) {
        Assert.notNull(resourceLoader, "ResourceLoader must not be null");
        this.resourceLoader = resourceLoader;
    }    
    public PathMatchingResourcePatternResolver( ClassLoader classLoader) {
        this.resourceLoader = new DefaultResourceLoader(classLoader);
    }
    
    public ResourceLoader getResourceLoader() {
        return this.resourceLoader;
    }

    @Override
    public  ClassLoader getClassLoader() {
        return getResourceLoader().getClassLoader();
    }    
    public void setPathMatcher(PathMatcher pathMatcher) {
        Assert.notNull(pathMatcher, "PathMatcher must not be null");
        this.pathMatcher = pathMatcher;
    }    
    public PathMatcher getPathMatcher() {
        return this.pathMatcher;
    }    
    public void setUseCaches(boolean useCaches) {
        this.useCaches = useCaches;
    }


    @Override
    public Resource getResource(String location) {
        return getResourceLoader().getResource(location);
    }

    @Override
    public Resource[] getResources(String locationPattern) throws IOException {
        Assert.notNull(locationPattern, "Location pattern must not be null");
        if (locationPattern.startsWith(CLASSPATH_ALL_URL_PREFIX)) {
            // a class path resource (multiple resources for same name possible)
            String locationPatternWithoutPrefix = locationPattern.substring(CLASSPATH_ALL_URL_PREFIX.length());
            // Search the module path first.
            Set<Resource> resources = findAllModulePathResources(locationPatternWithoutPrefix);
            // Search the class path next.
            if (getPathMatcher().isPattern(locationPatternWithoutPrefix)) {
                // a class path resource pattern
                Collections.addAll(resources, findPathMatchingResources(locationPattern));
            }
            else {
                // all class path resources with the given name
                Collections.addAll(resources, findAllClassPathResources(locationPatternWithoutPrefix));
            }
            return resources.toArray(EMPTY_RESOURCE_ARRAY);
        }
        else {
            // Generally only look for a pattern after a prefix here,
            // and on Tomcat only after the "*/" separator for its "war:" protocol.
            int prefixEnd = (locationPattern.startsWith("war:") ? locationPattern.indexOf("*/") + 1 :
                    locationPattern.indexOf(':') + 1);
            if (getPathMatcher().isPattern(locationPattern.substring(prefixEnd))) {
                // a file pattern
                return findPathMatchingResources(locationPattern);
            }
            else {
                // a single resource with the given name
                return new Resource[] {getResourceLoader().getResource(locationPattern)};
            }
        }
    }    
    public void clearCache() {
        this.rootDirCache.clear();
        this.jarEntriesCache.clear();
        this.manifestEntriesCache = null;
    }
    
    protected Resource[] findAllClassPathResources(String location) throws IOException {
        String path = stripLeadingSlash(location);
        Set<Resource> result = doFindAllClassPathResources(path);
        if (logger.isTraceEnabled()) {
            logger.trace("Resolved class path location [" + path + "] to resources " + result);
        }
        return result.toArray(EMPTY_RESOURCE_ARRAY);
    }    
    protected Set<Resource> doFindAllClassPathResources(String path) throws IOException {
        Set<Resource> result = new LinkedHashSet<>(16);
        ClassLoader cl = getClassLoader();
        Enumeration<URL> resourceUrls = (cl != null ? cl.getResources(path) : ClassLoader.getSystemResources(path));
        while (resourceUrls.hasMoreElements()) {
            URL url = resourceUrls.nextElement();
            result.add(convertClassLoaderURL(url));
        }
        if (!StringUtils.hasLength(path)) {
            // The above result is likely to be incomplete, i.e. only containing file system references.
            // We need to have pointers to each of the jar files on the class path as well...
            addAllClassLoaderJarRoots(cl, result);
        }
        return result;
    }    
    @SuppressWarnings("deprecation")  // on JDK 20 (deprecated URL constructor)
    protected Resource convertClassLoaderURL(URL url) {
        if (ResourceUtils.URL_PROTOCOL_FILE.equals(url.getProtocol())) {
            try {
                // URI decoding for special characters such as spaces.
                return new FileSystemResource(ResourceUtils.toURI(url).getSchemeSpecificPart());
            }
            catch (URISyntaxException ex) {
                // Fallback for URLs that are not valid URIs (should hardly ever happen).
                return new FileSystemResource(url.getFile());
            }
        }
        else {
            String urlString = url.toString();
            String cleanedPath = StringUtils.cleanPath(urlString);
            if (!cleanedPath.equals(urlString)) {
                // Prefer cleaned URL, aligned with UrlResource#createRelative(String)
                try {
                    // Retain original URL instance, potentially including custom URLStreamHandler.
                    return new UrlResource(new URL(url, cleanedPath));
                }
                catch (MalformedURLException ex) {
                    // Fallback to regular URL construction below...
                }
            }
            // Retain original URL instance, potentially including custom URLStreamHandler.
            return new UrlResource(url);
        }
    }    
    protected void addAllClassLoaderJarRoots( ClassLoader classLoader, Set<Resource> result) {
        if (classLoader instanceof URLClassLoader urlClassLoader) {
            try {
                for (URL url : urlClassLoader.getURLs()) {
                    try {
                        UrlResource jarResource = (ResourceUtils.URL_PROTOCOL_JAR.equals(url.getProtocol()) ?
                                new UrlResource(url) :
                                new UrlResource(ResourceUtils.JAR_URL_PREFIX + url + ResourceUtils.JAR_URL_SEPARATOR));
                        if (jarResource.exists()) {
                            result.add(jarResource);
                        }
                    }
                    catch (MalformedURLException ex) {
                        if (logger.isDebugEnabled()) {
                            logger.debug("Cannot search for matching files underneath [" + url +
                                    "] because it cannot be converted to a valid 'jar:' URL: " + ex.getMessage());
                        }
                    }
                }
            }
            catch (Exception ex) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Cannot introspect jar files since ClassLoader [" + classLoader +
                            "] does not support 'getURLs()': " + ex);
                }
            }
        }

        if (classLoader == ClassLoader.getSystemClassLoader()) {
            // JAR "Class-Path" manifest header evaluation...
            addClassPathManifestEntries(result);
        }

        if (classLoader != null) {
            try {
                // Hierarchy traversal...
                addAllClassLoaderJarRoots(classLoader.getParent(), result);
            }
            catch (Exception ex) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Cannot introspect jar files in parent ClassLoader since [" + classLoader +
                            "] does not support 'getParent()': " + ex);
                }
            }
        }
    }    
    protected void addClassPathManifestEntries(Set<Resource> result) {
        Set<ClassPathManifestEntry> entries = this.manifestEntriesCache;
        if (entries == null) {
            entries = getClassPathManifestEntries();
            if (this.useCaches) {
                this.manifestEntriesCache = entries;
            }
        }
        for (ClassPathManifestEntry entry : entries) {
            if (!result.contains(entry.resource()) &&
                    (entry.alternative() != null && !result.contains(entry.alternative()))) {
                result.add(entry.resource());
            }
        }
    }

    private Set<ClassPathManifestEntry> getClassPathManifestEntries() {
        Set<ClassPathManifestEntry> manifestEntries = new LinkedHashSet<>();
        Set<File> seen = new HashSet<>();
        try {
            String paths = System.getProperty("java.class.path");
            for (String path : StringUtils.delimitedListToStringArray(paths, File.pathSeparator)) {
                try {
                    File jar = new File(path).getAbsoluteFile();
                    if (jar.isFile() && seen.add(jar)) {
                        manifestEntries.add(ClassPathManifestEntry.of(jar));
                        manifestEntries.addAll(getClassPathManifestEntriesFromJar(jar));
                    }
                }
                catch (MalformedURLException ex) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Cannot search for matching files underneath [" + path +
                                "] because it cannot be converted to a valid 'jar:' URL: " + ex.getMessage());
                    }
                }
            }
            return Collections.unmodifiableSet(manifestEntries);
        }
        catch (Exception ex) {
            if (logger.isDebugEnabled()) {
                logger.debug("Failed to evaluate 'java.class.path' manifest entries: " + ex);
            }
            return Collections.emptySet();
        }
    }

    private Set<ClassPathManifestEntry> getClassPathManifestEntriesFromJar(File jar) throws IOException {
        URL base = jar.toURI().toURL();
        File parent = jar.getAbsoluteFile().getParentFile();
        try (JarFile jarFile = new JarFile(jar)) {
            Manifest manifest = jarFile.getManifest();
            Attributes attributes = (manifest != null ? manifest.getMainAttributes() : null);
            String classPath = (attributes != null ? attributes.getValue(Attributes.Name.CLASS_PATH) : null);
            Set<ClassPathManifestEntry> manifestEntries = new LinkedHashSet<>();
            if (StringUtils.hasLength(classPath)) {
                StringTokenizer tokenizer = new StringTokenizer(classPath);
                while (tokenizer.hasMoreTokens()) {
                    String path = tokenizer.nextToken();
                    if (path.indexOf(':') >= 0 && !"file".equalsIgnoreCase(new URL(base, path).getProtocol())) {
                        // See jdk.internal.loader.URLClassPath.JarLoader.tryResolveFile(URL, String)
                        continue;
                    }
                    File candidate = new File(parent, path);
                    if (candidate.isFile() && candidate.getCanonicalPath().contains(parent.getCanonicalPath())) {
                        manifestEntries.add(ClassPathManifestEntry.of(candidate));
                    }
                }
            }
            return Collections.unmodifiableSet(manifestEntries);
        }
        catch (Exception ex) {
            if (logger.isDebugEnabled()) {
                logger.debug("Failed to load manifest entries from jar file '" + jar + "': " + ex);
            }
            return Collections.emptySet();
        }
    }    
    protected Resource[] findPathMatchingResources(String locationPattern) throws IOException {
        String rootDirPath = determineRootDir(locationPattern);
        String subPattern = locationPattern.substring(rootDirPath.length());

        // Look for pre-cached root dir resources, either a direct match or
        // a match for a parent directory in the same classpath locations.
        Resource[] rootDirResources = this.rootDirCache.get(rootDirPath);
        String actualRootPath = null;
        if (rootDirResources == null) {
            // No direct match -> search for a common parent directory match
            // (cached based on repeated searches in the same base location,
            // in particular for different root directories in the same jar).
            String commonPrefix = null;
            String existingPath = null;
            boolean commonUnique = true;
            for (String path : this.rootDirCache.keySet()) {
                String currentPrefix = null;
                for (int i = 0; i < path.length(); i++) {
                    if (i == rootDirPath.length() || path.charAt(i) != rootDirPath.charAt(i)) {
                        currentPrefix = path.substring(0, path.lastIndexOf('/', i - 1) + 1);
                        break;
                    }
                }
                if (currentPrefix != null) {
                    if (checkPathWithinPackage(path.substring(currentPrefix.length()))) {
                        // A prefix match found, potentially to be turned into a common parent cache entry.
                        if (commonPrefix == null || !commonUnique || currentPrefix.length() > commonPrefix.length()) {
                            commonPrefix = currentPrefix;
                            existingPath = path;
                        }
                        else if (currentPrefix.equals(commonPrefix)) {
                            commonUnique = false;
                        }
                    }
                }
                else if (actualRootPath == null || path.length() > actualRootPath.length()) {
                    // A direct match found for a parent directory -> use it.
                    rootDirResources = this.rootDirCache.get(path);
                    actualRootPath = path;
                }
            }
            if (rootDirResources == null && StringUtils.hasLength(commonPrefix)) {
                // Try common parent directory as long as it points to the same classpath locations.
                rootDirResources = getResources(commonPrefix);
                Resource[] existingResources = this.rootDirCache.get(existingPath);
                if (existingResources != null && rootDirResources.length == existingResources.length) {
                    // Replace existing subdirectory cache entry with common parent directory,
                    // avoiding repeated determination of root directories in the same jar.
                    this.rootDirCache.remove(existingPath);
                    this.rootDirCache.put(commonPrefix, rootDirResources);
                    actualRootPath = commonPrefix;
                }
                else if (commonPrefix.equals(rootDirPath)) {
                    // The identified common directory is equal to the currently requested path ->
                    // worth caching specifically, even if it cannot replace the existing sub-entry.
                    this.rootDirCache.put(rootDirPath, rootDirResources);
                }
                else {
                    // Mismatch: parent directory points to more classpath locations.
                    rootDirResources = null;
                }
            }
            if (rootDirResources == null) {
                // Lookup for specific directory, creating a cache entry for it.
                rootDirResources = getResources(rootDirPath);
                if (this.useCaches) {
                    this.rootDirCache.put(rootDirPath, rootDirResources);
                }
            }
        }

        Set<Resource> result = new LinkedHashSet<>(64);
        for (Resource rootDirResource : rootDirResources) {
            if (actualRootPath != null && actualRootPath.length() < rootDirPath.length()) {
                // Create sub-resource for requested sub-location from cached common root directory.
                rootDirResource = rootDirResource.createRelative(rootDirPath.substring(actualRootPath.length()));
            }
            rootDirResource = resolveRootDirResource(rootDirResource);
            URL rootDirUrl = rootDirResource.getURL();
            if (equinoxResolveMethod != null && rootDirUrl.getProtocol().startsWith("bundle")) {
                URL resolvedUrl = (URL) ReflectionUtils.invokeMethod(equinoxResolveMethod, null, rootDirUrl);
                if (resolvedUrl != null) {
                    rootDirUrl = resolvedUrl;
                }
                rootDirResource = new UrlResource(rootDirUrl);
            }
            if (rootDirUrl.getProtocol().startsWith(ResourceUtils.URL_PROTOCOL_VFS)) {
                result.addAll(VfsResourceMatchingDelegate.findMatchingResources(rootDirUrl, subPattern, getPathMatcher()));
            }
            else if (ResourceUtils.isJarURL(rootDirUrl) || isJarResource(rootDirResource)) {
                result.addAll(doFindPathMatchingJarResources(rootDirResource, rootDirUrl, subPattern));
            }
            else {
                result.addAll(doFindPathMatchingFileResources(rootDirResource, subPattern));
            }
        }
        if (logger.isTraceEnabled()) {
            logger.trace("Resolved location pattern [" + locationPattern + "] to resources " + result);
        }
        return result.toArray(EMPTY_RESOURCE_ARRAY);
    }    
    protected String determineRootDir(String location) {
        int prefixEnd = location.indexOf(':') + 1;
        int rootDirEnd = location.length();
        while (rootDirEnd > prefixEnd && getPathMatcher().isPattern(location.substring(prefixEnd, rootDirEnd))) {
            rootDirEnd = location.lastIndexOf('/', rootDirEnd - 2) + 1;
        }
        if (rootDirEnd == 0) {
            rootDirEnd = prefixEnd;
        }
        return location.substring(0, rootDirEnd);
    }    
    protected Resource resolveRootDirResource(Resource original) throws IOException {
        return original;
    }    
    protected boolean isJarResource(Resource resource) throws IOException {
        return false;
    }    
    protected Set<Resource> doFindPathMatchingJarResources(Resource rootDirResource, URL rootDirUrl, String subPattern)
            throws IOException {

        String jarFileUrl = null;
        String rootEntryPath = "";

        String urlFile = rootDirUrl.getFile();
        int separatorIndex = urlFile.indexOf(ResourceUtils.WAR_URL_SEPARATOR);
        if (separatorIndex == -1) {
            separatorIndex = urlFile.indexOf(ResourceUtils.JAR_URL_SEPARATOR);
        }
        if (separatorIndex >= 0) {
            jarFileUrl = urlFile.substring(0, separatorIndex);
            rootEntryPath = urlFile.substring(separatorIndex + 2);  // both separators are 2 chars
            NavigableSet<String> entriesCache = this.jarEntriesCache.get(jarFileUrl);
            if (entriesCache != null) {
                Set<Resource> result = new LinkedHashSet<>(64);
                // Clean root entry path to match jar entries format without "!" separators
                rootEntryPath = rootEntryPath.replace(ResourceUtils.JAR_URL_SEPARATOR, "/");
                // Search sorted entries from first entry with rootEntryPath prefix
                boolean rootEntryPathFound = false;
                for (String entryPath : entriesCache.tailSet(rootEntryPath, false)) {
                    if (!entryPath.startsWith(rootEntryPath)) {
                        // We are beyond the potential matches in the current TreeSet.
                        break;
                    }
                    rootEntryPathFound = true;
                    String relativePath = entryPath.substring(rootEntryPath.length());
                    if (getPathMatcher().match(subPattern, relativePath)) {
                        result.add(rootDirResource.createRelative(relativePath));
                    }
                }
                if (rootEntryPathFound) {
                    return result;
                }
            }
        }

        URLConnection con = rootDirUrl.openConnection();
        JarFile jarFile;
        boolean closeJarFile;

        if (con instanceof JarURLConnection jarCon) {
            // Should usually be the case for traditional JAR files.
            if (!this.useCaches) {
                jarCon.setUseCaches(false);
            }
            try {
                jarFile = jarCon.getJarFile();
                jarFileUrl = jarCon.getJarFileURL().toExternalForm();
                JarEntry jarEntry = jarCon.getJarEntry();
                rootEntryPath = (jarEntry != null ? jarEntry.getName() : "");
                closeJarFile = !jarCon.getUseCaches();
            }
            catch (ZipException | FileNotFoundException | NoSuchFileException ex) {
                // Happens in case of a non-jar file or in case of a cached root directory
                // without the specific subdirectory present, respectively.
                return Collections.emptySet();
            }
        }
        else {
            // No JarURLConnection -> need to resort to URL file parsing.
            // We'll assume URLs of the format "jar:path!/entry", with the protocol
            // being arbitrary as long as following the entry format.
            // We'll also handle paths with and without leading "file:" prefix.
            try {
                if (jarFileUrl != null) {
                    jarFile = getJarFile(jarFileUrl);
                }
                else {
                    jarFile = new JarFile(urlFile);
                    jarFileUrl = urlFile;
                    rootEntryPath = "";
                }
                closeJarFile = true;
            }
            catch (ZipException ex) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Skipping invalid jar class path entry [" + urlFile + "]");
                }
                return Collections.emptySet();
            }
        }

        try {
            if (logger.isTraceEnabled()) {
                logger.trace("Looking for matching resources in jar file [" + jarFileUrl + "]");
            }
            if (StringUtils.hasLength(rootEntryPath) && !rootEntryPath.endsWith("/")) {
                // Root entry path must end with slash to allow for proper matching.
                // The Sun JRE does not return a slash here, but BEA JRockit does.
                rootEntryPath = rootEntryPath + "/";
            }
            Set<Resource> result = new LinkedHashSet<>(64);
            NavigableSet<String> entriesCache = new TreeSet<>();
            Iterator<String> entryIterator = jarFile.stream().map(JarEntry::getName).sorted().iterator();
            while (entryIterator.hasNext()) {
                String entryPath = entryIterator.next();
                int entrySeparatorIndex = entryPath.indexOf(ResourceUtils.JAR_URL_SEPARATOR);
                if (entrySeparatorIndex >= 0) {
                    entryPath = entryPath.substring(entrySeparatorIndex + ResourceUtils.JAR_URL_SEPARATOR.length());
                }
                entriesCache.add(entryPath);
                if (entryPath.startsWith(rootEntryPath)) {
                    String relativePath = entryPath.substring(rootEntryPath.length());
                    if (getPathMatcher().match(subPattern, relativePath)) {
                        result.add(rootDirResource.createRelative(relativePath));
                    }
                }
            }
            if (this.useCaches) {
                // Cache jar entries in TreeSet for efficient searching on re-encounter.
                this.jarEntriesCache.put(jarFileUrl, entriesCache);
            }
            return result;
        }
        finally {
            if (closeJarFile) {
                jarFile.close();
            }
        }
    }    
    protected JarFile getJarFile(String jarFileUrl) throws IOException {
        if (jarFileUrl.startsWith(ResourceUtils.FILE_URL_PREFIX)) {
            try {
                return new JarFile(ResourceUtils.toURI(jarFileUrl).getSchemeSpecificPart());
            }
            catch (URISyntaxException ex) {
                // Fallback for URLs that are not valid URIs (should hardly ever happen).
                return new JarFile(jarFileUrl.substring(ResourceUtils.FILE_URL_PREFIX.length()));
            }
        }
        else {
            return new JarFile(jarFileUrl);
        }
    }    
    protected Set<Resource> doFindPathMatchingFileResources(Resource rootDirResource, String subPattern)
            throws IOException {

        Set<Resource> result = new LinkedHashSet<>(64);
        URI rootDirUri;
        try {
            rootDirUri = rootDirResource.getURI();
        }
        catch (Exception ex) {
            if (logger.isWarnEnabled()) {
                logger.warn("Failed to resolve directory [%s] as URI: %s".formatted(rootDirResource, ex));
            }
            return result;
        }

        Path rootPath = null;
        if (rootDirUri.isAbsolute() && !rootDirUri.isOpaque()) {
            // Prefer Path resolution from URI if possible
            try {
                try {
                    rootPath = Path.of(rootDirUri);
                }
                catch (FileSystemNotFoundException ex) {
                    // If the file system was not found, assume it's a custom file system that needs to be installed.
                    FileSystems.newFileSystem(rootDirUri, Map.of(), ClassUtils.getDefaultClassLoader());
                    rootPath = Path.of(rootDirUri);
                }
            }
            catch (Exception ex) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Failed to resolve %s in file system: %s".formatted(rootDirUri, ex));
                }
                // Fallback via Resource.getFile() below
            }
        }

        if (rootPath == null) {
            // Resource.getFile() resolution as a fallback -
            // for custom URI formats and custom Resource implementations
            try {
                rootPath = Path.of(rootDirResource.getFile().getAbsolutePath());
            }
            catch (FileNotFoundException ex) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Cannot search for matching files underneath " + rootDirResource +
                            " in the file system: " + ex.getMessage());
                }
                return result;
            }
            catch (Exception ex) {
                if (logger.isInfoEnabled()) {
                    logger.info("Failed to resolve " + rootDirResource + " in the file system: " + ex);
                }
                return result;
            }
        }

        if (!Files.exists(rootPath)) {
            if (logger.isDebugEnabled()) {
                logger.debug("Skipping search for files matching pattern [%s]: directory [%s] does not exist"
                        .formatted(subPattern, rootPath.toAbsolutePath()));
            }
            return result;
        }

        String rootDir = StringUtils.cleanPath(rootPath.toString());
        if (!rootDir.endsWith("/")) {
            rootDir += "/";
        }

        Path rootPathForPattern = rootPath;
        String resourcePattern = rootDir + StringUtils.cleanPath(subPattern);
        Predicate<Path> isMatchingFile = path -> (!path.equals(rootPathForPattern) &&
                getPathMatcher().match(resourcePattern, StringUtils.cleanPath(path.toString())));

        if (logger.isTraceEnabled()) {
            logger.trace("Searching directory [%s] for files matching pattern [%s]"
                    .formatted(rootPath.toAbsolutePath(), subPattern));
        }

        try (Stream<Path> files = Files.walk(rootPath, FileVisitOption.FOLLOW_LINKS)) {
            files.filter(isMatchingFile).sorted().map(FileSystemResource::new).forEach(result::add);
        }
        catch (Exception ex) {
            if (logger.isWarnEnabled()) {
                logger.warn("Failed to search in directory [%s] for files matching pattern [%s]: %s"
                        .formatted(rootPath.toAbsolutePath(), subPattern, ex));
            }
        }
        return result;
    }    
    protected Set<Resource> findAllModulePathResources(String locationPattern) throws IOException {
        Set<Resource> result = new LinkedHashSet<>(64);

        // Skip scanning the module path when running in a native image.
        if (NativeDetector.inNativeImage()) {
            return result;
        }

        String resourcePattern = stripLeadingSlash(locationPattern);
        Predicate<String> resourcePatternMatches = (getPathMatcher().isPattern(resourcePattern) ?
                path -> getPathMatcher().match(resourcePattern, path) :
                resourcePattern::equals);

        try {
            ModuleLayer.boot().configuration().modules().stream()
                    .filter(isNotSystemModule)
                    .forEach(resolvedModule -> {
                        // NOTE: a ModuleReader and a Stream returned from ModuleReader.list() must be closed.
                        try (ModuleReader moduleReader = resolvedModule.reference().open();
                             Stream<String> names = moduleReader.list()) {
                            names.filter(resourcePatternMatches)
                                    .map(name -> findResource(moduleReader, name))
                                    .filter(Objects::nonNull)
                                    .forEach(result::add);
                        }
                        catch (IOException ex) {
                            if (logger.isDebugEnabled()) {
                                logger.debug("Failed to read contents of module [%s]".formatted(resolvedModule), ex);
                            }
                            throw new UncheckedIOException(ex);
                        }
                    });
        }
        catch (UncheckedIOException ex) {
            // Unwrap IOException to conform to this method's contract.
            throw ex.getCause();
        }

        if (logger.isTraceEnabled()) {
            logger.trace("Resolved module-path location pattern [%s] to resources %s".formatted(resourcePattern, result));
        }
        return result;
    }

    private  Resource findResource(ModuleReader moduleReader, String name) {
        try {
            return moduleReader.find(name)
                    .map(this::convertModuleSystemURI)
                    .orElse(null);
        }
        catch (Exception ex) {
            if (logger.isDebugEnabled()) {
                logger.debug("Failed to find resource [%s] in module path".formatted(name), ex);
            }
            return null;
        }
    }    
    private Resource convertModuleSystemURI(URI uri) {
        return (ResourceUtils.URL_PROTOCOL_FILE.equals(uri.getScheme()) ?
                new FileSystemResource(uri.getPath()) : UrlResource.from(uri));
    }

    private static String stripLeadingSlash(String path) {
        return (path.startsWith("/") ? path.substring(1) : path);
    }

    private static boolean checkPathWithinPackage(String path) {
        return (path.contains("/") && !path.contains(ResourceUtils.JAR_URL_SEPARATOR));
    }
    
    private static class VfsResourceMatchingDelegate {

        public static Set<Resource> findMatchingResources(
                URL rootDirUrl, String locationPattern, PathMatcher pathMatcher) throws IOException {

            Object root = VfsPatternUtils.findRoot(rootDirUrl);
            PatternVirtualFileVisitor visitor =
                    new PatternVirtualFileVisitor(VfsPatternUtils.getPath(root), locationPattern, pathMatcher);
            VfsPatternUtils.visit(root, visitor);
            return visitor.getResources();
        }
    }
    
    @SuppressWarnings("unused")
    private static class PatternVirtualFileVisitor implements InvocationHandler {

        private final String subPattern;

        private final PathMatcher pathMatcher;

        private final String rootPath;

        private final Set<Resource> resources = new LinkedHashSet<>(64);

        public PatternVirtualFileVisitor(String rootPath, String subPattern, PathMatcher pathMatcher) {
            this.subPattern = subPattern;
            this.pathMatcher = pathMatcher;
            this.rootPath = (rootPath.isEmpty() || rootPath.endsWith("/") ? rootPath : rootPath + "/");
        }

        @Override
        public  Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String methodName = method.getName();
            if (Object.class == method.getDeclaringClass()) {
                switch (methodName) {
                    case "equals" -> {
                        // Only consider equal when proxies are identical.
                        return (proxy == args[0]);
                    }
                    case "hashCode" -> {
                        return System.identityHashCode(proxy);
                    }
                }
            }
            return switch (methodName) {
                case "getAttributes" -> getAttributes();
                case "visit" -> {
                    visit(args[0]);
                    yield null;
                }
                case "toString" -> toString();
                default -> throw new IllegalStateException("Unexpected method invocation: " + method);
            };
        }

        public void visit(Object vfsResource) {
            if (this.pathMatcher.match(this.subPattern,
                    VfsPatternUtils.getPath(vfsResource).substring(this.rootPath.length()))) {
                this.resources.add(new VfsResource(vfsResource));
            }
        }

        public  Object getAttributes() {
            return VfsPatternUtils.getVisitorAttributes();
        }

        public Set<Resource> getResources() {
            return this.resources;
        }

        public int size() {
            return this.resources.size();
        }

        @Override
        public String toString() {
            return "sub-pattern: " + this.subPattern + ", resources: " + this.resources;
        }
    }
    
    private record ClassPathManifestEntry(Resource resource,  Resource alternative) {

        private static final String JARFILE_URL_PREFIX = ResourceUtils.JAR_URL_PREFIX + ResourceUtils.FILE_URL_PREFIX;

        static ClassPathManifestEntry of(File file) throws MalformedURLException {
            String path = fixPath(file.getAbsolutePath());
            Resource resource = asJarFileResource(path);
            Resource alternative = createAlternative(path);
            return new ClassPathManifestEntry(resource, alternative);
        }

        private static String fixPath(String path) {
            int prefixIndex = path.indexOf(':');
            if (prefixIndex == 1) {
                // Possibly a drive prefix on Windows (for example, "c:"), so we prepend a slash
                // and convert the drive letter to uppercase for consistent duplicate detection.
                path = "/" + StringUtils.capitalize(path);
            }
            // Since '#' can appear in directories/filenames, java.net.URL should not treat it as a fragment
            return StringUtils.replace(path, "#", "%23");
        }

        
        private static  Resource createAlternative(String path) {
            try {
                String alternativePath = path.startsWith("/") ? path.substring(1) : "/" + path;
                return asJarFileResource(alternativePath);
            }
            catch (MalformedURLException ex) {
                return null;
            }
        }

        private static Resource asJarFileResource(String path) throws MalformedURLException {
            return new UrlResource(JARFILE_URL_PREFIX + path + ResourceUtils.JAR_URL_SEPARATOR);
        }
    }
}
