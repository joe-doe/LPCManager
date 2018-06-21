/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package lpcmanager;

import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author user
 */
public class Main {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        ArrayList<Command> ml = new ArrayList<Command>();
        ml.add(Command.maintenance());
        ml.add(Command.getSysUptime());
        final LPCManager a = LPCManagerProxy.getLPCManager("127.0.0.1", 8889, ml);

        // START BATCH IN THREAD
        new Thread(new Runnable() {
            @Override
            public void run() {
                ArrayList<String> replies = new ArrayList<String>();

                System.out.println("-------THREAD STARTED---------------");
                try {
                    replies = a.sendBatchCommand(Command.test1(), Command.test1(), Command.test1(), Command.test1(), Command.test1());
                } catch (LPCManagerException ex) {
                    System.out.println("1 *************************");
                    Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
                }

                for (String reply : replies) {
                    System.out.println(reply);
                }

            }
        }).start();
        // START BATCH IN THREAD
//        new Thread(new Runnable() {
//            @Override
//            public void run() {
//                ArrayList<String> replies = new ArrayList<String>();
//
//                System.out.println("-------THREAD STARTED---------------");
//                try {
//                    replies = a.sendBatchCommand(Command.screenOn(true), Command.screenOn(true), Command.screenOn(true), Command.screenOn(true), Command.screenOn(false));
//                } catch (LPCManagerException ex) {
//                    System.out.println("1 *************************");
//                    Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
//                }
//
//                for (String reply : replies) {
//                    System.out.println(reply);
//                }
//
//            }
//        }).start();
        // ADD COMMAND TO QUEUE
        System.out.println("\n1");
        for (int i = 0; i < 10; i++) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        System.out.println("FULL RESP: " + a.sendCommand(Command.screenOff()));
                    } catch (LPCManagerException ex) {
                        Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }).start();
        }

//        try {
//            System.out.println("FULL RESP: " + a.sendCommand(Command.screenOn(false)));
//        } catch (LPCManagerException ex) {
//            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
//        }
        // START BATCH IN THREAD WITH ERROR
//        System.out.println("\n2");
//        ArrayList<String> replies = new ArrayList<String>();
//
//        try {
//            replies = a.sendBatchCommand(Command.screenOff(), Command.errorTest(), Command.screenOn());
//            // NEVER PRINT
//            System.out.println("**** REPLY FORM BATCH WITH ERROR ****");
//            for (String reply : replies) {
//                System.out.println(reply);
//            }
//        } catch (LPCManagerException e) {
//            System.out.println("EX");
//        }
//
//        // START BATCH IN THREAD
        new Thread(new Runnable() {
            @Override
            public void run() {
                ArrayList<String> replies = new ArrayList<String>();

                System.out.println("-------THREAD STARTED---------------");
                try {
                    replies = a.sendBatchCommand(Command.test(), Command.test(), Command.test(), Command.test(), Command.test());
                } catch (LPCManagerException ex) {
                    System.out.println("2 *************************");
                    Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
                }
                for (String reply : replies) {
                    System.out.println(reply);
                }
            }

        }).start();
//
//        // ADD COMMAND TO QUEUE
//        System.out.println("\n3");
//        try {
//            a.sendCommand(Command.screenOff());
//        } catch (LPCManagerException ex) {
//            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
//        }
//
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
