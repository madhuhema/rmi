package rmi;

import common.Tools;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * RMI skeleton
 *
 * <p>
 * A skeleton encapsulates a multithreaded TCP server. The server's clients are
 * intended to be RMI stubs created using the <code>Stub</code> class.
 *
 * <p>
 * The skeleton class is parametrized by a type variable. This type variable
 * should be instantiated with an interface. The skeleton will accept from the
 * stub requests for calls to the methods of this interface. It will then
 * forward those requests to an object. The object is specified when the
 * skeleton is constructed, and must implement the remote interface. Each
 * method in the interface should be marked as throwing
 * <code>RMIException</code>, in addition to any other exceptions that the user
 * desires.
 *
 * <p>
 * Exceptions may occur at the top level in the listening and service threads.
 * The skeleton's response to these exceptions can be customized by deriving
 * a class from <code>Skeleton</code> and overriding <code>listen_error</code>
 * or <code>service_error</code>.
 */
public class Skeleton<T> {
    /**
     * Server Socket address
     */
    private InetSocketAddress socket;

    /**
     * Server Listener
     */
    private ServerSocket server;

    /**
     * Thread for listening requests
     */
    private RequestHandler requestHandler;

    /**
     * Server current status
     */
    private boolean status;

    /**
     * Server for which the skeleton is associated
     */
    private T serverInstance;

    /**
     * default port for skeletons if not specified externally
     */
    private final static AtomicInteger port = new AtomicInteger(1000);

    /**
     * Map holding respective skeleton for each client
     */
    public static Map<InetSocketAddress, Skeleton> skeletonMap = new HashMap<>();


    /**
     * Creates a <code>Skeleton</code> with no initial server address. The
     * address will be determined by the system when <code>start</code> is
     * called. Equivalent to using <code>Skeleton(null)</code>.
     *
     * <p>
     * This constructor is for skeletons that will not be used for
     * bootstrapping RMI - those that therefore do not require a well-known
     * port.
     *
     * @param c      An object representing the class of the interface for which the
     *               skeleton server is to handle method call requests.
     * @param server An object implementing said interface. Requests for method
     *               calls are forwarded by the skeleton to this object.
     * @throws Error                If <code>c</code> does not represent a remote interface -
     *                              an interface whose methods are all marked as throwing
     *                              <code>RMIException</code>.
     * @throws NullPointerException If either of <code>c</code> or
     *                              <code>server</code> is <code>null</code>.
     */
    public Skeleton(Class<T> c, T server) {
        Tools.checkNull(c, server);
        Tools.checkRMIImplementation(c);
        this.serverInstance = server;
        this.socket = new InetSocketAddress(port.incrementAndGet());
        skeletonMap.put(socket, this);
    }

    /**
     * Creates a <code>Skeleton</code> with the given initial server address.
     *
     * <p>
     * This constructor should be used when the port number is significant.
     *
     * @param c       An object representing the class of the interface for which the
     *                skeleton server is to handle method call requests.
     * @param server  An object implementing said interface. Requests for method
     *                calls are forwarded by the skeleton to this object.
     * @param address The address at which the skeleton is to run. If
     *                <code>null</code>, the address will be chosen by the
     *                system when <code>start</code> is called.
     * @throws Error                If <code>c</code> does not represent a remote interface -
     *                              an interface whose methods are all marked as throwing
     *                              <code>RMIException</code>.
     * @throws NullPointerException If either of <code>c</code> or
     *                              <code>server</code> is <code>null</code>.
     */
    public Skeleton(Class<T> c, T server, InetSocketAddress address) {
        Tools.checkNull(c, server);
        Tools.checkRMIImplementation(c);
        socket = address;
        this.serverInstance = server;
        skeletonMap.put(socket, this);

    }

    /**
     * Called when the listening thread exits.
     *
     * <p>
     * The listening thread may exit due to a top-level exception, or due to a
     * call to <code>stop</code>.
     *
     * <p>
     * When this method is called, the calling thread owns the lock on the
     * <code>Skeleton</code> object. Care must be taken to avoid deadlocks when
     * calling <code>start</code> or <code>stop</code> from different threads
     * during this call.
     *
     * <p>
     * The default implementation does nothing.
     *
     * @param cause The exception that stopped the skeleton, or
     *              <code>null</code> if the skeleton stopped normally.
     */
    protected void stopped(Throwable cause) {
//        if (Objects.isNull(cause)) {
//            System.out.println("stopped server");
//        } else {
//            cause.printStackTrace();
//        }
    }

    /**
     * Called when an exception occurs at the top level in the listening
     * thread.
     *
     * <p>
     * The intent of this method is to allow the user to report exceptions in
     * the listening thread to another thread, by a mechanism of the user's
     * choosing. The user may also ignore the exceptions. The default
     * implementation simply stops the server. The user should not use this
     * method to stop the skeleton. The exception will again be provided as the
     * argument to <code>stopped</code>, which will be called later.
     *
     * @param exception The exception that occurred.
     * @return <code>true</code> if the server is to resume accepting
     * connections, <code>false</code> if the server is to shut down.
     */
    protected boolean listen_error(Exception exception) {
        exception.printStackTrace();
        return false;
    }

    /**
     * Called when an exception occurs at the top level in a service thread.
     *
     * <p>
     * The default implementation does nothing.
     *
     * @param exception The exception that occurred.
     */
    protected void service_error(RMIException exception) {
    }

    /**
     * Starts the skeleton server.
     *
     * <p>
     * A thread is created to listen for connection requests, and the method
     * returns immediately. Additional threads are created when connections are
     * accepted. The network address used for the server is determined by which
     * constructor was used to create the <code>Skeleton</code> object.
     *
     * @throws RMIException When the listening socket cannot be created or
     *                      bound, when the listening thread cannot be created,
     *                      or when the server has already been started and has
     *                      not since stopped.
     */
    public synchronized void start() throws RMIException {
        try {
            server = new ServerSocket(socket.getPort());
            requestHandler = new RequestHandler(server);
            requestHandler.start();
            status = true;
        } catch (Exception p_e) {
            throw new RMIException(p_e);
        }
    }

    /**
     * Stops the skeleton server, if it is already running.
     *
     * <p>
     * The listening thread terminates. Threads created to service connections
     * may continue running until their invocations of the <code>service</code>
     * method return. The server stops at some later time; the method
     * <code>stopped</code> is called at that point. The server may then be
     * restarted.
     */
    public synchronized void stop() {
        if (status && Objects.nonNull(requestHandler)) {
            requestHandler.stopServer();
            status = false;
            stopped(null);
            skeletonMap.remove(socket);
        }
    }

    /**
     * Returns the server status
     *
     * @return status of server
     */
    public boolean isRunning() {
        return status;
    }


    public InetSocketAddress getSocket() {
        return socket;
    }

    /**
     * setSocket address using the host address. Port is decided by skeleton itself.
     *
     * @param p_socket Host Address is needed
     */
    public void setSocket(InetAddress p_socket) {
        int port = Skeleton.port.incrementAndGet();
        socket = new InetSocketAddress(p_socket, port);
    }

    /**
     * setSocket address using the host address.
     *
     * @param p_socket Host socket is needed
     */
    public void setSocket(InetSocketAddress p_socket) {
        socket = p_socket;
    }

    /**
     * Returns the assigned server
     *
     * @return this server
     */
    public T getServerInstance() {
        return serverInstance;
    }

    /**
     * Set the server
     *
     * @param p_serverInstance Server Instance
     */
    public void setServerInstance(T p_serverInstance) {
        serverInstance = p_serverInstance;
    }
}
