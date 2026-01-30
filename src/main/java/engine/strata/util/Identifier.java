package engine.strata.util;

import java.util.Objects;

public final class Identifier{
    public final String namespace;
    public final String path;

    private Identifier(String namespace, String path) {
        assert isNamespaceValid(namespace);

        assert isPathValid(path);

        this.namespace = namespace;
        this.path = path;
    }

    private static Identifier ofValidated(String namespace, String path) {
        return new Identifier(validateNamespace(namespace, path), validatePath(namespace, path));
    }

    public static Identifier of(String namespace, String path) {
        return new Identifier(namespace, path);
    }

    public static Identifier of(String id) {
        return splitOn(id, ':');
    }

    public static Identifier ofEngine(String path) {
        return new Identifier("strata", validatePath("strata", path));
    }

    public static Identifier splitOn(String id, char delimiter) {
        int i = id.indexOf(delimiter);
        if (i >= 0) {
            String path = id.substring(i + 1);
            if (i != 0) {
                String namespace = id.substring(0, i);
                return ofValidated(namespace, path);
            } else {
                return ofEngine(path);
            }
        } else {
            return ofEngine(id);
        }
    }

    /**
     * {@return whether {@code path} can be used as an identifier's path}
     */
    public static boolean isPathValid(String path) {
        for (int i = 0; i < path.length(); i++) {
            if (!isPathCharacterValid(path.charAt(i))) {
                return false;
            }
        }

        return true;
    }

    /**
     * {@return whether {@code namespace} can be used as an identifier's namespace}
     */
    public static boolean isNamespaceValid(String namespace) {
        for (int i = 0; i < namespace.length(); i++) {
            if (!isNamespaceCharacterValid(namespace.charAt(i))) {
                return false;
            }
        }

        return true;
    }

    private static String validateNamespace(String namespace, String path) {
        if (!isNamespaceValid(namespace)) {
            throw new InvalidIdentifierException("Non [a-z0-9_.-] character in namespace of location: " + namespace + ":" + path);
        } else {
            return namespace;
        }
    }

    /**
     * {@return whether {@code character} is valid for use in identifier paths}
     */
    public static boolean isPathCharacterValid(char character) {
        return character == '_'
                || character == '-'
                || character >= 'a' && character <= 'z'
                || character >= '0' && character <= '9'
                || character == '/'
                || character == '.';
    }

    /**
     * {@return whether {@code character} is valid for use in identifier namespaces}
     */
    private static boolean isNamespaceCharacterValid(char character) {
        return character == '_' || character == '-' || character >= 'a' && character <= 'z' || character >= '0' && character <= '9' || character == '.';
    }

    private static String validatePath(String namespace, String path) {
        if (!isPathValid(path)) {
            throw new InvalidIdentifierException("Non [a-z0-9/._-] character in path of location: " + namespace + ":" + path);
        } else {
            return path;
        }
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Identifier that = (Identifier) o;
        return Objects.equals(namespace, that.namespace) &&
                Objects.equals(path, that.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(namespace, path);
    }

    public String toString() {
        return namespace + ":" + path;
    }

    public String toAssetBase() {
        return namespace + "/" + path;
    }

    public String toAssetPath(String folder, String extension) {
        return "assets/" + namespace + "/" + folder + "/" + path + "." + extension;
    }
}
