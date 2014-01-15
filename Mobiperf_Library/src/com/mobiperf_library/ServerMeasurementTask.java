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
import com.mobiperf_library.exceptions.MeasurementSkippedException;
import com.mobiperf_library.measurements.ParallelTask;
import com.mobiperf_library.measurements.SequentialTask;
import com.mobiperf_library.util.Logger;
import com.mobiperf_library.util.PhoneUtils;

/**
 * @author Hongyi Yao
 *
 */


public class ServerMeasurementTask implements Callable<MeasurementResult []> {
  private MeasurementTask realTask;
  private MeasurementScheduler scheduler;
  public ServerMeasurementTask(MeasurementTask task, MeasurementScheduler scheduler) {
    realTask = task;
    this.scheduler = scheduler;
  }

  private void broadcastMeasurementStart() {
    Intent intent = new Intent();
    intent.setAction(UpdateIntent.MEASUREMENT_PROGRESS_UPDATE_ACTION);
    intent.putExtra(UpdateIntent.TASK_STATUS_PAYLOAD, Config.TASK_STARTED);
//    intent.putExtra(UpdateIntent.TASKID_PAYLOAD, realTask.generateTaskID());
    intent.putExtra(UpdateIntent.TASKID_PAYLOAD, realTask.getTaskId());
    scheduler.sendBroadcast(intent);
  }

//  private void broadcastMeasurementEnd(MeasurementResult result
//    , MeasurementError error, String clientKey) {
  private void broadcastMeasurementEnd(MeasurementResult result
    , MeasurementError error) {

    // Only broadcast information about measurements if they are true errors.
    if (!(error instanceof MeasurementSkippedException)) {
      Intent intent = new Intent();
      intent.setAction(UpdateIntent.MEASUREMENT_PROGRESS_UPDATE_ACTION);
      intent.putExtra(UpdateIntent.TASK_STATUS_PAYLOAD, Config.TASK_FINISHED);
      intent.putExtra(UpdateIntent.TASK_PRIORITY_PAYLOAD, (int) realTask.getDescription().priority);
//      intent.putExtra(UpdateIntent.TASKID_PAYLOAD, realTask.generateTaskID());
      intent.putExtra(UpdateIntent.TASKID_PAYLOAD, realTask.getTaskId());

      if (result != null){
        if(result.getTaskProgress()==TaskProgress.PAUSED){
          intent.putExtra(UpdateIntent.TASK_STATUS_PAYLOAD, Config.TASK_PAUSED);
        }
        else {
          intent.putExtra(UpdateIntent.TASK_STATUS_PAYLOAD, Config.TASK_FINISHED);
          intent.putExtra(UpdateIntent.RESULT_PAYLOAD, result);
          //scheduler.sendResultMessage(result, result.getTaskKey(), realTask.getTaskId());
        }
        scheduler.sendBroadcast(intent);
      }

      
//      if (result != null){
//        if(result.getTaskProgress()==TaskProgress.PAUSED){
//          intent.putExtra(UpdateIntent.TASK_STATUS_PAYLOAD, Config.TASK_PAUSED);
//        }else if(result.getTaskProgress()==TaskProgress.COMPLETED){
//          intent.putExtra(UpdateIntent.TASK_STATUS_PAYLOAD, Config.TASK_FINISHED);
//          scheduler.sendResultMessage(result, result.getTaskKey(), realTask.getTaskId());
//
//          // Hongyi: task succeed. return result.
////          scheduler.sendResultMessage(result, clientKey);
//        }
//        else{
//          intent.putExtra(UpdateIntent.TASK_STATUS_PAYLOAD, Config.TASK_FINISHED);
//          String errorString = "Measurement " + realTask.getDescriptor() + " has failed";
//          errorString += "\nTimestamp: " + Calendar.getInstance().getTime();
//          Logger.e(errorString);
//          intent.putExtra(UpdateIntent.ERROR_STRING_PAYLOAD, errorString);
//
//          // TODO(Hongyi): task is stopped, return partial result?
//          scheduler.sendResultMessage(null, clientKey);
//        }
//      }
//      else {
//        intent.putExtra(UpdateIntent.TASK_STATUS_PAYLOAD, Config.TASK_FINISHED);
//        String errorString = "Measurement " + realTask.getDescriptor() + " has failed";
//        errorString += "\nTimestamp: " + Calendar.getInstance().getTime();
//        intent.putExtra(UpdateIntent.ERROR_STRING_PAYLOAD, errorString);
//
//        // Hongyi: task failed.
//        scheduler.sendResultMessage(null, clientKey);
//      } 
//
//      scheduler.sendBroadcast(intent);
    }

  }

