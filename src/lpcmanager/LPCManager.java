package lpcmanager;

import main.java.com.amco.amcoticketmt.utils.MTProperties;
import main.java.com.amco.amcoticketmt.utils.PropName;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Level;

/**
 * Class that is responsible to open a socket with LPC server and use it
 * to send and receive data.
 * <p>
 * One thread listens for incoming traffic and another
 * thread reading commands from a queue and sending them to LPC server.
 * Threads communicate each other with wait() and notify() in order
 * for a dispatched command to wait for the reply.
 * <p>
 * Also scheduled jobs are dispatched for giving maintenance commands list.
 *
 * @author user
 */
public class LPCManager {

    private static final Logger LOGGER = LogManager.getLogger(LPCManager.class);

    private final int port;
    private final String ip;
    private final Object commandLock;

    private Socket socket = null;
    private PrintWriter out = null;
    private BufferedReader in = null;

    private LinkedBlockingQueue<Command> queue = null;
    private ArrayList<Command> maintenanceCommands = null;
    private ArrayList<ScheduledFuture<?>> maintenanceTasks = null;
    private ScheduledFuture<?> maintenanceScheduler = null;
    private ScheduledExecutorService scheduler;

    private Command currentCommand = null;
    private Thread incomingHandler = null;
    private Thread outcomingHandler = null;
    private Thread threadsWatchdog = null;
    ExecutorService exService;

    /**
     * Use LPCManagerProxy to create new LPCManager
     *
     * @param ip                  IP Address of the LPC server
     * @param port                Port of the LPC server
     * @param maintenanceCommands List of maintenance commands
     */
    public LPCManager(String ip, Integer port, ArrayList<Command> maintenanceCommands) {

        this.scheduler = Executors.newScheduledThreadPool(MTProperties.getInt(PropName.ThreadPoolSize));
        this.queue = new LinkedBlockingQueue<Command>(MTProperties.getInt(PropName.LPCQueueSize, 5));
        this.maintenanceTasks = new ArrayList<ScheduledFuture<?>>();
        this.ip = ip;
        this.port = port;
        this.maintenanceCommands = maintenanceCommands;
        this.commandLock = new Object();
        this.exService = Executors.newFixedThreadPool(1);

        setupSocket();
        startIncomingHandler();
        startOutcomingHandler();
        startMaintenanceJobs();
        startWatchdog();
    }

    /**
     * Do necessary stuff to terminate LPCManager.
     */
    public void terminateLPCManager() {
        try {
            scheduler.shutdownNow();

            out.flush();
            out.close();
            in.close();
            socket.close();

        } catch (IOException ex) {
            LOGGER.log(Level.ERROR, "Failed to terminate LPCManager", ex);
        }
    }

