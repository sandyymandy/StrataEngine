package engine.strata.core.io;

import engine.strata.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/**
 * Utility for scanning directories and JAR files for resources.
 * Used by auto-registration system to discover blocks, items, etc.
 */
public class DirectoryScanner {
    private static final Logger LOGGER = LoggerFactory.getLogger("DirectoryScanner");

    /**
     * Scans for all JSON files in a specific folder across all namespaces.
     *
     * @param folder The folder to scan (e.g., "blocks", "items")
     * @param classLoader The classloader to scan
     * @return Map of Identifier to resource path
     */
    public static Map<Identifier, String> scanForJsonFiles(String folder, ClassLoader classLoader) {
        Map<Identifier, String> found = new HashMap<>();

        try {
            // Get all resources in assets/
            Enumeration<URL> resources = classLoader.getResources("assets");

            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                scanUrl(url, folder, found);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to scan for resources in folder: " + folder, e);
        }

        return found;
    }

    /**
     * Scans a specific namespace for JSON files.
     *
     * @param namespace The namespace to scan (e.g., "strata", "mymod")
     * @param folder The folder to scan (e.g., "blocks", "items")
     * @param classLoader The classloader to scan
     * @return Map of Identifier to resource path
     */
    public static Map<Identifier, String> scanNamespace(String namespace, String folder,
                                                        ClassLoader classLoader) {
        Map<Identifier, String> found = new HashMap<>();

        try {
            String basePath = "assets/" + namespace + "/" + folder;
            Enumeration<URL> resources = classLoader.getResources(basePath);

            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                scanNamespaceUrl(url, namespace, folder, found);
            }
        } catch (IOException e) {
            LOGGER.debug("No resources found for namespace {} in folder {}", namespace, folder);
        }

