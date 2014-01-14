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
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.Callable;

import android.content.Intent;

import com.mobiperf_library.MeasurementResult.TaskProgress;
import com.mobiperf_library.exceptions.MeasurementError;
import com.mobiperf_library.exceptions.MeasurementSkippedException;
import com.mobiperf_library.util.Logger;
import com.mobiperf_library.util.PhoneUtils;



public class ServerMeasurementTask implements Callable<MeasurementResult []> {
  private MeasurementTask realTask;
  private MeasurementScheduler scheduler;
  private ContextCollector contextCollector;
  public ServerMeasurementTask(MeasurementTask task, MeasurementScheduler scheduler) {
    realTask = task;
    this.scheduler = scheduler;
    this.contextCollector= new ContextCollector();
  }

  private void broadcastMeasurementStart() {
    Intent intent = new Intent();
    intent.setAction(UpdateIntent.MEASUREMENT_PROGRESS_UPDATE_ACTION);
    intent.putExtra(UpdateIntent.TASK_STATUS_PAYLOAD, Config.TASK_STARTED);
    intent.putExtra(UpdateIntent.TASKID_PAYLOAD, realTask.generateTaskID());
    scheduler.sendBroadcast(intent);
  }

  private void broadcastMeasurementEnd(MeasurementResult result
    , MeasurementError error, String clientKey) {

    // Only broadcast information about measurements if they are true errors.
    if (!(error instanceof MeasurementSkippedException)) {
      Intent intent = new Intent();
      intent.setAction(UpdateIntent.MEASUREMENT_PROGRESS_UPDATE_ACTION);
      intent.putExtra(UpdateIntent.TASK_STATUS_PAYLOAD, Config.TASK_FINISHED);
      intent.putExtra(UpdateIntent.TASK_PRIORITY_PAYLOAD, (int) realTask.getDescription().priority);
      intent.putExtra(UpdateIntent.TASKID_PAYLOAD, realTask.generateTaskID());

      if (result != null){
        if(result.getTaskProgress()==TaskProgress.PAUSED){
          intent.putExtra(UpdateIntent.TASK_STATUS_PAYLOAD, Config.TASK_PAUSED);
        }else if(result.getTaskProgress()==TaskProgress.COMPLETED){
          intent.putExtra(UpdateIntent.TASK_STATUS_PAYLOAD, Config.TASK_FINISHED);

          // Hongyi: task succeed. return result.
          scheduler.sendResultMessage(result, clientKey);
        }
        else{
          intent.putExtra(UpdateIntent.TASK_STATUS_PAYLOAD, Config.TASK_FINISHED);
          String errorString = "Measurement " + realTask.getDescriptor() + " has failed";
          errorString += "\nTimestamp: " + Calendar.getInstance().getTime();
          Logger.e(errorString);
          intent.putExtra(UpdateIntent.ERROR_STRING_PAYLOAD, errorString);

          //TODO change this
          scheduler.sendResultMessage(null, clientKey);
        }
      }
      else {
        String errorString = "Measurement " + realTask.getDescriptor() + " has failed";
        errorString += "\nTimestamp: " + Calendar.getInstance().getTime();
        intent.putExtra(UpdateIntent.ERROR_STRING_PAYLOAD, errorString);

        // Hongyi: task failed.
        scheduler.sendResultMessage(null, clientKey);
      } 

      scheduler.sendBroadcast(intent);
    }

  }

  @Override
  public MeasurementResult[] call() throws MeasurementError {
    MeasurementResult[] results = null;
    try {
      PhoneUtils.getPhoneUtils().acquireWakeLock();

      if(!(PhoneUtils.getPhoneUtils().isCharging() || PhoneUtils.getPhoneUtils().getCurrentBatteryLevel() > Config.minBatteryThreshold)){
        throw new MeasurementSkippedException("Not enough battery power");
      }
      if (scheduler.isPauseRequested()) {
        Logger.i("Skipping measurement - scheduler paused");
        throw new MeasurementSkippedException("Scheduler paused");
      }
      //        MeasurementScheduler.this.setCurrentTask(realTask);
      broadcastMeasurementStart();
      try {
    	contextCollector.setInterval(realTask.getDescription().contextIntervalSec);
        contextCollector.startCollector();
        results = realTask.call();
        Vector<Map<String, Object>> contextResults=contextCollector.stopCollector();
        //TODO attach the results to the MeasurementResults
        
        for(MeasurementResult r: results){
          broadcastMeasurementEnd(r, null, realTask.measurementDesc.key);
        }
        return results;
      } catch (MeasurementError e) {
        Logger.e("Got MeasurementError running task", e);
        broadcastMeasurementEnd(null, e, realTask.measurementDesc.key);
        throw e;
      } catch (Exception e) {
        Logger.e("Got exception running task", e);
        MeasurementError err = new MeasurementError("Got exception running task", e);
        broadcastMeasurementEnd(null, err, realTask.measurementDesc.key);
        throw err;
      }
    } finally {
      PhoneUtils.getPhoneUtils().releaseWakeLock();
      if(scheduler.getCurrentTask().equals(realTask)){
        scheduler.setCurrentTask(null);
      }
    }
  }
}