    /**
     * Setup socket for communication with the LPC server.
     * The same socket is used for sending and receiving data.
     *
     * @return True if successful, false if not
     */
    private boolean setupSocket() {
        boolean setupOK = false;

        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ex) {
                LOGGER.log(Level.ERROR, "Failed to close open socket", ex);
                return false;
            }
        }

        try {
            socket = new Socket(ip, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            setupOK = true;
        } catch (IOException ex) {
            LOGGER.log(Level.ERROR, "Failed to setup socket", ex.getMessage());
            setupOK = false;
        }

        Runtime.getRuntime()
                .addShutdownHook(new Thread() {
                    @Override
                    public void run() {
                        try {
                            socket.close();
                            LOGGER.log(Level.WARN, "The socket is closed!");
                        } catch (IOException ex) {
                            LOGGER.log(Level.ERROR, "Failed to shut down socket", ex);
                        }
                    }
                }
                );
        return setupOK;
    }

    /**
     * Start a new Task in order to
     * <ul>
     * <li> Add Command to queue</li>
     * <li> Wait for Command to complete</li>
     * <li> When Command.setResponse() is used the reply is ready</li>
     * <li> Get the reply</li>
     * <li> Handle reply and return string with response to caller</li>
     * </ul>
     *
     * @param newCommand Command to add to queue
     * @return Response of the LPC server
     *
     * @throws LPCManagerException When
     * <ul>
     * <li> If socket is closed </li>
     * <li> Cannot add to queue</li>
     * <li> Command.setResponse() threw an LPCManagerException</li>
     * <li> handleResponse() threw an LPCManagerException</li>
     * </ul>
     *
     */
    public String sendCommand(final Command newCommand) throws LPCManagerException {

        boolean successfullyAddedToQueue = false;

        // Return if socket is not ready
        if (socket.isClosed()) {
            LOGGER.log(Level.WARN, "LPC Not accepting new entries; socket is down");
            throw new LPCManagerException("LPC Not accepting new entries; socket is down");
        }

        // Try to add to queue
        try {
            LOGGER.log(Level.DEBUG, "Adding to queue: " + newCommand.commandString.trim());
            successfullyAddedToQueue = queue.add(newCommand);
        } catch (IllegalStateException ex) {
            throw new LPCManagerException("LPC command: " + newCommand.commandString.trim() + " cannot be added to queue" + ex.getMessage());
        }

        if (successfullyAddedToQueue == false) {
            throw new LPCManagerException("LPC command: " + newCommand.commandString.trim() + " cannot be added to queue");
        }

        class Task implements Callable<String> {

            @Override
            public String call() throws LPCManagerException {

                // This will block until Command.setResponse() is used. See Command class.
                String resp = newCommand.getResponse();

                // Return the reply when getResponse() unblocks
                return resp;
            }
        }
        // We need thread (it will block for reply) but return a value
        FutureTask<String> futureTask = new FutureTask<String>(new Task());
        exService.execute(futureTask);

        String reply = "-";
        try {
            // Task.call() method has been blocked at the point of newCommand.getResponse()
            // Use futureTask.get() which blocks until Task.call() method returns the string 
            // with the reply
            reply = handleResponse(futureTask.get());
        } catch (InterruptedException ex) {
            LOGGER.log(Level.ERROR, "InterruptedException: " + ex.getMessage());
        } catch (ExecutionException ex) {
            LOGGER.log(Level.ERROR, "ExecutionException" + ex.getMessage());
        } catch (LPCManagerException ex) {
            throw new LPCManagerException(ex.getMessage());
        }

        return reply;
    }

    /**
     * Convenience method for batch inserting commands. If one Command fails
     * the other Commands are not executed.
     *
     * @param commands List that contains all batch Commands
     * @return List with the replies from LPC server
     *
     * @throws LPCManagerException If sendCommand throws it.
     */
    public synchronized ArrayList<String> sendBatchCommand(Command... commands) throws LPCManagerException {
        final ArrayList<String> replies = new ArrayList<String>();

        for (final Command command : commands) {
            replies.add(sendCommand(command));
        }
        return replies;
    }

    private void printQueue(String msg) {
        System.out.print("---- " + msg);
        for (Command c : queue) {
            System.out.print(c.commandString.trim() + ", ");
        }
        System.out.println();
    }

    /**
     * Get the LPC response and parse it.
     * When it is an 'error' throw LPCManagerException.
     * When it is 'ok' return the reply written in currentCommand.
     * Events are handled right after the data arrive to
     * socket (see 'startIncomingHandler')
     *
     * @return The reply from LPC server without the starting 'ok'
     * if the reply has data (comma separated values)
     *
     * @throws LPCManagerException If LPC server reply starts with error or
     * if 'LPC is down' written as reply during
     * lost of communication with LPC server.
     */
    private String handleResponse(String lpcResponse) throws LPCManagerException {
        if (lpcResponse.startsWith("error")) {
            String error = lpcResponse.substring(lpcResponse.indexOf(",") + 1);
            throw new LPCManagerException("Got error from lpc: " + error);
        } else if (lpcResponse.startsWith("ok")) {
            String a = lpcResponse.substring(lpcResponse.indexOf(",") + 1);
            LOGGER.log(Level.DEBUG, "ANSWER: " + a);
        }

        return lpcResponse;
    }

    /**
     * An event string came from socket. Handle it properly.
     *
     * @param incomingString What came from LPC server
     */
    private void handleEvent(String incomingString) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * Iterate maintenance commands list and start a scheduler at a fix rate
     * to send the maintenance command in intervals.
     * <p>
     * When the scheduled job starts, waits for the queue to be empty, then
     * adds the command to the queue. If queue is not empty sleeps for half
     * a second and then retries.
     */
    private void startMaintenanceJobs() {
        Integer interval = MTProperties.getInt(PropName.LPCManagerMainenanceJobsIntervalSecs, 0);

        // Do nothing if no maintenance commands provided during constructing
        // this object.
        if (maintenanceCommands == null) {
            LOGGER.log(Level.WARN, "No mainenance jobs provided");
            return;
        }

        // Iterate maintenance command and start a new scheduled job for each
        // one of them.
        for (final Command command : maintenanceCommands) {
            LOGGER.log(Level.INFO, "Dispatching scheduled job for: " + command.commandString.trim());

            ScheduledFuture<?> task = scheduler.scheduleWithFixedDelay(new Runnable() {

                @Override
                public void run() {
                    AtomicBoolean retry = new AtomicBoolean(true);
                    Integer retries = 0;

//                    while (retry.get()) {
                    if (queue.isEmpty()) {
                        try {
                            sendCommand(command);
                            LOGGER.log(Level.DEBUG, "Send mainenance job: " + command.commandString.trim() + " after retries: " + retries);
                            retry.set(false);
                        } catch (LPCManagerException ex) {
                            LOGGER.log(Level.ERROR, "Add maintenance job: " + command.commandString.trim() + " to queue, failed");
                        }
                    }
//                        } else {
//                            try {
//                                Thread.sleep(500);
//                            } catch (InterruptedException ex) {
//                                LOGGER.log(Level.WARN, "Interrupted while waiting for half a second to retry to send mainenance job: " + command.commandString, ex);
//                                return;
//                            }
//                            retries++;
//                            // If queue is not empty, retry 3 times and then give up.
//                            if (retries > 2) {
//                                retry.set(false);
//                                System.out.println("*************** DROP: " + command.commandString.trim());
//                            }
//                        }
//                    }
                    LOGGER.log(Level.DEBUG, "Done with maintenance job:" + command.commandString.trim());
                }
            }, 1, interval, TimeUnit.SECONDS);
            maintenanceTasks.add(task);
        }
    }

    /**
     * Stop all maintenance jobs and create a new scheduler.
     */
    private void stopMainenanceJobs() {
        ((ScheduledThreadPoolExecutor) scheduler).shutdownNow();
        this.scheduler = Executors.newScheduledThreadPool(MTProperties.getInt(PropName.ThreadPoolSize));
    }

    /**
     * Thread responsible to peek Commands from queue and execute them.
     * When it dispatches the command it waits for Thread startIncomingHandler
     * to remove the Command from queue and notify that there is a response.
     * Then it proceeds to the next Command in queue
     */
    private void startOutcomingHandler() {

        outcomingHandler = new Thread(new Runnable() {

            @Override
            public void run() {
                AtomicBoolean running = new AtomicBoolean(true);

                while (running.get()) {
                    if (!socket.isClosed()) {
                        // Socket ready
                        // Proccess queue
                        if ((currentCommand = queue.peek()) != null) {
                            synchronized (commandLock) {

                                printQueue("NOT EMPTY: ");

                                LOGGER.log(Level.DEBUG, "Send command from queue:" + currentCommand.commandString.trim());

                                out.write(currentCommand.commandString);
                                out.flush();

                                // Wait for the reply from the other thread
                                try {
                                    commandLock.wait();
                                } catch (InterruptedException ex) {
                                    LOGGER.log(Level.WARN, "Interrupted while waiting for reply for command: " + currentCommand.commandString.trim(), ex.getMessage());
                                    running.set(false);
                                }
                            }
                        } else {
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException ex) {
                                LOGGER.log(Level.ERROR, "Interrupted while sleeping on an empty queue", ex.getMessage());
                            }
                        }

                    } else {
                        // Socket not ready
                        running.set(false);
                        LOGGER.log(Level.WARN, "Socket not ready");
                    }
                }
            }
        });

        outcomingHandler.start();
    }

    /**
     * Thread that is constantly listening for incoming traffic from socket.
     * If it's an event then it handles it to eventHandler otherwise it
     * sets the response to currentCommand by calling
     * currentCommand.setResponse()
     * (this call unblocks sendCommand() which waits for the reply to get set).
     * Then remove the command from queue notify StartOutcomingHandler that
     * it can proceed with the next Command.
     */
    private void startIncomingHandler() {
        incomingHandler = new Thread(new Runnable() {

            @Override
            public void run() {
                String incomingString = null;
                AtomicBoolean running = new AtomicBoolean(true);

                while (running.get()) {
                    if (!socket.isClosed()) {
                        // Socket ready
                        try {
                            // Proccess incoming
                            incomingString = in.readLine();
                        } catch (IOException ex) {
                            LOGGER.log(Level.ERROR, "Reading stream produce I/O error", ex.getMessage());
                            if (currentCommand != null) {
                                synchronized (commandLock) {
                                    // Set response for pending action
                                    currentCommand.setResponse("error,LOCAL,LPC is down while trying to read from socket for: " + currentCommand.commandString.trim());
                                    // Notify to proceed to the next command
                                    commandLock.notify();
                                }
                            }
                            running.set(false);
                            continue;
                        }

                        // LPC is dead
                        if (incomingString == null) {
                            LOGGER.log(Level.WARN, "LPC OFF");
                            if (currentCommand != null) {
                                synchronized (commandLock) {
                                    // Set response for pending action, notify to return the response
                                    currentCommand.setResponse("error,LOCAL,LPC is down and socket is closed for: " + currentCommand.commandString.trim());
                                    // Notify to proceed to the next command
                                    commandLock.notify();
                                }
                            }
                            running.set(false);
                            continue;
                        }

                        // LPC alive and responded
                        LOGGER.log(Level.DEBUG, "Got response: '" + incomingString + "'");
                        if (incomingString.startsWith("event")) {
                            handleEvent(incomingString);
                        } else {
                            if (currentCommand != null) {
                                synchronized (commandLock) {
                                    // Got response for pending action, notify to return the response
                                    currentCommand.setResponse(incomingString);
                                    // Got response for pending action, notify to proceed to the next command
                                    queue.remove();
                                    commandLock.notify();
                                }
                            }
                        }

                    } else {
                        // Socket not ready
                        LOGGER.log(Level.WARN, "Socket not ready");
                        running.set(false);
                    }

                }
                LOGGER.log(Level.WARN, "Incoming handler died.");
            }
        }
        );
        incomingHandler.start();
    }

    /**
     * Run for ever , every LPCManagerWatchdogInteravalMills milliseconds and
     * check if incoming / outcoming threads are alive.
     */
    private void startWatchdog() {
        final Integer interval = MTProperties.getInt(PropName.LPCManagerWatchdogInteravalMills, 2000);

        threadsWatchdog = new Thread(new Runnable() {

            @Override
            public void run() {
                while (true) {
                    if (!incomingHandler.isAlive() || !outcomingHandler.isAlive()) {
                        stopMainenanceJobs();

                        if (setupSocket()) {
                            startIncomingHandler();
                            startOutcomingHandler();
                            startMaintenanceJobs();
                            LOGGER.log(Level.DEBUG, "LPC is up and running!");
                        }
                    }

                    try {
                        Thread.sleep(interval);
                    } catch (InterruptedException ex) {
                        LOGGER.log(Level.WARN, "watchdog interrupted while sleeping", ex.getMessage());
                    }
                }
            }
        });
        threadsWatchdog.start();

    }
}