        return found;
    }

    private static void scanUrl(URL url, String folder, Map<Identifier, String> found) {
        try {
            URI uri = url.toURI();

            if (uri.getScheme().equals("jar")) {
                scanJar(uri, folder, found);
            } else if (uri.getScheme().equals("file")) {
                scanFileSystem(Paths.get(uri), folder, found);
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to scan URL: " + url, e);
        }
    }

    private static void scanNamespaceUrl(URL url, String namespace, String folder,
                                         Map<Identifier, String> found) {
        try {
            URI uri = url.toURI();

            if (uri.getScheme().equals("jar")) {
                scanJarNamespace(uri, namespace, folder, found);
            } else if (uri.getScheme().equals("file")) {
                scanFileSystemNamespace(Paths.get(uri), namespace, found);
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to scan URL: " + url, e);
        }
    }

    private static void scanJar(URI uri, String folder, Map<Identifier, String> found) {
        try {
            // Extract JAR file path
            String[] parts = uri.toString().split("!");
            if (parts.length < 2) return;

            URI jarUri = URI.create(parts[0]);

            try (FileSystem fs = FileSystems.newFileSystem(jarUri, Collections.emptyMap())) {
                Path assetsPath = fs.getPath("assets");
                if (Files.exists(assetsPath)) {
                    scanAssetsDirectory(assetsPath, folder, found);
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to scan JAR: " + uri, e);
        }
    }

    private static void scanJarNamespace(URI uri, String namespace, String folder,
                                         Map<Identifier, String> found) {
        try {
            String[] parts = uri.toString().split("!");
            if (parts.length < 2) return;

            URI jarUri = URI.create(parts[0]);

            try (FileSystem fs = FileSystems.newFileSystem(jarUri, Collections.emptyMap())) {
                Path folderPath = fs.getPath("assets", namespace, folder);
                if (Files.exists(folderPath)) {
                    scanFolderForJson(folderPath, namespace, found);
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to scan JAR namespace: " + uri, e);
        }
    }

    private static void scanFileSystem(Path assetsPath, String folder,
                                       Map<Identifier, String> found) {
        if (!Files.exists(assetsPath)) {
            return;
        }

        scanAssetsDirectory(assetsPath, folder, found);
    }

    private static void scanFileSystemNamespace(Path folderPath, String namespace,
                                                Map<Identifier, String> found) {
        if (!Files.exists(folderPath)) {
            return;
        }

        scanFolderForJson(folderPath, namespace, found);
    }

    private static void scanAssetsDirectory(Path assetsPath, String folder,
                                            Map<Identifier, String> found) {
        try (Stream<Path> namespaceDirs = Files.list(assetsPath)) {
            namespaceDirs.filter(Files::isDirectory).forEach(namespaceDir -> {
                String namespace = namespaceDir.getFileName().toString();
                Path folderPath = namespaceDir.resolve(folder);

                if (Files.exists(folderPath)) {
                    scanFolderForJson(folderPath, namespace, found);
                }
            });
        } catch (IOException e) {
            LOGGER.debug("Failed to scan assets directory: " + assetsPath, e);
        }
    }

    private static void scanFolderForJson(Path folderPath, String namespace,
                                          Map<Identifier, String> found) {
        try (Stream<Path> files = Files.walk(folderPath)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".json"))
                    .forEach(jsonFile -> {
                        String fileName = jsonFile.getFileName().toString();
                        String name = fileName.substring(0, fileName.length() - 5); // Remove .json

                        // Handle subdirectories
                        Path relative = folderPath.relativize(jsonFile);
                        String fullPath = relative.toString().replace('\\', '/');
                        String idPath = fullPath.substring(0, fullPath.length() - 5); // Remove .json

                        Identifier id = Identifier.of(namespace, idPath);
                        String resourcePath = jsonFile.toString();
                        found.put(id, resourcePath);
                    });
        } catch (IOException e) {
            LOGGER.debug("Failed to scan folder: " + folderPath, e);
        }
    }

    /**
     * Lists all namespaces that have a specific folder.
     *
     * @param folder The folder to check for (e.g., "blocks")
     * @param classLoader The classloader to scan
     * @return Set of namespace strings
     */
    public static Set<String> listNamespacesWithFolder(String folder, ClassLoader classLoader) {
        Set<String> namespaces = new HashSet<>();

        try {
            Enumeration<URL> resources = classLoader.getResources("assets");

            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                collectNamespaces(url, folder, namespaces);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to list namespaces", e);
        }

        return namespaces;
    }

    private static void collectNamespaces(URL url, String folder, Set<String> namespaces) {
        try {
            URI uri = url.toURI();

            if (uri.getScheme().equals("jar")) {
                collectNamespacesFromJar(uri, folder, namespaces);
            } else if (uri.getScheme().equals("file")) {
                collectNamespacesFromFileSystem(Paths.get(uri), folder, namespaces);
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to collect namespaces from: " + url, e);
        }
    }

    private static void collectNamespacesFromJar(URI uri, String folder, Set<String> namespaces) {
        try {
            String[] parts = uri.toString().split("!");
            if (parts.length < 2) return;

            URI jarUri = URI.create(parts[0]);

            try (FileSystem fs = FileSystems.newFileSystem(jarUri, Collections.emptyMap())) {
                Path assetsPath = fs.getPath("assets");
                if (Files.exists(assetsPath)) {
                    collectNamespacesFromPath(assetsPath, folder, namespaces);
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to collect namespaces from JAR", e);
        }
    }

    private static void collectNamespacesFromFileSystem(Path assetsPath, String folder,
                                                        Set<String> namespaces) {
        if (!Files.exists(assetsPath)) {
            return;
        }

        collectNamespacesFromPath(assetsPath, folder, namespaces);
    }

    private static void collectNamespacesFromPath(Path assetsPath, String folder,
                                                  Set<String> namespaces) {
        try (Stream<Path> dirs = Files.list(assetsPath)) {
            dirs.filter(Files::isDirectory).forEach(namespaceDir -> {
                String namespace = namespaceDir.getFileName().toString();
                Path folderPath = namespaceDir.resolve(folder);
                if (Files.exists(folderPath)) {
                    namespaces.add(namespace);
                }
            });
        } catch (IOException e) {
            LOGGER.debug("Failed to collect namespaces from: " + assetsPath, e);
        }
    }
}