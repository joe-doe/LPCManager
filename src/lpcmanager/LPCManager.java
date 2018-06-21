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
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
/**
 *
 * @author user
 */
public class LPCManager {

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
    private Thread maintenanceThread = null;

    /**
     * Use LPCManagerProxy to create new LPCManager
     *
     * @param ip
     * @param port
     * @param maintenanceCommands
     */
    public LPCManager(String ip, Integer port, ArrayList<Command> maintenanceCommands) {

        // read from config
        this.queue = new LinkedBlockingQueue<Command>(12);

        // other initialization
        this.ip = ip;
        this.port = port;
        this.maintenanceCommands = maintenanceCommands;
        this.commandLock = new Object();
        this.responseLock = new Object();

        setupSocket();
        startIncommingHandler();
        startConsumeQueue();
        startMaintenanceThreads();
    }

    public void terminateLPCManager() {
        try {
            out.flush();
            out.close();
            in.close();
            socket.close();
        } catch (IOException ex) {
            Logger.getLogger(LPCManager.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void setupSocket() {
        if (out != null) {
            out.flush();
            out.close();
        }

        if (in != null) {
            try {
                in.close();
            } catch (IOException ex) {
                Logger.getLogger(LPCManager.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        try {
            socket = new Socket(ip, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        } catch (IOException ex) {
            Logger.getLogger(LPCManager.class.getName()).log(Level.SEVERE, null, ex);
        }

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                try {
                    socket.close();
                    System.out.println("The server is shut down!");
                } catch (IOException e) {
                    /* failed */ }
            }
        });
    }

    public String sendCommand(final Command newCommand) throws LPCManagerException {
        System.out.println("------queue size: " + queue.size() + "-------");

//        new Thread(new Runnable() {
//            @Override
//            public void run() {
//                try {
//                System.out.print("Added to queue: " + newCommand.commandString);
        try {
                                queue.add(newCommand);
//            queue.offer(newCommand, 1, TimeUnit.SECONDS);
//        } catch (InterruptedException ex) {
//            System.out.println("WOW");
//            Logger.getLogger(LPCManager.class.getName()).log(Level.SEVERE, null, ex);
//            throw new LPCManagerException("LPC response waiting error!", ex);
//
//        }
                } catch (IllegalStateException ex) {
                    System.out.println("WOW");
                    Logger.getLogger(LPCManager.class.getName()).log(Level.SEVERE, null, ex);
                    throw new LPCManagerException("NO FIT", ex);
                }
            
//
//        }).start();
//        try {
//            System.out.print("Added to queue: " + newCommand.commandString);
//            queue.add(newCommand);
//        } catch (IllegalStateException ex) {
//            System.out.println("WOW");
//            Logger.getLogger(LPCManager.class.getName()).log(Level.SEVERE, null, ex);
//        }
//        System.out.println("------queue size: " + queue.size() + "-------");
//        System.out.print("Added to queue: " + newCommand.commandString);

        // Wait for Incomming Handler thread to wake you up when it has the response
        synchronized (responseLock) {
            try {
                responseLock.wait();
            } catch (InterruptedException ex) {
                Logger.getLogger(LPCManager.class.getName()).log(Level.SEVERE, null, ex);
                throw new LPCManagerException("LPC response waiting error!", ex);
            }
        }

        // We have the response from LPC
        return handleResponse();
    }

    public ArrayList<String> sendBatchCommand(Command... commands) throws LPCManagerException {
        ArrayList<String> replies = new ArrayList<String>();

        for (Command command : commands) {
            replies.add(sendCommand(command));
        }
        return replies;
    }

    private String handleResponse() throws LPCManagerException {
        String lpcResponse = currentCommand.getResponse();

        if (lpcResponse.startsWith("ok,error")) {
            System.out.println("Got ERROR from lpc");
            throw new LPCManagerException("OOPS");
        } else if (lpcResponse.startsWith("event")) {
            System.out.println("Got EVENT from lpc");
        } else if (lpcResponse.startsWith("ok")) {
            String a = lpcResponse.substring(lpcResponse.indexOf(",") + 1);
            System.out.println("ANSWER: " + a);
        }

        return lpcResponse;
    }

    private void handleEvent(String incommingString) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * Iterate maintenance commands list and start a scheduler at a fix rate
     * to send the maintenance command in intervals.
     * <p>
     * When the scheduled job starts, waits for the queue to be empty, then
     * adds the command to the queue. If queue is not empty sleeps for half
     * a second and then retries.
     */
    private void startMaintenanceThreads() {
//        Integer lo = MTProperties.getInt(PropName.TickThread, 0);
        Integer lo = 3;

        if (maintenanceCommands == null) {
            return;
        }
        for (final Command command : maintenanceCommands) {
            try {
                ThreadUtil.scheduleAtFixedRate(new Runnable() {

                    @Override
                    public void run() {
                        AtomicBoolean retry = new AtomicBoolean(true);
                        Integer retries = 0;

                        while (retry.get()) {
                            if (queue.isEmpty()) {
                                try {
                                    sendCommand(command);
//                                    System.out.print("****************** SEND MAINTENEANCE JOB: " + command.commandString);
//                                    System.out.print("****************** success after retries: " + retries + " job: "+command.commandString);
                                    retry.set(false);
                                } catch (LPCManagerException ex) {
                                    Logger.getLogger(LPCManager.class.getName()).log(Level.SEVERE, null, ex);
                                }
                            } else {
                                try {
                                    System.out.print("****************** QUEUE NOT EMTPY. SLEEP FOR HALF A SECOND AND RETRY SEND: " + command.commandString);

                                    Thread.sleep(500);
                                } catch (InterruptedException ex) {
                                    Logger.getLogger(LPCManager.class.getName()).log(Level.SEVERE, null, ex);
                                }
                                retries++;
//                                System.out.print("****************** retries: " + retries + " job: "+command.commandString );
                                if (retries > 3) {
//                                    System.out.print("****************** MAX retries: " + retries+ " job: "+command.commandString );
                                    retry.set(false);
                                }
                            }
                        }
                    }
                }, 100, 2000, TimeUnit.MILLISECONDS);
            } catch (Exception ex) {
                Logger.getLogger(LPCManager.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    private void startConsumeQueue() {
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
                                out.write(currentCommand.commandString);
                                out.flush();

                                // Wait for the reply from the other thread
                                try {
                                    commandLock.wait();
                                } catch (InterruptedException ex) {
                                    Logger.getLogger(LPCManager.class.getName()).log(Level.SEVERE, null, ex);
                                }
                            } catch (InterruptedException ex) {
                                Logger.getLogger(LPCManager.class.getName()).log(Level.SEVERE, null, ex);
                            }
                        }
                    } else {
                        // Socket not ready
                        setupSocket();
                    }
                }
            }
        }
        );

        queueConsumer.start();
    }

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
                            Logger.getLogger(LPCManager.class
                                    .getName()).log(Level.SEVERE, null, ex);
                            continue;
                        }

                        // LPC is dead
                        if (incommingString == null) {
                            System.out.println("LPC OFF");
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
                System.out.println("THIS THREAD DIES HERE");
            }
        });
        incommingHandler.start();
    }

}
