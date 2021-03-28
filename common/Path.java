package common;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Distributed filesystem paths.
 *
 * <p>
 * Objects of type <code>Path</code> are used by all filesystem interfaces.
 * Path objects are immutable.
 *
 * <p>
 * The string representation of paths is a forward-slash-delimeted sequence of
 * path components. The root directory is represented as a single forward
 * slash.
 *
 * <p>
 * The colon (<code>:</code>) and forward slash (<code>/</code>) characters are
 * not permitted within path components. The forward slash is the delimeter,
 * and the colon is reserved as a delimeter for application use.
 */
public class Path implements Iterable<String>, Serializable {
    public final String ROOT_PATH = "/";
    private String location;

    /**
     * Creates a new path which represents the root directory.
     */
    public Path() {
        location = ROOT_PATH;
    }

    /**
     * Creates a new path by appending the given component to an existing path.
     *
     * @param path      The existing path.
     * @param component The new component.
     * @throws IllegalArgumentException If <code>component</code> includes the
     *                                  separator, a colon, or
     *                                  <code>component</code> is the empty
     *                                  string.
     */
    public Path(Path path, String component) {
        if (component.equals("") || component.contains(":") || component.contains("/")) {
            throw new IllegalArgumentException("Please check your component value");
        }
        location = Objects.isNull(path.location) ? ROOT_PATH : path.location;
        if (!path.location.endsWith("/")) {
            location += "/";
        }
        location += component;

    }

    /**
     * Creates a new path from a path string.
     *
     * <p>
     * The string is a sequence of components delimited with forward slashes.
     * Empty components are dropped. The string must begin with a forward
     * slash.
     *
     * @param path The path string.
     * @throws IllegalArgumentException If the path string does not begin with
     *                                  a forward slash, or if the path
     *                                  contains a colon character.
     */
    public Path(String path) {
        if (path.equals("")) {
            throw new IllegalArgumentException("The path is empty");
        }
        if (!path.startsWith("/")) {
            throw new IllegalArgumentException("The path should start with forward slash");
        }
        if (path.contains(":")) {
            throw new IllegalArgumentException("The component contains colon");
        }
        location = "";
        for (String component : path.split("/")) {
            if (!component.trim().equals("")) {
                location = location.concat("/");
                location = location.concat(component.trim());
            }
        }
        if (location.equals("")) {
            location = ROOT_PATH;
        }
    }

    /**
     * Returns an iterator over the components of the path.
     *
     * <p>
     * The iterator cannot be used to modify the path object - the
     * <code>remove</code> method is not supported.
     *
     * @return The iterator.
     */
    @Override
    public Iterator<String> iterator() {
        List<String> components = new ArrayList<>();
        Arrays.stream(
                location.split("/")).
                filter(e -> !e.equals("")).
                forEach(e -> components.add(e.trim()));
        return new NonRemovableIterator<>(components.iterator());
    }

    /**
     * Lists the paths of all files in a directory tree on the local
     * filesystem.
     *
     * @param directory The root directory of the directory tree.
     * @return An array of relative paths, one for each file in the directory
     * tree.
     * @throws FileNotFoundException    If the root directory does not exist.
     * @throws IllegalArgumentException If <code>directory</code> exists but
     *                                  does not refer to a directory.
     */
    public static Path[] list(File directory) throws FileNotFoundException {
        if (!directory.exists()) {
            throw new FileNotFoundException("Directory does not exist");
        }
        if (!directory.isDirectory()) {
            throw new IllegalArgumentException("File is not a directory");
        }
        return readDirectories(directory, new ArrayList<Path>(), directory.getAbsolutePath().length());
    }

    private static Path[] readDirectories(File directory, ArrayList<Path> pathList, int length) {
        List<Path> paths = Arrays.stream(directory.listFiles()).map(file -> {
            if (file.isDirectory()) {
                readDirectories(file, pathList, length);
                return null;
            }
            String filePath = file.getAbsolutePath().substring(length);
            filePath = filePath.replaceAll("\\\\", "/");
            return new Path(filePath);
        }).filter(Objects::nonNull).collect(Collectors.toList());
        pathList.addAll(paths);
        return pathList.toArray(new Path[0]);
    }

    /**
     * Determines whether the path represents the root directory.
     *
     * @return <code>true</code> if the path does represent the root directory,
     * and <code>false</code> if it does not.
     */
    public boolean isRoot() {
        return this.location.equals(ROOT_PATH);
    }

    /**
     * Returns the path to the parent of this path.
     *
     * @throws IllegalArgumentException If the path represents the root
     *                                  directory, and therefore has no parent.
     */
    public Path parent() {
        if (isRoot()) {
            throw new IllegalArgumentException("No parent path yet");
        }
        int end = location.lastIndexOf("/");
        return new Path(location.substring(0, end));
    }

    /**
     * Returns the last component in the path.
     *
     * @throws IllegalArgumentException If the path represents the root
     *                                  directory, and therefore has no last
     *                                  component.
     */
    public String last() {
        if (isRoot()) {
            throw new IllegalArgumentException("this is root directory, no last part available in path");
        }
        int start = location.lastIndexOf("/") + 1;
        return location.substring(start, location.length());
    }

    /**
     * Determines if the given path is a subpath of this path.
     *
     * <p>
     * The other path is a subpath of this path if is a prefix of this path.
     * Note that by this definition, each path is a subpath of itself.
     *
     * @param other The path to be tested.
     * @return <code>true</code> If and only if the other path is a subpath of
     * this path.
     */
    public boolean isSubpath(Path other) {
        return location.contains(other.location);
    }

    /**
     * Converts the path to <code>File</code> object.
     *
     * @param root The resulting <code>File</code> object is created relative
     *             to this directory.
     * @return The <code>File</code> object.
     */
    public File toFile(File root) {
        return new File(root.getPath());
    }

    /**
     * Compares two paths for equality.
     *
     * <p>
     * Two paths are equal if they share all the same components.
     *
     * @param other The other path.
     * @return <code>true</code> if and only if the two paths are equal.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Path)) return false;
        Path path = (Path) other;
        return Objects.equals(location, path.location);
    }

    /**
     * Returns the hash code of the path.
     */
    @Override
    public int hashCode() {
        return Objects.hash(ROOT_PATH, location);
    }

    public List<String> getComponents() {
        return Arrays.stream(this.toString().split("/")).filter(s -> !s.isEmpty()).collect(Collectors.toList());
    }

    /**
     * Converts the path to a string.
     *
     * <p>
     * The string may later be used as an argument to the
     * <code>Path(String)</code> constructor.
     *
     * @return The string representation of the path.
     */
    @Override
    public String toString() {
        return location;
    }
}
