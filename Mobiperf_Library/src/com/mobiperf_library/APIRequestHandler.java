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

import com.mobiperf_library.util.Logger;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

/**
 * @author Hongyi Yao
 * Define message handler to process cross-process request
 */
public class APIRequestHandler extends Handler {
  MeasurementScheduler scheduler;
  
  public APIRequestHandler(MeasurementScheduler scheduler) {
    this.scheduler = scheduler;
  }
  
  @Override
  public void handleMessage(Message msg) {
    Bundle data = msg.getData();
    // TODO(Hongyi): check it
    data.setClassLoader(scheduler.getClassLoader());
    String clientKey = data.getString("clientKey");

    MeasurementTask task;
    String taskId = null;
    switch (msg.what) {
      case Config.MSG_REGISTER_CLIENT:
        if ( clientKey != null ) {
          scheduler.getClientsMap().put(clientKey, msg.replyTo);
          Logger.d("Get register client message! key = " + clientKey);
        }
        else {
          Logger.e("No client key found when registering!");
        }
        break;
      case Config.MSG_UNREGISTER_CLIENT:
        if ( clientKey != null ) {
          scheduler.getClientsMap().remove(clientKey);
          Logger.d("Unregister client key = " + clientKey);
        }
        else {
          Logger.e("No client key found when unregistering!");
        }
        break;
      case Config.MSG_SUBMIT_TASK:
        task = null;
        task = (MeasurementTask) data.getParcelable("measurementTask");
        if ( task != null ) {
          Logger.d("Hongyi: Add new task! taskId " + task.getTaskId());
          
//          // Hongyi: for test
//          task.measurementDesc.parameters.put("secondTimestamp"
//            , String.valueOf(System.currentTimeMillis()));
          
          taskId = scheduler.submitTask(task);
        }
        break;
      case Config.MSG_CANCEL_TASK:
        taskId = data.getString("taskId");
        if ( taskId != null && clientKey != null ) {
          Logger.d("cancel taskId: " + taskId + ", clientKey: " + clientKey);
          scheduler.cancelTask(taskId, clientKey);
        }
        break;
      default:
        super.handleMessage(msg);
    }
  }
}