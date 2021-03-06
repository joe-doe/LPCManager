/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package lpcmanager;

import java.util.ArrayList;
import java.util.Hashtable;


/**
 *
 * @author user
 */
public class LPCManagerProxy {

    private static Hashtable<Hashtable<String, Integer>, LPCManager> runningInstances = new Hashtable<Hashtable<String, Integer>, LPCManager>();

    /**
     * Use getLPCManager to get a new LPCManager object. If an LPCManager is
     * already running for the pair (ip, port) return the running instance.
     *
     * @param ip
     * @param port
     * @param maintenanceCommands
     * @return LPCManager object
     */
    public static LPCManager getLPCManager(String ip, Integer port, ArrayList<Command> maintenanceCommands) {
        Hashtable<String, Integer> requestedPair = new Hashtable<String, Integer>();
        requestedPair.put(ip, port);
        
        // Check if running for (ip, port) pair
        if (runningInstances.containsKey(requestedPair)) {
            return runningInstances.get(requestedPair);
        }

        LPCManager newInstance = new LPCManager(ip, port, maintenanceCommands);
        runningInstances.put(requestedPair, newInstance);
        return newInstance;
    }

}
