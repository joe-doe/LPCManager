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
        final LPCManager a = LPCManager.getInstance();

        // START BATCH IN THREAD
//        new Thread(new Runnable() {
//            @Override
//            public void run() {
                ArrayList<String> replies = new ArrayList<String>();

                System.out.println("-------THREAD STARTED---------------");
                try {
                    replies = a.sendBatchCommand(Command.test1(), Command.test1(), Command.test1(), Command.test1(), Command.test1());
                } catch (LPCManagerException ex) {
                    Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
                }

                for (String reply : replies) {
                    System.out.println(reply);
                }

//            }
//        }).start();

        // ADD COMMAND TO QUEUE
        System.out.println("\n1");
        try {
            System.out.println("FULL RESP: " + a.sendCommand(Command.screenOn()));
        } catch (LPCManagerException ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
        }

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

        // START BATCH IN THREAD
//        new Thread(new Runnable() {
//            @Override
//            public void run() {
//                ArrayList<String> replies = new ArrayList<String>();
//
//                System.out.println("-------THREAD STARTED---------------");
//                try {
//                    replies = a.sendBatchCommand(Command.test(), Command.test(), Command.test(), Command.test(), Command.test());
//                } catch (LPCManagerException ex) {
//                    Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
//                }
//                for (String reply : replies) {
//                    System.out.println(reply);
//                }
//            }
//
//        }).start();

        // ADD COMMAND TO QUEUE
        System.out.println("\n3");
        try {
            a.sendCommand(Command.screenOff());
        } catch (LPCManagerException ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
        }

        // ADD COMMAND TO QUEUE
        System.out.println("\n4");
        try {
            a.sendCommand(Command.errorTest());
        } catch (LPCManagerException e) {
            System.out.println("EX");
        }

    }

}
