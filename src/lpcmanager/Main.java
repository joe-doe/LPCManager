/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package lpcmanager;

import java.util.ArrayList;
import java.util.Scanner;
import java.util.concurrent.LinkedBlockingQueue;
import main.java.com.amco.amcoticketmt.utils.MTProperties;
import main.java.com.amco.amcoticketmt.utils.PropName;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;

/**
 *
 * @author user
 */
public class Main {

    private static final org.apache.logging.log4j.Logger LOGGER = LogManager.getLogger(LPCManager.class);

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {

        ArrayList<Command> ml = new ArrayList<Command>();
        ml.add(Command.maintenanceTest().setTimeToLive(10000L));
        ml.add(Command.getSysUptime().setTimeToLive(10000L));
        final LPCManager a = LPCManagerProxy.getLPCManager("127.0.0.1", 8889, ml);

        
        // Keyboard emulation of adding to queue from thread other than main thread
        Thread kb = new Thread(new Runnable() {

            @Override
            public void run() {

                Scanner keyboard = new Scanner(System.in);
                while (true) {
                    String i = keyboard.nextLine();

                    System.out.println(i);

                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                System.out.println("KEYBOARD RESPONSE FOR JOB:" + a.sendCommand(Command.keyboardTest()));
                            } catch (LPCManagerException ex) {
                                LOGGER.log(Level.ERROR, "KEYBOARD LPC EXCEPTION", ex.getMessage());
                            }
                        }
                    }).start();
                }
            }
        });
        kb.start();

        System.out.println("\n++++++++++++++++++++++ CP TEST +++++++++++++++++++");
        // Send 3 commands sequentialy 
        for (int i = 0; i < 3; i++) {
            try {
                System.out.println("TEST FULL RESP: " + a.sendCommand(Command.test1().setTimeToLive(10000L)));
            } catch (LPCManagerException ex) {
                LOGGER.log(Level.ERROR, "LPC EXCEPTION");
            }
        }
        
        System.out.println("\n++++++++++++++++++++++ CP TEST 2 +++++++++++++++++++");
        // Start new Thread for every command
        for (int i = 0; i < 10; i++) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        System.out.println("TEST 2 FULL RESP: " + a.sendCommand(Command.test2()));
                    } catch (LPCManagerException ex) {
                        System.out.println("---===== NOT EXECUTED ========-----");
                        LOGGER.log(Level.ERROR, ex.getMessage());
                    }
                }
            }).start();
        }

        System.out.println("\n++++++++++++++++++++++ CP TEST 3 +++++++++++++++++++");
        // New thread with multiple commands
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String b = a.sendCommand(Command.test3());
                    String c = a.sendCommand(Command.test3());
                    String d = a.sendCommand(Command.test3());
                    String e = a.sendCommand(Command.test3());
                    String f = a.sendCommand(Command.test3());
                } catch (LPCManagerException ex) {
                    LOGGER.log(Level.ERROR, ex);
                }
            }
        }).start();
        
        
        System.out.println("\n++++++++++++++++++++++ CP TEST 4 +++++++++++++++++++");
        // Send batch command
        ArrayList<String> replies = new ArrayList<String>();

        try {
            replies = a.sendBatchCommand(Command.test4(), Command.test4(), Command.test4(), Command.test4(), Command.test4(), Command.test4());
        } catch (LPCManagerException ex) {
            LOGGER.log(Level.ERROR, "BATCH LPC EXCEPTION");
        }

        for (String reply : replies) {
            System.out.println(reply);
        }

        System.out.println("\n++++++++++++++++++++++ CP TEST 5 +++++++++++++++++++");
        // START BATCH IN THREAD
        new Thread(new Runnable() {
            @Override
            public void run() {
                ArrayList<String> replies = new ArrayList<String>();

                System.out.println("-------THREAD STARTED---------------");
                try {
                    replies = a.sendBatchCommand(Command.test5(), Command.test5(), Command.test5(), Command.test5(), Command.test5(), Command.test5());
                } catch (LPCManagerException ex) {
                    System.out.println("1 *************************");
                    LOGGER.log(Level.ERROR, ex);
                }

                for (String reply : replies) {
                    System.out.println(reply);
                }
            }
        }).start();

        
        System.out.println("\n++++++++++++++++++++++ CP BATCH WITH ERROR  +++++++++++++++++++");
        // START BATCH WITH ERROR
        ArrayList<String> replies2 = new ArrayList<String>();

        try {
            replies2 = a.sendBatchCommand(Command.screenOff(), Command.errorTest(), Command.screenOn());
            // NEVER PRINT
            System.out.println("**** REPLY FORM BATCH WITH ERROR ****");
            for (String reply : replies2) {
                System.out.println(reply);
            }
        } catch (LPCManagerException ex) {
            System.out.println("BATCH COMMAND FAILED: "+ ex.getMessage());
        }

        System.out.println("\n++++++++++++++++++++++ CP SIMPLE ADD +++++++++++++++++++");
        // ADD COMMAND TO QUEUE
        try {
            a.sendCommand(Command.screenOff());
        } catch (LPCManagerException ex) {
            LOGGER.log(Level.ERROR, ex);
        }


        System.out.println("\n++++++++++++++++++++++ CP 7 +++++++++++++++++++");
//        // ADD COMMAND TO QUEUE
//        System.out.println("\n4");
//        try {
//            a.sendCommand(Command.errorTest());
//        } catch (LPCManagerException e) {
//            System.out.println("EX");
//        }
//










//        LPCManager b = LPCManagerProxy.getLPCManager("127.0.0.1", 8889);
//
//        if (a == b) {
//            System.out.println("SAME");
//        } else {
//            System.out.println("NOT SAME");
//        }
    }

}
