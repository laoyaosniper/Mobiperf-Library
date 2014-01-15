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

import java.util.Calendar;
import java.util.concurrent.Callable;

import android.content.Intent;

import com.mobiperf_library.MeasurementResult.TaskProgress;
import com.mobiperf_library.exceptions.MeasurementError;
import com.mobiperf_library.measurements.ParallelTask;
import com.mobiperf_library.measurements.SequentialTask;
import com.mobiperf_library.util.Logger;
import com.mobiperf_library.util.PhoneUtils;

/**
 * @author Hongyi Yao
 *
 */

public class UserMeasurementTask implements Callable<MeasurementResult[]> {
  MeasurementTask realTask;
  MeasurementScheduler scheduler;

  public UserMeasurementTask(MeasurementTask task, MeasurementScheduler scheduler) {
    realTask = task;
    this.scheduler = scheduler;  
  }

  private void broadcastMeasurementStart() {
    Intent intent = new Intent();
    intent.setAction(UpdateIntent.MEASUREMENT_PROGRESS_UPDATE_ACTION);
    intent.putExtra(UpdateIntent.TASK_PRIORITY_PAYLOAD, MeasurementTask.USER_PRIORITY);
    intent.putExtra(UpdateIntent.TASK_STATUS_PAYLOAD, Config.TASK_STARTED);
//  intent.putExtra(UpdateIntent.TASKID_PAYLOAD, realTask.generateTaskID());
    intent.putExtra(UpdateIntent.TASKID_PAYLOAD, realTask.getTaskId());
    scheduler.sendBroadcast(intent);
  }

//  private void broadcastMeasurementEnd(MeasurementResult result,String clientKey, String taskId) {
  private void broadcastMeasurementEnd(MeasurementResult result) {
    Intent intent = new Intent();
    intent.setAction(UpdateIntent.MEASUREMENT_PROGRESS_UPDATE_ACTION);

    intent.putExtra(UpdateIntent.TASK_PRIORITY_PAYLOAD, MeasurementTask.USER_PRIORITY);//TODO fixed one value priority for all users task?
//    intent.putExtra(UpdateIntent.TASKID_PAYLOAD, realTask.generateTaskID());
    intent.putExtra(UpdateIntent.TASKID_PAYLOAD, realTask.getTaskId());

    if (result != null){
      // Hongyi: only single task can be paused
      if(result.getTaskProgress()==TaskProgress.PAUSED){
        intent.putExtra(UpdateIntent.TASK_STATUS_PAYLOAD, Config.TASK_PAUSED);
      }
      else{
        intent.putExtra(UpdateIntent.TASK_STATUS_PAYLOAD, Config.TASK_FINISHED);
        intent.putExtra(UpdateIntent.RESULT_PAYLOAD, result);
        //scheduler.sendResultMessage(result, result.getTaskKey(), realTask.getTaskId());
      }
      scheduler.sendBroadcast(intent);
    }
    



//    if (result != null){
//      if(result.getTaskProgress()==TaskProgress.PAUSED){
//        intent.putExtra(UpdateIntent.TASK_STATUS_PAYLOAD, Config.TASK_PAUSED);
//      }else if(result.getTaskProgress()==TaskProgress.COMPLETED){
//        intent.putExtra(UpdateIntent.TASK_STATUS_PAYLOAD, Config.TASK_FINISHED);
//        scheduler.sendResultMessage(result, result.getTaskKey(), realTask.generateTaskID());
//        
////        intent.putExtra(UpdateIntent.RESULT_PAYLOAD, result);
//        
//        // Hongyi: task succeed. return result.//TODO do this after receiving task finished intent
//        scheduler.sendResultMessage(result, clientKey, taskId);
//      }
//      else{
//        intent.putExtra(UpdateIntent.TASK_STATUS_PAYLOAD, Config.TASK_FINISHED);
//        
//        // TODO(Hongyi): better move them to measurement results
//        String errorString = "Measurement " + realTask.getDescriptor() + " has failed";
//        errorString += "\nTimestamp: " + Calendar.getInstance().getTime();
//        Logger.e(errorString);
//        intent.putExtra(UpdateIntent.ERROR_STRING_PAYLOAD, errorString);
//
//        // TODO(Hongyi): task is stopped, shall we return partial result? Ashkan: No, null
//        // Hongyi: currently just return null
//        scheduler.sendResultMessage(null, clientKey, taskId);
//
//      }
//    }
//    
//    else {
//      intent.putExtra(UpdateIntent.TASK_STATUS_PAYLOAD, Config.TASK_FINISHED);
//      
//      String errorString = "Measurement " + realTask.getDescriptor() + " has failed";
//      errorString += "\nTimestamp: " + Calendar.getInstance().getTime();
//      intent.putExtra(UpdateIntent.ERROR_STRING_PAYLOAD, errorString);
//
//      //Hongyi: task failed, return empty result.
//      Logger.e("Task failed, return empty result");
//      scheduler.sendResultMessage(null, clientKey, taskId);
//    } 
//
//    scheduler.sendBroadcast(intent);
//    // Update the status bar once the user measurement finishes

  }

