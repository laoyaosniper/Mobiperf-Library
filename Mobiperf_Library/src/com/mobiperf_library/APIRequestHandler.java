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

import java.security.InvalidParameterException;

import com.mobiperf_library.api.TaskParams;
import com.mobiperf_library.measurements.DnsLookupTask;
import com.mobiperf_library.measurements.HttpTask;
import com.mobiperf_library.measurements.PingTask;
import com.mobiperf_library.measurements.TracerouteTask;
import com.mobiperf_library.util.Logger;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

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

    int id, taskType;
    MeasurementTask task;
    MeasurementDesc desc;
    Messenger messenger;
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
        id = msg.arg1;
        taskType = msg.arg2;
        task = null;
//        desc = (MeasurementDesc) data.getParcelable("measurementDesc");
//        if ( desc != null ) {
//          switch (taskType) {
//            case TaskParams.DNSLookup:  task = new DnsLookupTask(desc);  break;
//            case TaskParams.HTTP:       task = new HttpTask(desc);       break;
//            case TaskParams.Ping:       task = new PingTask(desc);       break;
//            case TaskParams.Traceroute: task = new TracerouteTask(desc); break;
//            default:
//              throw new InvalidParameterException("Unknown measurement type");
//          }
//        }
//        else {
//          Logger.e("MeasurementDesc not found!");
//        }
        task = (MeasurementTask) data.getParcelable("measurementTask");
        if ( task != null ) {
          Logger.d("Hongyi: Add new task!");
          taskId = scheduler.submitTask(task);
          messenger = scheduler.getClientsMap().get(task.measurementDesc.key);
          Logger.d("Get submit task message! key = " + id);
          if ( messenger != null ) {          
            int pid = android.os.Process.myPid();
            Message replyMsg = Message.obtain(null, Config.MSG_SUBMIT_TASK, id, pid, null);
            Bundle sendData = new Bundle();
            sendData.putString("taskId", taskId);
            replyMsg.setData(sendData);
            try {
              messenger.send(replyMsg);
            } catch (RemoteException e) {
            }
          }
        }
        else {
          // TODO(Hongyi): handle this case
        }
        break;
      case Config.MSG_CANCEL_TASK:
        taskId = data.getString("taskId");
        Logger.d("taskId: " + taskId);
        messenger = scheduler.getClientsMap().get(clientKey);
        if ( messenger != null ) {
          Message replyMsg = Message.obtain(null, Config.MSG_CANCEL_TASK);

          Bundle sendData = new Bundle();
          if ( scheduler.cancelTask(taskId, clientKey) == true ) {
            replyMsg.arg1 = 1;
          }
          else {
            replyMsg.arg1 = 0;
          }
          sendData.putString("taskId", taskId);
          replyMsg.setData(sendData);
          try {
            messenger.send(replyMsg);
          } catch (RemoteException e) {
          }
        }
        break;
      default:
        super.handleMessage(msg);
    }
  }
}