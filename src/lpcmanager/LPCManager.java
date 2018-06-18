package lpcmanager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.concurrent.LinkedBlockingQueue;
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

    private static final LPCManager INSTANCE;
    private final int port;
    private final String ip;
    private final Object commandLock;
    private final Object responseLock;

    private Socket socket = null;
    private PrintWriter out = null;
    private BufferedReader in = null;

    private LinkedBlockingQueue<Command> queue = null;

    private Command currentCommand = null;
    private Thread incommingHandler = null;
    private Thread queueConsumer = null;

    static {
        try {
            INSTANCE = new LPCManager();
        } catch (Exception ex) {
            throw new ExceptionInInitializerError("LPC failed to initialize: " + ex);
        }
    }

    private LPCManager() {

        ip = "127.0.0.1";
        port = 8889;
        queue = new LinkedBlockingQueue<Command>(2);
        commandLock = new Object();
        responseLock = new Object();

        setupSocket();
        startIncommingHandler();
        startConsumeQueue();
    }

    public static LPCManager getInstance() {
        return INSTANCE;
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

    public String sendCommand(Command newCommand) throws LPCManagerException {
        try {
            queue.put(newCommand);
        } catch (InterruptedException ex) {
            Logger.getLogger(LPCManager.class.getName()).log(Level.SEVERE, null, ex);
        }
        System.out.println("------queue size: " + queue.size() + "-------");
        System.out.print("Added to queue: " + newCommand.commandString);


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

    private void startConsumeQueue() {
        queueConsumer = new Thread(new Runnable() {

            @Override
            public void run() {
                AtomicBoolean running = new AtomicBoolean(true);

                while (running.get()) {
                    if (!socket.isClosed()) {
                        // Socket ready

                        synchronized (commandLock) {
                            // Proccess queue
                            if ((currentCommand = queue.poll()) != null) {
                                System.out.print("Send command from queue:" + currentCommand.commandString);
                                out.write(currentCommand.commandString);
                                out.flush();

                                // Wait for the reply from the other thread
                                try {
                                    commandLock.wait();
                                } catch (InterruptedException ex) {
                                    Logger.getLogger(LPCManager.class.getName()).log(Level.SEVERE, null, ex);
                                }
                            }
                        }
                        // Do maintenance jobs
                        //TODO
                        // Sleep for half a second
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException ex) {
                            Logger.getLogger(LPCManager.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    } else {
                        // Socket not ready
                        setupSocket();
                    }
                }
            }
        });

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
                            Logger.getLogger(LPCManager.class.getName()).log(Level.SEVERE, null, ex);
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