  /**
   * The call() method that broadcast intents before the measurement starts and after the
   * measurement finishes.
   */
  @Override
  public MeasurementResult[] call() throws MeasurementError {
    MeasurementResult[] results = null;
    PhoneUtils phoneUtils = PhoneUtils.getPhoneUtils();
    try {
      phoneUtils.acquireWakeLock();
      //        setCurrentTask(realTask);
      broadcastMeasurementStart();
      results = realTask.call();
      // Hongyi: better to catch exception in the same way of ServerMeasurement
      // otherwise iterating results may cause null pointer exception
//      broadcastMeasurementEnd(results);
      for(MeasurementResult r: results){
//        broadcastMeasurementEnd(r,realTask.measurementDesc.key, realTask.generateTaskID());
        broadcastMeasurementEnd(r);
      }
      
    } catch (MeasurementError e) {
//      String error = "User measurement " + realTask.getDescriptor() 
//          + " has failed: " + e.getMessage() + "\n";
//      error += "Timestamp: " + Calendar.getInstance().getTime();
//      Logger.e(error);
      
      if ( realTask.getType().equals(ParallelTask.TYPE) ) {
        ParallelTask pTask = (ParallelTask)realTask;
        for ( MeasurementTask t : pTask.getTasks() ) {
          MeasurementResult result = scheduler.getFailureResult(t, e);
          // Hongyi: change taskId
          result.setTaskId(realTask.getTaskId());
          broadcastMeasurementEnd(result);
        }
//        results = MeasurementResult
//        broadcastMeasurementEnd(results);
      }
      else if (realTask.getType().equals(SequentialTask.TYPE)  ) {
        SequentialTask sTask = (SequentialTask)realTask;
        for ( MeasurementTask t : sTask.getTasks() ) {
          MeasurementResult result = scheduler.getFailureResult(t, e);
          // Hongyi: change taskId
          result.setTaskId(realTask.getTaskId());
          broadcastMeasurementEnd(result);
        }
      }
      else {
        MeasurementResult result = scheduler.getFailureResult(realTask, e);
        broadcastMeasurementEnd(result);
      }

//      broadcastMeasurementEnd(result, realTask.measurementDesc.key, realTask.generateTaskID());
      
//      broadcastMeasurementEnd(null, realTask.measurementDesc.key, realTask.generateTaskID());
//      throw e;
    } catch (Exception e) {
      String error = "User measurement " + realTask.getDescriptor() + " has failed\n";
      error += "Unexpected Exception: " + e.getMessage() + "\n";
      error += "\nTimestamp: " + Calendar.getInstance().getTime();
      Logger.e(error);
      

      if ( realTask.getType().equals(ParallelTask.TYPE) ) {
        ParallelTask pTask = (ParallelTask)realTask;
        for ( MeasurementTask t : pTask.getTasks() ) {
          MeasurementResult result = scheduler.getFailureResult(t, e);
          // Hongyi: change taskId
          result.setTaskId(realTask.getTaskId());
          broadcastMeasurementEnd(result);
        }
      }
      else if (realTask.getType().equals(SequentialTask.TYPE)  ) {
        SequentialTask sTask = (SequentialTask)realTask;
        for ( MeasurementTask t : sTask.getTasks() ) {
          MeasurementResult result = scheduler.getFailureResult(t, e);
          // Hongyi: change taskId
          result.setTaskId(realTask.getTaskId());
          broadcastMeasurementEnd(result);
        }
      }
      else {
        MeasurementResult result = scheduler.getFailureResult(realTask, e);
        broadcastMeasurementEnd(result);
      }
      

//      broadcastMeasurementEnd(result, realTask.measurementDesc.key, realTask.generateTaskID());
//      broadcastMeasurementEnd(null, realTask.measurementDesc.key, realTask.generateTaskID());
//      MeasurementError err = new MeasurementError("Got exception running task", e);
//      throw err;
    } finally {
      if(scheduler.getCurrentTask().equals(realTask)){
        scheduler.setCurrentTask(null);
      }
      phoneUtils.releaseWakeLock();
    }
    return results;
  }
  
  
}