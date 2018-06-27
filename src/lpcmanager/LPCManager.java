package lpcmanager;

import main.java.com.amco.amcoticketmt.utils.MTProperties;
import main.java.com.amco.amcoticketmt.utils.PropName;
import main.java.com.amco.amcoticketmt.utils.ThreadUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Date;
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
 *
 * @author user
 */
public class LPCManager {

    private static final Logger LOGGER = LogManager.getLogger(LPCManager.class);

    private final int port;
    private final String ip;
    private final Object commandLock;
    private final Object responseLock;

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
        this.responseLock = new Object();

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
            LOGGER.log(Level.FATAL, "Failed to terminate LPCManager", ex);
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
                LOGGER.log(Level.ERROR, ex);
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

    public synchronized String sendCommand1(final Command newCommand) throws LPCManagerException{
        class Task implements Callable<String> {

            @Override
            public String call() throws LPCManagerException {
                boolean successfullyAddedToQueue = false;

                if (socket.isClosed()) {
                    LOGGER.log(Level.ERROR, "LPC Not accepting new entries; socket is down");
//                        throw new LPCManagerException("LPC Not accepting new entries; socket is down");
                }

                try {
                    successfullyAddedToQueue = queue.add(newCommand);
                } catch (IllegalStateException ex) {
                        throw new LPCManagerException("LPC command: " + newCommand.commandString + " cannot be added to queue");
                }
                if (successfullyAddedToQueue == false) {
                        throw new LPCManagerException("LPC command: " + newCommand.commandString + " cannot be added to queue");
                }

                LOGGER.log(Level.INFO, "Added to queue: " + newCommand.commandString);

                String resp = newCommand.getResponse();
                return resp;
            }
        }
        ExecutorService exService = Executors.newSingleThreadExecutor();
        FutureTask<String> futureTask = new FutureTask<String>(new Task());
        exService.execute(futureTask);
        String reply = null;
        try {
            // We have the response from LPC
            reply = handleResponse(futureTask.get());
        } catch (InterruptedException ex) {
            LOGGER.log(Level.FATAL, "InterruptedException: "+ex.getMessage());
        } catch (ExecutionException ex) {
            LOGGER.log(Level.FATAL, "ExecutionException"+ex.getMessage());
        } catch (LPCManagerException ex) {
            LOGGER.log(Level.FATAL, "LPCManagerException"+ex.getMessage());
            throw new LPCManagerException("LPC command: " + newCommand.commandString + " not executed");
        }
        return reply;

    }

