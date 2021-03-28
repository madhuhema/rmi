package storage;

import common.Path;
import naming.Registration;
import rmi.RMIException;
import rmi.Skeleton;
import rmi.Stub;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Storage server.
 *
 * <p>
 * Storage servers respond to client file access requests. The files accessible
 * through a storage server are those accessible under a given directory of the
 * local filesystem.
 */
public class StorageServer implements Storage, Command {
    private final File root;
    private final Skeleton<Storage> storageSkeleton;
    private final Skeleton<Command> commandSkeleton;
    private Storage storageStub;
    private Command commandStub;

    /**
     * Creates a storage server, given a directory on the local filesystem.
     *
     * @param root Directory on the local filesystem. The contents of this
     *             directory will be accessible through the storage server.
     * @throws NullPointerException If <code>root</code> is <code>null</code>.
     */
    public StorageServer(File root) {
        if (Objects.isNull(root)) {
            throw new NullPointerException("root is missing in storage server");
        }
        this.root = root;
        this.storageSkeleton = new Skeleton<>(Storage.class, this);
        this.commandSkeleton = new Skeleton<>(Command.class, this);
    }

    /**
     * Starts the storage server and registers it with the given naming
     * server.
     *
     * @param hostname      The externally-routable hostname of the local host on
     *                      which the storage server is running. This is used to
     *                      ensure that the stub which is provided to the naming
     *                      server by the <code>start</code> method carries the
     *                      externally visible hostname or address of this storage
     *                      server.
     * @param naming_server Remote interface for the naming server with which
     *                      the storage server is to register.
     * @throws UnknownHostException  If a stub cannot be created for the storage
     *                               server because a valid address has not been
     *                               assigned.
     * @throws FileNotFoundException If the directory with which the server was
     *                               created does not exist or is in fact a
     *                               file.
     * @throws RMIException          If the storage server cannot be started, or if it
     *                               cannot be registered.
     */
    public synchronized void start(String hostname, Registration naming_server)
            throws RMIException, UnknownHostException, FileNotFoundException {
        InetAddress inetAddress = InetAddress.getByName(hostname);
        storageSkeleton.setSocket(inetAddress);
        commandSkeleton.setSocket(inetAddress);
        storageSkeleton.start();
        commandSkeleton.start();
        this.storageStub = Stub.create(Storage.class, storageSkeleton);
        this.commandStub = Stub.create(Command.class, commandSkeleton);
        List<Path> paths = Arrays.asList(naming_server.register(storageStub, commandStub, Path.list(root)).clone());
        paths.forEach(this::delete);
        deleteFolderIfEmpty(root);
    }

    /**
     * Stops the storage server.
     *
     * <p>
     * The server should not be restarted.
     */
    public void stop() {
        storageSkeleton.stop();
        stopped(null);
    }

    /**
     * Called when the storage server has shut down.
     *
     * @param cause The cause for the shutdown, if any, or <code>null</code> if
     *              the server was shut down by the user's request.
     */
    protected void stopped(Throwable cause) {
    }

    // The following methods are documented in Storage.java.
    @Override
    public synchronized long size(Path file) throws FileNotFoundException {
        File localFile = new File(this.root + file.toString());
        if (!localFile.exists() || localFile.isDirectory()) {
            throw new FileNotFoundException("file not found");
        }
        return localFile.length();
    }

    @Override
    public synchronized byte[] read(Path path, long offset, int length) throws FileNotFoundException, IOException {
        File file = new File(root + path.toString());
        if (!file.exists() || file.isDirectory()) {
            throw new FileNotFoundException("file not found");
        }
        if (offset < 0 || length < 0 || file.length() < offset + length) {
            throw new IndexOutOfBoundsException();
        }
        if (file.length() == 0) {
            return new byte[]{};
        }
        RandomAccessFile reader = new RandomAccessFile(file, "r");
        byte[] bytes = new byte[length];
        reader.read(bytes, (int) offset, length);
        return bytes;
    }

    @Override
    public synchronized void write(Path path, long offset, byte[] data) throws FileNotFoundException, IOException {
        File file = new File(root + path.toString());
        if (!file.exists() || file.isDirectory()) {
            throw new FileNotFoundException("file not found");
        }
        if (offset < 0) {
            throw new IndexOutOfBoundsException();
        }
        RandomAccessFile writer = new RandomAccessFile(file, "rw");
        if (offset != 0) {
            writer.seek(offset);
        }
        writer.write(data);
    }

    // The following methods are documented in Command.java.
    @Override
    public synchronized boolean create(Path path) {
        try {
            if (Objects.isNull(path)) {
                throw new NullPointerException();
            }
            if (path.isRoot()) {
                return false;
            }
            File file = new File(this.root + path.toString());
            if (file.exists()) return false;
            new File(file.getParent()).mkdirs();
            file.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return true;
    }

    @Override
    public synchronized boolean delete(Path path) {
        if (path.isRoot()) return false;
        File file = new File(this.root, path.toString());
        if (!file.exists()) return false;
        if (file.isFile()) {
            file.delete();
        } else if (file.isDirectory()) {
            deleteFolder(file);
        }
        return true;
    }

    private void deleteFolder(File folder) {
        for (File file : folder.listFiles()) {
            if (file.isDirectory()) {
                deleteFolder(file);
            }
            file.delete();
        }
        folder.delete();
    }

    /**
     * Deletes the empty folders in the root directory
     *
     * @param root folder in which to recursively delete the empty folders
     */
    private void deleteFolderIfEmpty(File root) {

        if (root.isDirectory()) {
            Arrays.stream(Objects.requireNonNull(root.listFiles())).forEach(this::deleteFolderIfEmpty);
            if (Objects.requireNonNull(root.list()).length == 0) {
                root.delete();
            }
        }
    }
}
