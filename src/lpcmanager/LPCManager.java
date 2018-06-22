package lpcmanager;

import com.amco.amcoticketmt.utils.MTProperties;
import com.amco.amcoticketmt.utils.PropName;
import com.amco.amcoticketmt.utils.ThreadUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Level;

/**
 * Class that is responsible to open a socket with LPC server and use it
 * to send and receive data. 
 * 
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
    private ScheduledFuture<?> maintenanceScheduler = null;

    private Command currentCommand = null;
    private Thread incommingHandler = null;
    private Thread queueConsumer = null;

    /**
     * Use LPCManagerProxy to create new LPCManager
     *
     * @param ip IP Address of the LPC server
     * @param port Port of the LPC server
     * @param maintenanceCommands List of maintenance commands 
     */
    public LPCManager(String ip, Integer port, ArrayList<Command> maintenanceCommands) {

        this.queue = new LinkedBlockingQueue<Command>(MTProperties.getInt(PropName.LPCQueueSize, 5));
        this.ip = ip;
        this.port = port;
        this.maintenanceCommands = maintenanceCommands;
        this.commandLock = new Object();
        this.responseLock = new Object();

        setupSocket();
        startIncommingHandler();
        startOutcommingHandler();
        startMaintenanceJobs();
    }

    /**
     * Do necessary stuff to terminate LPCManager.
     */
    public void terminateLPCManager() {
        try {
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
     */
    private void setupSocket() {
        if (out != null) {
            out.flush();
            out.close();
        }

        if (in != null) {
            try {
                in.close();
            } catch (IOException ex) {
                LOGGER.log(Level.INFO, ex);
            }
        }

        try {
            socket = new Socket(ip, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        } catch (IOException ex) {
            LOGGER.log(Level.FATAL, "Failed to setup socket", ex);
        }

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                try {
                    socket.close();
                    System.out.println("The server is shut down!");
                } catch (IOException ex) {
                    LOGGER.log(Level.ERROR, "Failed to shut down socket", ex);
                }
            }
        });
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
    public String sendCommand(final Command newCommand) throws LPCManagerException {
        System.out.println("------queue size: " + queue.size() + "-------");
        boolean successfullyAddedToQueue = false;

        // Try to add  the specified element into this queue, waiting up 
        // to the specified wait time if necessary for space to become available.
        try {
            successfullyAddedToQueue = queue.offer(newCommand, 1, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            LOGGER.log(Level.ERROR, "Interrupted while waiting to insert to queue", ex);
            throw new LPCManagerException("LPC command: " + newCommand.commandString + " not added to queue", ex);

        }

        if (successfullyAddedToQueue == false) {
            throw new LPCManagerException("LPC command: " + newCommand.commandString + " cannot be added to queue");
        }

//        System.out.println("------queue size: " + queue.size() + "-------");
//        System.out.print("Added to queue: " + newCommand.commandString);
        // New command successfuly added to queue.
        // Wait for Incomming Handler thread to wake you up when it has the response.
        synchronized (responseLock) {
            try {
                responseLock.wait();
            } catch (InterruptedException ex) {
                LOGGER.log(Level.ERROR, "Interrupted while waiting for response", ex);
                throw new LPCManagerException("LPC response waiting error!", ex);
            }
        }

        // We have the response from LPC
        return handleResponse();
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
            replies.add(sendCommand(command));
        }
        return replies;
    }

    /**
     * Get the response written to currentCommand and parse it.
     * When it is an 'error' throw LPCManagerException.
     * When it is 'ok' return the reply written in currentCommand.
     * Events are handled right after the data arrive to
     * socket (see 'startIncommingHandler')
     *
     * @return The reply from LPC server without the starting 'ok'
     * if the reply has data (comma separated values)
     *
     * @throws LPCManagerException If LPC server reply starts with error
     */
    private String handleResponse() throws LPCManagerException {
        String lpcResponse = currentCommand.getResponse();

        if (lpcResponse.startsWith("ok,error")) {
            System.out.println("Got ERROR from lpc");
            throw new LPCManagerException("OOPS");
        } else if (lpcResponse.startsWith("ok")) {
            String a = lpcResponse.substring(lpcResponse.indexOf(",") + 1);
            System.out.println("ANSWER: " + a);
        }

        return lpcResponse;
    }

    /**
     * An event string came from socket. Handle it properly.
     * 
     * @param incommingString What came from LPC server
     */
    private void handleEvent(String incommingString) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * Iterate maintenance commands list and start a scheduler at a fix rate
     * to send the maintenance command in intervals.
     * 
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

            ThreadUtil.scheduleAtFixedRate(new Runnable() {

                @Override
                public void run() {
                    AtomicBoolean retry = new AtomicBoolean(true);
                    Integer retries = 0;

                    while (retry.get()) {
                        if (queue.isEmpty()) {
                            try {
                                sendCommand(command);
                                LOGGER.log(Level.INFO, "Send mainenance job: " + command.commandString + " after retries: " + retries);
                                retry.set(false);
                            } catch (LPCManagerException ex) {
                                LOGGER.log(Level.INFO, "Add maintenance job: " + command.commandString + " to queue, failed");
                            }
                        } else {
                            try {
                                LOGGER.log(Level.INFO, "Queue not empty. Try again in half a second to send: " + command.commandString);
                                Thread.sleep(500);
                            } catch (InterruptedException ex) {
                                LOGGER.log(Level.WARN, "Interrupted while waiting for hlaf a second to retry to send mainenance job: " + command.commandString, ex);
                            }
                            retries++;
                            // If queue is not empty, retry 3 times and then give up.
                            if (retries > 2) {
                                retry.set(false);
                            }
                        }
                    }
                }
            }, 1, interval, TimeUnit.SECONDS);
        }
    }

    /**
     * Thread responsible to take Commands from queue and execute them.
     * When it dispatched the command it waits fro Thread startIncommingHandler
     * to notify that there is a response and then it proceeds to the next 
     * Command in queue
     */
    private void startOutcommingHandler() {
        queueConsumer = new Thread(new Runnable() {

            @Override
            public void run() {
                AtomicBoolean running = new AtomicBoolean(true);

                while (running.get()) {
                    if (!socket.isClosed()) {
                        // Socket ready

                        // Emulate delay for picking up next item from queue
//                        try {
//                            Thread.sleep(1300);
//                        } catch (InterruptedException ex) {
//                            Logger.getLogger(Command.class.getName()).log(Level.SEVERE, null, ex);
//                        }
                        synchronized (commandLock) {
                            try {
                                // Proccess queue (block)
                                currentCommand = queue.take();

                                System.out.print("Send command from queue:" + currentCommand.commandString);
                                LOGGER.log(Level.INFO, "Send command from queue:" + currentCommand.commandString);

                                out.write(currentCommand.commandString);
                                out.flush();

                                // Wait for the reply from the other thread
                                try {
                                    commandLock.wait();
                                } catch (InterruptedException ex) {
                                    LOGGER.log(Level.WARN, "Interrupted while waiting for reply for command: " + currentCommand.commandString, ex);
                                }
                            } catch (InterruptedException ex) {
                                LOGGER.log(Level.WARN, "Interrupted while waiting in empty queue", ex);
                            }
                        }
                    } else {
                        // Socket not ready
                        LOGGER.log(Level.WARN, "Socket not ready");
                        setupSocket();
                    }
                }
            }
        }
        );

        queueConsumer.start();
    }

    /**
     * Thread that is constantly listening for incoming traffic from socket.
     * If it's an event then it handles it to eventHandler otherwise it
     * sets the response to currentCommand and notifies sendCommand that has a
     * response and StartOutcomingHandler that it can proceed with the next Command
     */
    private void startIncommingHandler() {
        incommingHandler = new Thread(new Runnable() {

            @Override
            public void run() {
                String incommingString;
                AtomicBoolean running = new AtomicBoolean(true);

                while (running.get()) {
                    if (!socket.isClosed()) {
                        // Socket ready
                        try {
                            // Proccess incomming
                            incommingString = in.readLine();

                        } catch (IOException ex) {
                            LOGGER.log(Level.ERROR, "Reading stream produce I/O error", ex);
                            continue;
                        }

                        // LPC is dead
                        if (incommingString == null) {
                            LOGGER.log(Level.WARN, "LPC OFF");
                            running.set(false);
                            continue;
                        }

                        // LPC alive and responded
                        System.out.println("Got response: '" + incommingString + "'");
                        if (incommingString.startsWith("event")) {
                            handleEvent(incommingString);
                        } else {
                            synchronized (commandLock) {
                                synchronized (responseLock) {
                                    // Got response for pending action, notify to return the response
                                    currentCommand.setResponse(incommingString);
                                    responseLock.notify();
                                }
                                // Got response for pending action, notify to proceed to the next command
                                commandLock.notify();
                            }
                        }

                    } else {
                        // Socket not ready
                        setupSocket();
                    }

                }
                LOGGER.log(Level.WARN, "Incomming handler died.");
                System.out.println("THIS THREAD DIES HERE");
            }
        });
        incommingHandler.start();
    }

}