    /**
     * Try to add new Command to queue and when succeeded, wait for the
     * reply thread to read the reply. Then handle the reply to sender.
     *
     * @param newCommand Command to add to queue
     * @return Response of the LPC server
     *
     * @throws LPCManagerException If command is not added to queue you don't
     * have to wait for a a reply.
     */
    public synchronized String sendCommand(final Command newCommand) throws LPCManagerException {
        boolean successfullyAddedToQueue = false;

        if (socket.isClosed()) {
            LOGGER.log(Level.ERROR, "LPC Not accepting new entries; socket is down");
            throw new LPCManagerException("LPC Not accepting new entries; socket is down");
        }

        try {
            successfullyAddedToQueue = queue.add(newCommand);
        } catch (IllegalStateException ex) {
            throw new LPCManagerException("LPC command: " + newCommand.commandString + " cannot be added to queue");
        }
        if (successfullyAddedToQueue == false) {
            throw new LPCManagerException("LPC command: " + newCommand.commandString + " cannot be added to queue");
        }

        LOGGER.log(Level.INFO, "Added to queue: " + newCommand.commandString);

        String resp = newCommand.getResponse();

        // We have the response from LPC
        return handleResponse(resp);

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
    public ArrayList<String> sendBatchCommand(Command... commands) throws LPCManagerException {
        ArrayList<String> replies = new ArrayList<String>();

        for (Command command : commands) {
            replies.add(sendCommand1(command));
        }
        return replies;
    }

    /**
     * Get the response written to currentCommand and parse it.
     * When it is an 'error' throw LPCManagerException.
     * When it is 'ok' return the reply written in currentCommand.
     * Events are handled right after the data arrive to
     * socket (see 'startIncomingHandler')
     *
     * @return The reply from LPC server without the starting 'ok'
     * if the reply has data (comma separated values)
     *
     * @throws LPCManagerException If LPC server reply starts with error
     */
    private String handleResponse(String lpcResponse) throws LPCManagerException {
//        String lpcResponse = currentCommand.getResponse();

        if (lpcResponse.startsWith("ok,error")) {
            LOGGER.log(Level.ERROR, "Got ERROR from lpc");
            throw new LPCManagerException("OOPS");
        } else if (lpcResponse.startsWith("LPC is down")) {
            LOGGER.log(Level.ERROR, "Got reply 'LPC is down'");
            throw new LPCManagerException("LPC is down");
        } else if (lpcResponse.startsWith("ok")) {
            String a = lpcResponse.substring(lpcResponse.indexOf(",") + 1);
            LOGGER.log(Level.INFO, "ANSWER: " + a);
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
        Integer interval = MTProperties.getInt(PropName.LPCManagerMainenanceJobs, 0);

        // Do nothing if no maintenance commands provided during constructing
        // this object.
        if (maintenanceCommands == null) {
            LOGGER.log(Level.WARN, "No mainenance jobs provided");
            return;
        }

        // Iterate maintenance command and start a new scheduled job for each
        // one of them.
        for (final Command command : maintenanceCommands) {
            LOGGER.log(Level.INFO, "Dispatching scheduled job for: " + command.commandString);

            ScheduledFuture<?> task = scheduler.scheduleWithFixedDelay(new Runnable() {

                @Override
                public void run() {
                    AtomicBoolean retry = new AtomicBoolean(true);
                    Integer retries = 0;

                    System.out.println("");
                    System.out.println("" + (new Date()).toString());
                    System.out.println("");
//                    while (retry.get()) {
                    if (queue.isEmpty()) {
                        try {
                            sendCommand(command);
                            LOGGER.log(Level.INFO, "Send mainenance job: " + command.commandString + " after retries: " + retries);
                            retry.set(false);
                        } catch (LPCManagerException ex) {
                            LOGGER.log(Level.INFO, "Add maintenance job: " + command.commandString + " to queue, failed");
                        }
//                        } 
                    } else {
                        System.out.println("*************** DROP: " + command.commandString);

                    }
                    System.out.print("DONE JOB:" + command.commandString);
                }
            }, 1, 10, TimeUnit.SECONDS);
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
     * Thread responsible to take Commands from queue and execute them.
     * When it dispatched the command it waits fro Thread startIncomingHandler
     * to notify that there is a response and then it proceeds to the next
     * Command in queue
     */
    private void startOutcomingHandler() {

        outcomingHandler = new Thread(new Runnable() {

            @Override
            public void run() {
                AtomicBoolean running = new AtomicBoolean(true);

                while (running.get()) {
                    if (!socket.isClosed()) {
                        // Socket ready
                        synchronized (commandLock) {
                            try {
                                // Proccess queue (block)
                                currentCommand = queue.take();

                                LOGGER.log(Level.INFO, "Send command from queue:" + currentCommand.commandString);

                                out.write(currentCommand.commandString);
                                out.flush();

                                // Wait for the reply from the other thread
                                try {
                                    commandLock.wait();
                                } catch (InterruptedException ex) {
                                    LOGGER.log(Level.WARN, "Interrupted while waiting for reply for command: " + currentCommand.commandString, ex);
                                    running.set(false);
                                }
                            } catch (InterruptedException ex) {
                                LOGGER.log(Level.WARN, "Interrupted while waiting in empty queue", ex);
                                running.set(false);
                            }
                        }
                    } else {
                        // Socket not ready
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
     * sets the response to currentCommand and notifies sendCommand that has a
     * response and StartOutcomingHandler that it can proceed with the next
     * Command
     */
    private void startIncomingHandler() {
        incomingHandler = new Thread(new Runnable() {

            @Override
            public void run() {
                String incomingString;
                AtomicBoolean running = new AtomicBoolean(true);

                while (running.get()) {
                    if (!socket.isClosed()) {
                        // Socket ready
                        try {
                            // Proccess incoming
                            incomingString = in.readLine();

                        } catch (IOException ex) {
                            LOGGER.log(Level.ERROR, "Reading stream produce I/O error", ex);
                            synchronized (commandLock) {
                                synchronized (responseLock) {
                                    // Set response for pending action, notify to return the response
                                    currentCommand.setResponse("LPC is down while trying to read from socket for: " + currentCommand.commandString);
                                    responseLock.notify();
                                }
                                // Notify to proceed to the next command
                                commandLock.notify();
                            }
                            running.set(false);
                            continue;
                        }

                        // LPC is dead
                        if (incomingString == null) {
                            LOGGER.log(Level.WARN, "LPC OFF");
                            synchronized (commandLock) {
                                synchronized (responseLock) {
                                    // Set response for pending action, notify to return the response
                                    currentCommand.setResponse("LPC is down and socket is closed for: " + currentCommand.commandString);
                                    responseLock.notify();
                                }
                                // Notify to proceed to the next command
                                commandLock.notify();
                            }
                            running.set(false);
                            continue;
                        }

                        // LPC alive and responded
                        LOGGER.log(Level.INFO, "Got response: '" + incomingString + "'");
                        if (incomingString.startsWith("event")) {
                            handleEvent(incomingString);
                        } else {
                            synchronized (commandLock) {
                                synchronized (responseLock) {
                                    // Got response for pending action, notify to return the response
                                    currentCommand.setResponse(incomingString);
                                    responseLock.notify();
                                }
                                // Got response for pending action, notify to proceed to the next command
                                commandLock.notify();
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
     * Run for ever , every 1 second and check if incoming / outcoming
     * threads are alive.
     */
    private void startWatchdog() {
        threadsWatchdog = new Thread(new Runnable() {

            @Override
            public void run() {
                while (true) {
                    if (!incomingHandler.isAlive() || !outcomingHandler.isAlive()) {

                        if (setupSocket()) {
                            stopMainenanceJobs();
                            startIncomingHandler();
                            startOutcomingHandler();
                            startMaintenanceJobs();
                        }
                    }

                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ex) {
                        LOGGER.log(Level.WARN, "watchdog interrupted while sleeping", ex);
                    }
                }
            }
        });
        threadsWatchdog.start();

    }
}
