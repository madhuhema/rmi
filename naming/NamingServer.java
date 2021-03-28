package naming;

import common.Path;
import rmi.RMIException;
import rmi.Skeleton;
import storage.Command;
import storage.Storage;

import java.io.FileNotFoundException;
import java.net.InetSocketAddress;
import java.util.*;

/**
 * Naming server.
 *
 * <p>
 * Each instance of the filesystem is centered on a single naming server. The
 * naming server maintains the filesystem directory tree. It does not store any
 * file data - this is done by separate storage servers. The primary purpose of
 * the naming server is to map each file name (path) to the storage server
 * which hosts the file's contents.
 *
 * <p>
 * The naming server provides two interfaces, <code>Service</code> and
 * <code>Registration</code>, which are accessible through RMI. Storage servers
 * use the <code>Registration</code> interface to inform the naming server of
 * their existence. Clients use the <code>Service</code> interface to perform
 * most filesystem operations. The documentation accompanying these interfaces
 * provides details on the methods supported.
 *
 * <p>
 * Stubs for accessing the naming server must typically be created by directly
 * specifying the remote network address. To make this possible, the client and
 * registration interfaces are available at well-known ports defined in
 * <code>NamingStubs</code>.
 */
public class NamingServer implements Service, Registration {

    /**
     * DirectoryInfo object containing map of the Naming server's directory structure
     */
    static DirectoryInfo directoryInfo;
    /**
     * Set of Storage stubs registered with this NamingServer
     */
    static Set<Storage> storageStubSet;
    /**
     * Set of Command stubs registered with this NamingServer
     */
    static Set<Command> commandStubSet;
    Skeleton<Service> serviceSkeleton;
    Skeleton<Registration> registrationSkeleton;

    /**
     * Default ConstructorI
     *
     * <p>
     * The naming server is not started.</p>
     */
    public NamingServer() {
        storageStubSet = new HashSet<>();
        commandStubSet = new HashSet<>();
        directoryInfo = new DirectoryInfo();
    }

    /**
     * Method starting the naming server.
     *
     * <p>
     * Once this method is called, it is possible to remotely access the client and
     * registration interfaces of the naming server.
     *
     * @throws RMIException If either of the two skeletons, for the client or
     *                      registration server interfaces, could not be
     *                      started. The user should not attempt to start the
     *                      server again if an exception occurs.
     */
    public synchronized void start() throws RMIException {

        //This serviceSkeleton is assigned a value, given localHost address
        serviceSkeleton = new Skeleton<>(
                Service.class, new NamingServer(), new InetSocketAddress("127.0.0.1", NamingStubs.SERVICE_PORT));
        //This registrationSkeleton is assigned a value, given localHost address
        registrationSkeleton = new Skeleton<>(
                Registration.class, new NamingServer(), new InetSocketAddress("127.0.0.1", NamingStubs.REGISTRATION_PORT));

        //Start this skeletons
        serviceSkeleton.start();
        registrationSkeleton.start();
    }

    /**
     * Stops the naming server.
     *
     * <p>
     * This method waits for both the client and registration interface
     * skeletons to stop. It attempts to interrupt as many of the threads that
     * are executing naming server code as possible. After this method is
     * called, the naming server is no longer accessible remotely. The naming
     * server should not be restarted.
     */
    public void stop() {

        //Stop this skeletons
        serviceSkeleton.stop();
        registrationSkeleton.stop();
        stopped(null);
    }

    /**
     * Indicates that the server has completely shut down.
     *
     * <p>
     * This method should be overridden for error reporting and application
     * exit purposes. The default implementation does nothing.
     *
     * @param cause The cause for the shutdown, or <code>null</code> if the
     *              shutdown was by explicit user request.
     */
    protected void stopped(Throwable cause) {
    }

    // The following methods are documented in Service.java.
    @Override
    public boolean isDirectory(Path path) throws FileNotFoundException {
        if (path == null) {
            throw new NullPointerException("Path for isDirectory cannot be null");
        }
        if (!directoryInfo.pathExists(path)) {
            throw new FileNotFoundException("directory not found");
        }

        return directoryInfo.isDirectory(path);
    }