  @Override
  public MeasurementResult[] call() throws MeasurementError {
    MeasurementResult[] results = null;
    PhoneUtils phoneUtils = PhoneUtils.getPhoneUtils();
    try {
      phoneUtils.acquireWakeLock();

      if(!(phoneUtils.isCharging() || phoneUtils.getCurrentBatteryLevel() > Config.minBatteryThreshold)){
        throw new MeasurementSkippedException("Not enough battery power");
      }
      if (scheduler.isPauseRequested()) {
        Logger.i("Skipping measurement - scheduler paused");
        throw new MeasurementSkippedException("Scheduler paused");
      }
      //        MeasurementScheduler.this.setCurrentTask(realTask);
      broadcastMeasurementStart();
      try {
        results = realTask.call(); 
        for(MeasurementResult r: results){
//          broadcastMeasurementEnd(r, null, realTask.measurementDesc.key);
          broadcastMeasurementEnd(r, null);
        }
        return results;
      } catch (MeasurementError e) {
        String error = "Server measurement " + realTask.getDescriptor() 
            + " has failed: " + e.getMessage() + "\n";
        error += "Timestamp: " + Calendar.getInstance().getTime();
        Logger.e(error);

        if ( realTask.getType().equals(ParallelTask.TYPE) ) {
          ParallelTask pTask = (ParallelTask)realTask;
          for ( MeasurementTask t : pTask.getTasks() ) {
            MeasurementResult result = scheduler.getFailureResult(t, e);
            // Hongyi: change taskId
            result.setTaskId(realTask.getTaskId());
            broadcastMeasurementEnd(result, e);
          }
        }
        else if (realTask.getType().equals(SequentialTask.TYPE)  ) {
          SequentialTask sTask = (SequentialTask)realTask;
          for ( MeasurementTask t : sTask.getTasks() ) {
            MeasurementResult result = scheduler.getFailureResult(t, e);
            // Hongyi: change taskId
            result.setTaskId(realTask.getTaskId());
            broadcastMeasurementEnd(result, e);
          }
        }
        else {
          MeasurementResult result = scheduler.getFailureResult(realTask, e);
          broadcastMeasurementEnd(result, e);
        }       
        
//        Logger.e("Got MeasurementError running task: " + e.getMessage());
//        broadcastMeasurementEnd(null, e, realTask.measurementDesc.key);
//        throw e;
      } catch (Exception e) {
        String error = "Server measurement " + realTask.getDescriptor() + " has failed\n";
        error += "Unexpected Exception: " + e.getMessage() + "\n";
        error += "\nTimestamp: " + Calendar.getInstance().getTime();
        Logger.e(error);
        

        if ( realTask.getType().equals(ParallelTask.TYPE) ) {
          ParallelTask pTask = (ParallelTask)realTask;
          for ( MeasurementTask t : pTask.getTasks() ) {
            MeasurementResult result = scheduler.getFailureResult(t, e);
            // Hongyi: change taskId
            result.setTaskId(realTask.getTaskId());
            broadcastMeasurementEnd(result, new MeasurementError("Got exception running task", e));
          }
        }
        else if (realTask.getType().equals(SequentialTask.TYPE)  ) {
          SequentialTask sTask = (SequentialTask)realTask;
          for ( MeasurementTask t : sTask.getTasks() ) {
            MeasurementResult result = scheduler.getFailureResult(t, e);
            // Hongyi: change taskId
            result.setTaskId(realTask.getTaskId());
            broadcastMeasurementEnd(result, new MeasurementError("Got exception running task", e));
          }
        }
        else {
          MeasurementResult result = scheduler.getFailureResult(realTask, e);
          broadcastMeasurementEnd(result, new MeasurementError("Got exception running task", e));
        }
        
//        Logger.e("Got exception running task: " + e.getMessage());
//        MeasurementError err = new MeasurementError("Got exception running task", e);
//        broadcastMeasurementEnd(null, err, realTask.measurementDesc.key);
//        throw err;
      }
    } finally {
      phoneUtils.releaseWakeLock();
      if(scheduler.getCurrentTask().equals(realTask)){
        scheduler.setCurrentTask(null);
      }
    }
    return results;
  }
}
