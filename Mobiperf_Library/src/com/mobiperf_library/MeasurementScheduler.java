/* Copyright 2013 RobustNet Lab, University of Michigan. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mobiperf_library;

import java.io.IOException;
import java.util.Calendar;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.Date;

import java.util.HashMap;
import java.util.List;

import java.util.Vector;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.PriorityBlockingQueue;

import com.mobiperf_library.Config;
import com.mobiperf_library.MeasurementTask;
import com.mobiperf_library.UpdateIntent;

import com.mobiperf_library.util.Logger;
import com.mobiperf_library.util.PhoneUtils;
import com.mobiperf_library.exceptions.MeasurementSkippedException;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.Parcelable;
import android.os.RemoteException;

public class MeasurementScheduler extends Service{

  public enum TaskStatus {//TODO changing paused to scheduled?
    FINISHED, PAUSED, CANCELLED, SCHEDULED,RUNNING, NOTFOUND
  }

  private ExecutorService measurementExecutor;
  private BroadcastReceiver broadcastReceiver;
  private boolean pauseRequested = true;
  private boolean stopRequested = false;
  public boolean isSchedulerStarted =  false;


  private Checkin checkin;
  private long checkinIntervalSec;
  private long checkinRetryIntervalSec;
  private int checkinRetryCnt;
  private CheckinTask checkinTask;
  private Calendar lastCheckinTime;


  private PhoneUtils phoneUtils;

  private PendingIntent measurementIntentSender;
  private PendingIntent checkinIntentSender;
  private PendingIntent checkinRetryIntentSender;

  private AlarmManager alarmManager;
  private volatile ConcurrentHashMap<String, TaskStatus> tasksStatus;
  private volatile PriorityBlockingQueue<MeasurementTask> mainQueue;
  private volatile PriorityBlockingQueue<MeasurementTask> waitingTasksQueue;
  private volatile ConcurrentHashMap<MeasurementTask, Future<MeasurementResult[]>> pendingTasks;
  private volatile Date currentTaskStartTime;
  private volatile MeasurementTask currentTask;

  private volatile ConcurrentHashMap<String, String> idToClientKey;

  private HashMap<String, Messenger> mClients;
  private Messenger messenger;
  
  // context collector class
  


  @Override
  public void onCreate() {

    Logger.d("MeasurementScheduler -> onCreate called");
    PhoneUtils.setGlobalContext(this.getApplicationContext());
    
    
    phoneUtils = PhoneUtils.getPhoneUtils();
    phoneUtils.registerSignalStrengthListener();

    this.measurementExecutor = Executors.newSingleThreadExecutor();
    this.mainQueue=new PriorityBlockingQueue<MeasurementTask>(Config.MAX_TASK_QUEUE_SIZE,new TaskComparator());
    this.waitingTasksQueue=new PriorityBlockingQueue<MeasurementTask>(Config.MAX_TASK_QUEUE_SIZE,new WaitingTasksComparator());
    this.pendingTasks=new ConcurrentHashMap<MeasurementTask, Future<MeasurementResult[]>>();
    this.tasksStatus=new ConcurrentHashMap<String, MeasurementScheduler.TaskStatus>();
    this.alarmManager = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);

    this.idToClientKey= new ConcurrentHashMap<String, String>();

    this.mClients = new HashMap<String, Messenger>();
    messenger = new Messenger(new APIRequestHandler(this));
    
    this.setCurrentTask(null);


    this.checkin = new Checkin(this);
    this.checkinRetryIntervalSec = Config.MIN_CHECKIN_RETRY_INTERVAL_SEC;
    this.checkinRetryCnt = 0;
    this.checkinTask = new CheckinTask();




    this.pauseRequested = true;
    this.stopRequested = false;
    phoneUtils = PhoneUtils.getPhoneUtils();

    // Register activity specific BroadcastReceiver here    
    IntentFilter filter = new IntentFilter();
    //    filter.addAction(UpdateIntent.PREFERENCE_ACTION);
    //    filter.addAction(UpdateIntent.MSG_ACTION);
    filter.addAction(UpdateIntent.CHECKIN_ACTION);
    filter.addAction(UpdateIntent.CHECKIN_RETRY_ACTION);
    filter.addAction(UpdateIntent.MEASUREMENT_ACTION);
    filter.addAction(UpdateIntent.MEASUREMENT_PROGRESS_UPDATE_ACTION);

    broadcastReceiver = new BroadcastReceiver() {

      @Override
      public void onReceive(Context context, Intent intent) {
        Logger.d(intent.getAction()+" RECEIVED");
        if (intent.getAction().equals(UpdateIntent.MEASUREMENT_ACTION)) {
          handleMeasurement();
        }else if (intent.getAction().equals(UpdateIntent.MEASUREMENT_PROGRESS_UPDATE_ACTION)) {
          String taskid=intent.getStringExtra(UpdateIntent.TASKID_PAYLOAD);
          String taskKey=intent.getStringExtra(UpdateIntent.TASKKEY_PAYLOAD);
          Logger.e(intent.getStringExtra(UpdateIntent.TASK_STATUS_PAYLOAD)+" "+taskid + " " + taskKey);
          if(intent.getStringExtra(UpdateIntent.TASK_STATUS_PAYLOAD).equals(Config.TASK_FINISHED)){
            tasksStatus.put( taskid,TaskStatus.FINISHED);
            Parcelable[] results = intent.getParcelableArrayExtra(UpdateIntent.RESULT_PAYLOAD);
            if ( results != null ) {
              sendResultMessage(results, taskKey, taskid);
            }
            handleMeasurement();
          }
          else if(intent.getStringExtra(UpdateIntent.TASK_STATUS_PAYLOAD).equals(Config.TASK_PAUSED)){
            tasksStatus.put( taskid,TaskStatus.PAUSED);
          }
          else if(intent.getStringExtra(UpdateIntent.TASK_STATUS_PAYLOAD).equals(Config.TASK_STOPPED)){
            tasksStatus.put( taskid,TaskStatus.SCHEDULED);
          }
          else if(intent.getStringExtra(UpdateIntent.TASK_STATUS_PAYLOAD).equals(Config.TASK_CANCELED)){
            //TODO notifying users? The task is cancelled if endtime<currenttime
            // Hongyi: Sure
            tasksStatus.put( taskid,TaskStatus.CANCELLED);
            Parcelable[] results = intent.getParcelableArrayExtra(UpdateIntent.RESULT_PAYLOAD);
            if ( results != null ) {
              sendResultMessage(results, taskKey, taskid);
            }
          }
          else if (intent.getStringExtra(UpdateIntent.TASK_STATUS_PAYLOAD).equals(Config.TASK_STARTED)) {
            tasksStatus.put( taskid,TaskStatus.RUNNING);
          }
          else if(intent.getStringExtra(UpdateIntent.TASK_STATUS_PAYLOAD).equals(Config.TASK_RESUMED)){
            tasksStatus.put( taskid,TaskStatus.RUNNING);
            //TODO starttime can not be set to current time, seems we need to keep the (pausetime-starttime) for preemptable tasks
          }

        }else if(intent.getAction().equals(UpdateIntent.CHECKIN_ACTION) ||
            intent.getAction().equals(UpdateIntent.CHECKIN_RETRY_ACTION)) {
          Logger.d("Checkin intent received");
          handleCheckin();
        }


      }
    };
    this.registerReceiver(broadcastReceiver, filter);
  }



  @Override 
  public int onStartCommand(Intent intent, int flags, int startId)  {
    Logger.d("MeasurementScheduler -> onStartCommand, isSchedulerStarted = " + isSchedulerStarted);
    // Start up the thread running the service. Using one single thread for all requests
    Logger.i("starting scheduler");

    // this line enables check in
    setCheckinInterval(Config.MIN_CHECKIN_INTERVAL_SEC);

    if (!isSchedulerStarted) {
      this.resume();
      /* There is no onStop() for services. The service is only stopped when the user exits the
       * application. So don't worry about setting isSchedulerStarted to false.*/
      isSchedulerStarted = true;
    }
    return START_STICKY;
  }

  //return the current running task
  public synchronized MeasurementTask getCurrentTask(){
    return currentTask; 
  }

  //set current running task
  public synchronized void setCurrentTask(MeasurementTask newtask){

    if(newtask==null){
      currentTask=null;
      Logger.d("Setting Current task -> null");
    }
    else{
//      Logger.d("Setting Current task: "+newtask.generateTaskID());
      Logger.d("Setting Current task: " + newtask.getTaskId());
      currentTask=newtask.clone();
    }

  }

  //set current running task start time
  private synchronized void setCurrentTaskStartTime(Date starttime){
    currentTaskStartTime=starttime;
  }


  private synchronized Date getCurrentTaskStartTime(){//return the current running task (TODO synchronized?)
    Date starttime;
    starttime=currentTaskStartTime;
    return starttime;
  }

  public HashMap<String, Messenger> getClientsMap() {
    return mClients;
  }
  
  private synchronized void handleMeasurement() {
    try{
      Logger.e("In handleMeasurement");
      MeasurementTask task = mainQueue.peek();
      while(task!=null && task.timeFromExecution() <= 0){
        mainQueue.poll();
        Logger.e(task.getDescription().key+" added to waiting list");
        waitingTasksQueue.add(task);
        task=mainQueue.peek();
      }
      if (waitingTasksQueue.size()!=0) {
        Logger.e("waiting list size= "+waitingTasksQueue.size());
        MeasurementTask ready=waitingTasksQueue.poll();

        MeasurementDesc desc = ready.getDescription();
        long newStartTime = desc.startTime.getTime() + (long) desc.intervalSec * 1000;

        // Add a clone of the task if it's still valid, it does not change the taskID (hashCode) 
        if (newStartTime < desc.endTime.getTime() &&
            (desc.count == MeasurementTask.INFINITE_COUNT || desc.count > 1)) {
          MeasurementTask newTask = ready.clone();
          if (desc.count != MeasurementTask.INFINITE_COUNT) {
            newTask.getDescription().count--;
          }
          newTask.getDescription().startTime.setTime(newStartTime);
          tasksStatus.put(newTask.getTaskId(), TaskStatus.SCHEDULED);
          mainQueue.add(newTask);
        }

        if(ready.getDescription().endTime.before(new Date())){
          Intent intent = new Intent();
          intent.setAction(UpdateIntent.MEASUREMENT_PROGRESS_UPDATE_ACTION);
          intent.putExtra(UpdateIntent.TASK_STATUS_PAYLOAD, Config.TASK_CANCELED);
          MeasurementResult[] tempResults = MeasurementResult.getFailureResult(ready, 
            new CancellationException("Task cancelled!"));
          intent.putExtra(UpdateIntent.RESULT_PAYLOAD, tempResults);
          intent.putExtra(UpdateIntent.TASKID_PAYLOAD, ready.getTaskId());
          intent.putExtra(UpdateIntent.TASKKEY_PAYLOAD, ready.getKey());
          MeasurementScheduler.this.sendBroadcast(intent);
          handleMeasurement();
        }else{
          Logger.e(ready.getDescription().key+" is gonna run");
          Future<MeasurementResult[]> future;
          setCurrentTask(ready);
          setCurrentTaskStartTime(Calendar.getInstance().getTime());
          if (ready.getDescription().priority == MeasurementTask.USER_PRIORITY) {
            // User task can override the power policy. So a different task wrapper is used.
            future = measurementExecutor.submit(new UserMeasurementTask(ready, this));
          } else {
            future = measurementExecutor.submit(new ServerMeasurementTask(ready, this));
          }

          synchronized (pendingTasks) {
            pendingTasks.put(ready, future);
          }
        }
      }
      else{// if(task.timeFromExecution()>0){
        MeasurementTask waiting=mainQueue.peek();
        if(waiting!=null){
          long timeFromExecution = task.timeFromExecution();
          measurementIntentSender = PendingIntent.getBroadcast(this, 0, new UpdateIntent("", UpdateIntent.MEASUREMENT_ACTION),PendingIntent.FLAG_CANCEL_CURRENT);
          alarmManager.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + timeFromExecution, measurementIntentSender);
          setCurrentTask(null);//TODO
        }

      }
    } catch (IllegalArgumentException e) {
      // Task creation in clone can create this exception

    } catch (Exception e) {
      // We don't want any unexpected exception to crash the process

    }
  }

  //returns taskId on success submissions
  public synchronized String submitTask(MeasurementTask newTask) {  
    //TODO check if scheduler is running... and there is a current running/scheduled task
//
//    String newTaskId=newTask.generateTaskID();
    String newTaskId=newTask.getTaskId();
    tasksStatus.put(newTaskId, TaskStatus.SCHEDULED);
    idToClientKey.put(newTaskId, newTask.getKey());
    Logger.d("MeasurementScheduler --> submitTask: "+newTask.getDescription().key+" "+newTaskId);
    MeasurementTask current;
    if(getCurrentTask()!=null){
      current=getCurrentTask();
      Logger.d("submitTask: current is NOT null");
    }else{
      current=null;
      Logger.d("submitTask: current is null");
    }

    if(current!=null && 
        //        current instanceof PreemptibleMeasurementTask &&
        newTask.getDescription().priority<current.getDescription().priority &&
        new Date(current.getDuration()+getCurrentTaskStartTime().getTime()).after(newTask.getDescription().endTime) ){

      if(pendingTasks.containsKey(current)){
        for(MeasurementTask mt: pendingTasks.keySet()){
          if(current.equals(mt)){
            current=mt;
            //            pendingTasks.remove(mt);
            break;
          }
        }
        Logger.e("Cancelling Current Task");
        if(current instanceof PreemptibleMeasurementTask && ((PreemptibleMeasurementTask)current).pause()){
          pendingTasks.remove(current);
          if(newTask.timeFromExecution() <= 0){            
            mainQueue.add(newTask);
            mainQueue.add(current);
            handleMeasurement();
          }else{
            mainQueue.add(newTask);
            mainQueue.add(current);
            long timeFromExecution = newTask.timeFromExecution();
            measurementIntentSender = PendingIntent.getBroadcast(this, 0, new UpdateIntent("", UpdateIntent.MEASUREMENT_ACTION),PendingIntent.FLAG_CANCEL_CURRENT);
            alarmManager.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + timeFromExecution, measurementIntentSender);
            setCurrentTask(newTask);
            setCurrentTaskStartTime(new Date(System.currentTimeMillis() + timeFromExecution));
          }

        }else if(current.stop()){
          pendingTasks.remove(current);
          if(newTask.timeFromExecution() <= 0){
            mainQueue.add(newTask);
            mainQueue.add(current);
            handleMeasurement();
          }else{
            mainQueue.add(newTask);
            mainQueue.add(current);
            long timeFromExecution = newTask.timeFromExecution();
            measurementIntentSender = PendingIntent.getBroadcast(this, 0, new UpdateIntent("", UpdateIntent.MEASUREMENT_ACTION),PendingIntent.FLAG_CANCEL_CURRENT);
            alarmManager.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + timeFromExecution, measurementIntentSender);
            //            setCurrentTask(null);
            setCurrentTask(newTask);
            setCurrentTaskStartTime(new Date(System.currentTimeMillis() + timeFromExecution));
          }
        }
        else{
          mainQueue.add(newTask);
        }
      }else{
        alarmManager.cancel(measurementIntentSender);
        if(newTask.timeFromExecution() <= 0){
          mainQueue.add(newTask);
          handleMeasurement();
        }
        else{
          mainQueue.add(newTask);
          long timeFromExecution = newTask.timeFromExecution();
          measurementIntentSender = PendingIntent.getBroadcast(this, 0, new UpdateIntent("", UpdateIntent.MEASUREMENT_ACTION),PendingIntent.FLAG_CANCEL_CURRENT);
          alarmManager.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + timeFromExecution, measurementIntentSender);
          //          setCurrentTask(null);
          setCurrentTask(newTask);
          setCurrentTaskStartTime(new Date(System.currentTimeMillis() + timeFromExecution));
        }
      }
    }
    else{
      mainQueue.add(newTask); 
      if(current==null){
        Logger.e("submitTask: calling handleMeasurement");
        alarmManager.cancel(measurementIntentSender);
        handleMeasurement();
      }
    }
    return newTaskId;
  }
  
  public synchronized boolean cancelTask(String taskId, String clientKey){
    // Hongyi: should ensure taskId is not null 
    
    if(taskId != null && idToClientKey.containsKey(taskId)){
      if(idToClientKey.get(taskId).equals(clientKey)){
        
        boolean found=false;
        for(Object object : mainQueue) {
          MeasurementTask task = (MeasurementTask) object;
          if (task.getTaskId().equals(taskId) && task.getKey().equals(clientKey)){
            mainQueue.remove(task);
            found=true;
          }
        }

        for(Object object : waitingTasksQueue) {
          MeasurementTask task = (MeasurementTask) object;
          if (task.getTaskId().equals(taskId) && task.getKey().equals(clientKey)){
            waitingTasksQueue.remove(task);
            found=true;
          }
        }
        MeasurementTask currentMeasumrentTask=getCurrentTask();
        if(currentMeasumrentTask!=null && currentMeasumrentTask.getTaskId().equals(taskId) && currentMeasumrentTask.getKey().equals(clientKey)){
          for(MeasurementTask mt: pendingTasks.keySet()){
            if(currentMeasumrentTask.equals(mt)){
              currentMeasumrentTask=mt;
              break;
            }
          }
          pendingTasks.remove(currentMeasumrentTask);
          return currentMeasumrentTask.stop();
        }

        return found;
      }
    }
    return false; 
  }

  @Override
  public void onDestroy() {
    Logger.d("MeasurementScheduler -> onDestroy");
    super.onDestroy();
    cleanUp();
  }


  public TaskStatus getTaskStatus(String taskID){
    if(tasksStatus.get(taskID)==null){
      return TaskStatus.NOTFOUND;
    }
    return tasksStatus.get(taskID);
  }

  private class TaskComparator implements Comparator<MeasurementTask> {

    @Override
    public int compare(MeasurementTask task1, MeasurementTask task2) {
      return task1.compareTo(task2);
    }   
  }

  private class WaitingTasksComparator implements Comparator<MeasurementTask> {

    @Override
    public int compare(MeasurementTask task1, MeasurementTask task2) {
      Long task1Prority = task1.measurementDesc.priority;
      Long task2Priority = task2.measurementDesc.priority;

      int priorityComparison = task1Prority.compareTo(task2Priority); 
      if (priorityComparison == 0 && task1.measurementDesc.endTime != null && task2.measurementDesc.endTime != null){
        return task1.measurementDesc.endTime.compareTo(task2.measurementDesc.endTime);
      }else{
        return priorityComparison;
      }
    }
  }

  public void sendResultMessage (Parcelable[] results, String clientKey, String taskId) {
    // Hongyi: return the result to client by message
    Messenger messenger = mClients.get(clientKey);
    if (messenger != null) {
      Message msg = Message.obtain(null, Config.MSG_SEND_RESULT);
      Bundle data = new Bundle();     
      data.putParcelableArray("results", results);   
      data.putString("taskId", taskId);      
      Logger.d("Sending result back to client: taskId: " + taskId);
//      if ( result != null ) {
//        Logger.d("Result: " + result.toString());
//      }
//      else {
//        Logger.d("Empty Result!");
//      }
      msg.setData(data);
      try {
        messenger.send(msg);
      } catch (RemoteException e) {
        // The client is dead, just remove it
        mClients.remove(clientKey);
      }
    }
    else {
      Logger.e("ClientKey " + clientKey + " doesn't match a messenger!");
    }
  }

  public void sendResultMessage (Parcelable[] results, String clientKey) {
    sendResultMessage(results, clientKey, "hahaha");
  }
  


  @Override
  public IBinder onBind(Intent intent) {
    return messenger.getBinder();
  }

  /** 
   * Prevents new tasks from being scheduled. Started task will still run to finish. 
   */
  public synchronized void pause() {
    Logger.d("Service pause called");
    this.pauseRequested = true;

  }

  /** Enables new tasks to be scheduled */
  public synchronized void resume() {
    Logger.d("Service resume called");
    this.pauseRequested = false; 
  }

  public synchronized boolean isPauseRequested() {
    return this.pauseRequested;
  }

  /** Request the scheduler to stop execution. */
  public synchronized void requestStop() {
    this.stopRequested = true;
    this.notifyAll();
    this.stopForeground(true);
    this.stopSelf();
  }

  private synchronized boolean isStopRequested() {
    return this.stopRequested;
  }

  private synchronized void cleanUp() {
    Logger.d("Service cleanUp called");
    this.mainQueue.clear();
    this.waitingTasksQueue.clear();

    if (this.currentTask != null) {
      this.currentTask.stop();
    }

    // remove all future tasks
    this.measurementExecutor.shutdown();
    // remove and stop all active tasks
    this.measurementExecutor.shutdownNow();
    this.checkin.shutDown();

    this.unregisterReceiver(broadcastReceiver);
    Logger.d("canceling pending intents");

    if (checkinIntentSender != null) {
      checkinIntentSender.cancel();
      alarmManager.cancel(checkinIntentSender);
    }
    if (checkinRetryIntentSender != null) {
      checkinRetryIntentSender.cancel();
      alarmManager.cancel(checkinRetryIntentSender);
    }
    if (measurementIntentSender != null) {
      measurementIntentSender.cancel();
      alarmManager.cancel(measurementIntentSender);
    }
    this.notifyAll();
    phoneUtils.shutDown();



    Logger.i("Shut down all executors and stopping service");
  }


  private void getTasksFromServer() throws IOException {
    Logger.i("Downloading tasks from the server");
    checkin.getCookie();
    List<MeasurementTask> tasksFromServer = checkin.checkin();
    // The new task schedule overrides the old one

    for (MeasurementTask task : tasksFromServer) {
      Logger.i("added task: " + task.toString());
      this.mainQueue.add(task);
    }
  }

  private void resetCheckin() {
    // reset counters for checkin
    checkinRetryCnt = 0;
    checkinRetryIntervalSec = Config.MIN_CHECKIN_RETRY_INTERVAL_SEC;
    checkin.initializeAccountSelector();
  }

  private class CheckinTask implements Runnable {
    @Override
    public void run() {
      Logger.i("checking Speedometer service for new tasks");
      lastCheckinTime = Calendar.getInstance();
      try {
        uploadResults();
        getTasksFromServer();
        // Also reset checkin if we get a success
        resetCheckin();
        // Schedule the new tasks
        if(getCurrentTask()==null){
          alarmManager.cancel(measurementIntentSender);
          handleMeasurement();
        }
        //        
      } catch (Exception e) {
        /*
         * Executor stops all subsequent execution of a periodic task if a raised
         * exception is uncaught. We catch all undeclared exceptions here
         */
        Logger.e("Unexpected exceptions caught", e);
        if (checkinRetryCnt > Config.MAX_CHECKIN_RETRY_COUNT) {
          /* If we have retried more than MAX_CHECKIN_RETRY_COUNT times upon a checkin failure, 
           * we will stop retrying and wait until the next checkin period*/
          resetCheckin();
        } else if (checkinRetryIntervalSec < checkinIntervalSec) {
          Logger.i("Retrying checkin in " + checkinRetryIntervalSec + " seconds");
          /* Use checkinRetryIntentSender so that the periodic checkin schedule will
           * remain intact
           */
          checkinRetryIntentSender = PendingIntent.getBroadcast(MeasurementScheduler.this, 0, 
            new UpdateIntent("", UpdateIntent.CHECKIN_RETRY_ACTION), 
            PendingIntent.FLAG_CANCEL_CURRENT); 
          alarmManager.set(AlarmManager.RTC_WAKEUP, 
            System.currentTimeMillis() + checkinRetryIntervalSec * 1000, 
            checkinRetryIntentSender);
          checkinRetryCnt++;
          checkinRetryIntervalSec =
              Math.min(Config.MAX_CHECKIN_RETRY_INTERVAL_SEC, checkinRetryIntervalSec * 2);
        }
      } finally {
        PhoneUtils.getPhoneUtils().releaseWakeLock();
      }
    }
  }

  private void uploadResults() {
    Vector<MeasurementResult> finishedTasks = new Vector<MeasurementResult>();
    MeasurementResult[] results;
    Future<MeasurementResult[]> future;

    synchronized (this.pendingTasks) {
      try {
        for (MeasurementTask task : this.pendingTasks.keySet()) {
          future = this.pendingTasks.get(task);
          if (future != null) {
            if (future.isDone()) {
              try {
                this.pendingTasks.remove(task);
                if (!future.isCancelled()) {//TODO check this
                  results = future.get();
                  for(MeasurementResult r: results){
                    finishedTasks.add(r);
                  }

                } else {
                  Logger.e("Task execution was canceled");
                  results = MeasurementResult.getFailureResult(task,
                    new CancellationException("Task cancelled"));
                  for ( MeasurementResult r: results ) {
                    finishedTasks.add(r);
                  }
                }
              } catch (InterruptedException e) {
                Logger.e("Task execution interrupted", e);
              } catch (ExecutionException e) {
                if (e.getCause() instanceof MeasurementSkippedException) {
                  // Don't do anything with this - no need to report skipped measurements
                  Logger.i("Task skipped", e.getCause());
                } else {
                  // Log the error
                  Logger.e("Task execution failed", e.getCause());
                  results = MeasurementResult.getFailureResult(task, e.getCause());
                  for ( MeasurementResult r: results ) {
                    finishedTasks.add(r);
                  }
                }
              } catch (CancellationException e) {
                Logger.e("Task cancelled", e);
              }
            }
            //            else if (task.isPassedDeadline()) {//TODO check this
            //              /* If a task has reached its deadline but has not been run, 
            //               * remove it and report failure 
            //               */
            //              this.pendingTasks.remove(task);
            //              future.cancel(true);
            //              finishedTasks.add(this.getFailureResult(task,
            //                  new RuntimeException("Deadline passed before execution")));
            //            }
          }
          //                  if (future == null) {//TODO check this
          //                      /* Tasks that are scheduled after deadline are put into pendingTasks with a 
          //                       * null future.
          //                       */
          //                      this.pendingTasks.remove(task);
          //                      finishedTasks.add(this.getFailureResult(task,
          //                              new RuntimeException("Task scheduled after deadline")));
          //                  }
        }
      } catch (ConcurrentModificationException e) {
        /* keySet is a synchronized view of the keys. However, changes during iteration will throw
         * ConcurrentModificationException. Since we have synchronized all changes to pendingTasks
         * this should not happen. 
         */
        Logger.e("Pending tasks is changed during measurement upload");
      }
    }

    if (finishedTasks.size() > 0) {
      try {
        this.checkin.uploadMeasurementResult(finishedTasks);
      } catch (IOException e) {
        Logger.e("Error when uploading message");
      }
    }


    Logger.i("A total of " + finishedTasks.size() + " uploaded");
    Logger.i("A total of " + this.pendingTasks.size() + " is in pendingTasks");
  }

  /** Returns the checkin interval of the scheduler in seconds */
  public synchronized long getCheckinInterval() {
    return this.checkinIntervalSec;
  }


  /** Returns the last checkin time */
  public synchronized Date getLastCheckinTime() {
    if (lastCheckinTime != null) {
      return lastCheckinTime.getTime();
    } else {
      return null;
    }
  }

  /** Returns the next (expected) checkin time */
  public synchronized Date getNextCheckinTime() {
    if (lastCheckinTime != null) {
      Calendar nextCheckinTime = (Calendar)lastCheckinTime.clone();
      nextCheckinTime.add(Calendar.SECOND, (int)getCheckinInterval());
      return nextCheckinTime.getTime();
    } else {
      return null;
    }
  }

  /** Set the interval for checkin in seconds */
  public synchronized void setCheckinInterval(long interval) {
    Logger.i("Setting Checkin Interval");
    this.checkinIntervalSec = Math.max(Config.MIN_CHECKIN_INTERVAL_SEC, interval);
    // the new checkin schedule will start in PAUSE_BETWEEN_CHECKIN_CHANGE_MSEC seconds
    checkinIntentSender = PendingIntent.getBroadcast(this, 0, 
      new UpdateIntent("", UpdateIntent.CHECKIN_ACTION), PendingIntent.FLAG_CANCEL_CURRENT);
    alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, 
      System.currentTimeMillis() + Config.PAUSE_BETWEEN_CHECKIN_CHANGE_MSEC, 
      checkinIntervalSec * 1000, checkinIntentSender);

    Logger.i("Setting checkin interval to " + interval + " seconds");
  }
  /**
   * Perform a checkin operation.
   */
  public void handleCheckin() {
    if (PhoneUtils.getPhoneUtils().getCurrentBatteryLevel() < Config.minBatteryThreshold) {
      return;
    }
    /* The CPU can go back to sleep immediately after onReceive() returns. Acquire
     * the wake lock for the new thread here and release the lock when the thread finishes
     */
    PhoneUtils.getPhoneUtils().acquireWakeLock();
    new Thread(checkinTask).start();
  }
}