    @Override
    public String[] list(Path directory) throws FileNotFoundException {

        if (directory == null) {
            throw new NullPointerException("Path for list method cannot be null");
        }
        if (!directoryInfo.pathExists(directory)) {
            throw new FileNotFoundException("directory not found");
        }
        if (!directoryInfo.isDirectory(directory)) {
            throw new FileNotFoundException("not a directory");
        }

        return directoryInfo.list(directory);
    }

    @Override
    public boolean createFile(Path file)
            throws RMIException, FileNotFoundException {

        if (file == null) {
            throw new NullPointerException("file cannot be null");
        }
        if (!directoryInfo.parentExists(file)) {
            throw new FileNotFoundException("Parent Directory does not exist");
        }
        if (directoryInfo.pathExists(file)) {
            return false;
        }

        //Command one the Storage servers to create the file
        commandStubSet.iterator().next().create(file);

        //Add the path to the directoryInfo, given the associated storage stub
        return directoryInfo.addPath(file, storageStubSet.iterator().next(), commandStubSet.iterator().next());
    }

    @Override
    public boolean createDirectory(Path directory) throws FileNotFoundException {

        if (directory == null) {
            throw new NullPointerException("directory cannot be null");
        }
        if (directoryInfo.pathExists(directory)) {
            return false;
        }
        if (!directoryInfo.parentExists(directory)) {
            throw new FileNotFoundException("Parent Directory does not exist");
        }

        //Add the path to the directoryInfo, given the associated storage stub
        return directoryInfo.addPathDirectory(directory, storageStubSet.iterator().next(), commandStubSet.iterator().next());
    }

    @Override
    public boolean delete(Path path) throws FileNotFoundException {

        if(path == null) {
            throw new NullPointerException();
        }
        if(!directoryInfo.pathExists(path)) {
            throw new FileNotFoundException();
        }

        try {
            directoryInfo.getCommandStub(path).delete(path);
            directoryInfo.deletePath(path);
            return true;
        } catch (RMIException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public Storage getStorage(Path file) throws FileNotFoundException {

        if (file == null) {
            throw new NullPointerException();
        }
        if (!directoryInfo.pathExists(file)) {
            throw new FileNotFoundException();
        }
        if (directoryInfo.isDirectory(file)) {
            throw new FileNotFoundException("No storage stub with a directory");
        }

        return directoryInfo.getStorageStub(file);
    }

    // The method register is documented in Registration.java.
    @Override
    public Path[] register(Storage client_stub, Command command_stub,
                           Path[] files) {

        if (client_stub == null || command_stub == null || files == null) {
            throw new NullPointerException("Cannot pass null parameters");
        }
        if (storageStubSet.contains(client_stub) || commandStubSet.contains(command_stub)) {
            throw new IllegalStateException("Storage server already registered");
        }

        //Add storage and command stubs to this Set
        storageStubSet.add(client_stub);
        commandStubSet.add(command_stub);

        //Get the Paths to Delete
        Path[] toDelete = getToDeleteArr(files);

        //For each of the paths to be registered, remove the Paths to be deleted, then add them to the directoryInfo
        Arrays.stream(files)
                .filter(file -> Arrays.stream(toDelete).noneMatch(toDelFile -> toDelFile == file))
                .forEach(file -> directoryInfo.addPath(file, client_stub, command_stub));

        return toDelete;
    }

    /**
     * Returns the Paths to be deleted during registration
     *
     * @param files to be registered
     * @return the Paths to be deleted, which are the Paths which already exist
     */
    private Path[] getToDeleteArr(Path[] files) {

        //The files to be deleted are those with an already existing path, excluding the root
        return Arrays.stream(files)
                .filter(file -> !file.toString().equals("/"))
                .filter(file -> directoryInfo.pathExists(file))
                .toArray(Path[]::new);
    }
}
