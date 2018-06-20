/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.amco.amcoticketmt.utils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;


/**
 *
 * @author user
 */
public class ThreadUtil {

    private static final ExecutorService executor;
    private static final ScheduledExecutorService scheduler;
    private static ScheduledExecutorService schedulerForSend;

    static {
        executor = Executors.newFixedThreadPool(MTProperties.getInt(PropName.ThreadPoolSize));
        scheduler = Executors.newScheduledThreadPool(MTProperties.getInt(PropName.ThreadPoolSize));

        schedulerForSend = Executors.newScheduledThreadPool(3);
    }

    public static void submitJob(Runnable job) {
        executor.submit(job);
    }

    public static ScheduledFuture<?> scheduleForLater(Runnable job, long delay, TimeUnit unit) {
        return scheduler.schedule(job, delay, unit);
    }

    public static ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        return scheduler.scheduleAtFixedRate(command, initialDelay, period, unit);
    }

    public static ScheduledFuture<?> scheduleForSend(Runnable command, long initialDelay, long period, TimeUnit unit) {
        return schedulerForSend.scheduleAtFixedRate(command, initialDelay, period, unit);
    }

    public static void terminateAndWaitSchedulerForSend() throws InterruptedException {
        schedulerForSend.shutdown();
        schedulerForSend.awaitTermination(10, TimeUnit.MINUTES);
        schedulerForSend = null;
    }

    public static void initializeSchedulerForSend() {
        schedulerForSend = Executors.newScheduledThreadPool(3);
    }
}
