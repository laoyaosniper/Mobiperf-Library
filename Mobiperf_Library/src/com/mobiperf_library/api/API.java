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
package com.mobiperf_library.api;

import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.HashMap;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.Parcelable;
import android.os.RemoteException;


import android.os.Bundle;
import android.widget.Toast;

import com.mobiperf_library.Config;
import com.mobiperf_library.MeasurementResult;
import com.mobiperf_library.MeasurementScheduler;
import com.mobiperf_library.MeasurementTask;
import com.mobiperf_library.measurements.DnsLookupTask;
import com.mobiperf_library.measurements.DnsLookupTask.DnsLookupDesc;
import com.mobiperf_library.measurements.ParallelTask;
import com.mobiperf_library.measurements.ParallelTask.ParallelDesc;
import com.mobiperf_library.measurements.PingTask;
import com.mobiperf_library.measurements.PingTask.PingDesc;
import com.mobiperf_library.measurements.HttpTask;
import com.mobiperf_library.measurements.HttpTask.HttpDesc;
import com.mobiperf_library.measurements.SequentialTask;
import com.mobiperf_library.measurements.SequentialTask.SequentialDesc;
import com.mobiperf_library.measurements.TracerouteTask;
import com.mobiperf_library.measurements.TracerouteTask.TracerouteDesc;
import com.mobiperf_library.util.Logger;

/**
 * @author jackjia,Hongyi Yao (hyyao@umich.edu)
 * The user API for Mobiperf library
 * User: add task => Scheduler: run task, send finish event
 * => User: implement OnResultReturn to get result
 * thread safe
 * The client and server change TaskID as a Long type.
 */
public abstract class API {

  private Context parent;
  
  private boolean isBound = false;
  private boolean isBindingToService = false;
  Messenger mSchedulerMessenger = null;
  // Allocate an unique key when creating
  String uniqueKey = null;
  
  // TODO(Hongyi): We can use a increment counter as local taskId for 
  //    developer to pause or stop measurement. And we should also maintain a
  //    map from local taskId to global taskId 
  private int localId = 0;
  public HashMap<Integer, String> localToGlobalMap;
  public HashMap<String, Integer> globalToLocalMap;
  
  public API(Context parent) {
    this.parent = parent;
    localToGlobalMap = new HashMap<Integer, String>();
    globalToLocalMap = new HashMap<String, Integer>();
    // TODO(Hongyi): do we still need this client key?
    uniqueKey = "haha" + android.os.Process.myPid();
    bindToService();
  }

  
  /**
   * Author: Hongyi Yao
   * Define how to process the measurement result
   * The user must implement this interface to use the library
   * @param result
   */
  public abstract void handleResult(MeasurementResult[] results);
  /**
   * Handler of incoming messages from service.
   */
  class IncomingHandler extends Handler {
    @Override
    public void handleMessage(Message msg) {
      Bundle data = msg.getData();
      // TODO(Hongyi): check it
      data.setClassLoader(MeasurementScheduler.class.getClassLoader());
      
      int localId, pid;
      String taskId;
      String text = null;
      MeasurementResult[] results;
      switch (msg.what) {
        case Config.MSG_SUBMIT_TASK:
          localId = msg.arg1;
          pid = msg.arg2;
          taskId = data.getString("taskId");
          if ( taskId != null ) {
            localToGlobalMap.put(localId, taskId);
            globalToLocalMap.put(taskId, localId);
          }
          else {
            Logger.e("SUMBIT_TASK: TaskId disappeared!");
          }
          //Toast.makeText(parent, "Task added! id = " + localId + ", scheduler pid = " + pid + ", taskId = " + taskId, Toast.LENGTH_SHORT).show();
          break;
        case Config.MSG_SEND_RESULT:
          Parcelable[] parcels = data.getParcelableArray("results");
          results = new MeasurementResult[parcels.length];
          for ( int i = 0; i < results.length; i++ ) {
            results[i] = (MeasurementResult) parcels[i];
          }
          
          taskId = data.getString("taskId");
          //Toast.makeText(parent, "Got TaskId " + taskId + ", corresponding local Id is " + globalToLocalMap.get(taskId), Toast.LENGTH_SHORT).show();
          localToGlobalMap.remove(globalToLocalMap.get(taskId));
          globalToLocalMap.remove(taskId);
          handleResult(results);
          break;
        case Config.MSG_CANCEL_TASK:
          taskId = data.getString("taskId");
          if ( msg.arg1 != 0 ) {
            // cancel succeed, clean up local map
            //Toast.makeText(parent, "Local Id: " + globalToLocalMap.get(taskId), Toast.LENGTH_SHORT).show();
            localToGlobalMap.remove(globalToLocalMap.get(taskId));
            globalToLocalMap.remove(taskId);
          }
        default:
          super.handleMessage(msg);
      }
      text = "Local To Global Map!\n";
      for ( HashMap.Entry<Integer, String> e : localToGlobalMap.entrySet()) {
        text += "LocalId: " + e.getKey() + " --- TaskId: " + e.getValue() + "\n";
      }
      text += "\nGlobal To Local Map!\n";
      for ( HashMap.Entry<String, Integer> e : globalToLocalMap.entrySet()) {
        text += "TaskId: " + e.getKey() + " --- LocalId: " + e.getValue() + "\n";
      }
      Toast.makeText(parent, text, Toast.LENGTH_SHORT).show();
    }
  }
  
  /**
   * Target we publish for clients to send messages to IncomingHandler.
   */
  final Messenger mClientMessenger = new Messenger(new IncomingHandler());
  
  
  /** Defines callbacks for service binding, passed to bindService() */
  private ServiceConnection serviceConn = new ServiceConnection() {

    @Override
    public void onServiceConnected(ComponentName className, IBinder service) {
      Logger.d("onServiceConnected called.....");
      // We've bound to a Messenger and get Messenger instance
      mSchedulerMessenger = new Messenger(service);
      isBound = true;
      isBindingToService = false;
      // Hongyi: register client messenger
      Message msg = Message.obtain(null, Config.MSG_REGISTER_CLIENT);
      Bundle data = new Bundle();
      data.putString("clientKey", uniqueKey);
      msg.setData(data);
      msg.replyTo = mClientMessenger;
      try {
        mSchedulerMessenger.send(msg);
      } catch (RemoteException e) {
        // Service crushed, we can count on soon being disconnected
        // so we don't need to do anything
      }

    }
    
      @Override
      public void onServiceDisconnected(ComponentName arg0) {
          Logger.d("onServiceDisconnected called");
          mSchedulerMessenger = null;
          isBound = false;
      }
  };

  public Messenger getScheduler() {
      if (isBound) {
          return mSchedulerMessenger;
      } else {
          bindToService();
          try {
            Thread.sleep(1000);
          } catch (InterruptedException e) {
            Logger.e("Sleep is interrupted!");
          }
          if ( isBound ) {
            return mSchedulerMessenger;
          }
          else {
            return null;
          }
      }
  }
  
  private void bindToService() {
    Logger.d("MainActivity-> bindToService called");
    if (!isBindingToService && !isBound) {
        // Bind to the scheduler service if it is not bounded
        Intent intent = new Intent("com.mobiperf_library.MeasurementScheduler");
        //Intent intent = new Intent(this, MeasurementScheduler.class);
        parent.bindService(intent, serviceConn, Context.BIND_AUTO_CREATE);
        isBindingToService = true;
    }
  }
  
  public void unbind() {
    if (isBound) {
      // Hongyi: unregister client messenger in the service
      Message msg = Message.obtain(null, Config.MSG_UNREGISTER_CLIENT);
      Bundle data = new Bundle();
      data.putString("clientKey", uniqueKey);
      msg.setData(data);
      try {
        mSchedulerMessenger.send(msg);
      } catch (RemoteException e) {
        // Service crushed, we can count on soon being disconnected
        // so we don't need to do anything
      }
      parent.unbindService(serviceConn);
      isBound = false;
    }
  }
  
  private Bundle getData ( TaskParams taskParams) {
    taskParams.key = uniqueKey;
    Bundle data = new Bundle();
    switch ( taskParams.taskType ) {
      case TaskParams.DNSLookup:
        data.putParcelable("measurementDesc", new DnsLookupDesc(
          taskParams.key, taskParams.startTime, taskParams.endTime
          , taskParams.intervalSec, taskParams.count, taskParams.priority, taskParams.contextIntervalSec
          , taskParams.params ));
        break;
      case TaskParams.HTTP:
        data.putParcelable("measurementDesc", new HttpDesc(
          taskParams.key, taskParams.startTime, taskParams.endTime
          , taskParams.intervalSec, taskParams.count, taskParams.priority, taskParams.contextIntervalSec
          , taskParams.params ));
        break;
      case TaskParams.Ping:
        data.putParcelable("measurementDesc", new PingDesc(
          taskParams.key, taskParams.startTime, taskParams.endTime
          , taskParams.intervalSec, taskParams.count, taskParams.priority, taskParams.contextIntervalSec
          , taskParams.params ));
        break;
      case TaskParams.Traceroute:
        data.putParcelable("measurementDesc", new TracerouteDesc(
          taskParams.key, taskParams.startTime, taskParams.endTime
          , taskParams.intervalSec, taskParams.count, taskParams.priority, taskParams.contextIntervalSec
          , taskParams.params ));
        break;
      default:
        throw new InvalidParameterException("Unknown measurement type");
    }
    return data;
  }

  private Bundle packTask ( TaskParams taskParams) {
    taskParams.key = uniqueKey;
    Bundle data = new Bundle();
    switch ( taskParams.taskType ) {
      case TaskParams.DNSLookup:
        data.putParcelable("measurementTask", new DnsLookupTask(new DnsLookupDesc(
          taskParams.key, taskParams.startTime, taskParams.endTime
          , taskParams.intervalSec, taskParams.count, taskParams.priority, taskParams.contextIntervalSec
          , taskParams.params )));
        break;
      case TaskParams.HTTP:
        data.putParcelable("measurementTask", new HttpTask(new HttpDesc(
          taskParams.key, taskParams.startTime, taskParams.endTime
          , taskParams.intervalSec, taskParams.count, taskParams.priority, taskParams.contextIntervalSec
          , taskParams.params )));
        break;
      case TaskParams.Ping:
        data.putParcelable("measurementTask", new PingTask(new PingDesc(
          taskParams.key, taskParams.startTime, taskParams.endTime
          , taskParams.intervalSec, taskParams.count, taskParams.priority, taskParams.contextIntervalSec
          , taskParams.params )));
        break;
      case TaskParams.Traceroute:
        data.putParcelable("measurementTask", new TracerouteTask(new TracerouteDesc(
          taskParams.key, taskParams.startTime, taskParams.endTime
          , taskParams.intervalSec, taskParams.count, taskParams.priority, taskParams.contextIntervalSec
          , taskParams.params )));
        break;
      default:
        throw new InvalidParameterException("Unknown measurement type");
    }
    return data;
  }

  private Bundle packMultipleTasks( TaskParams taskParam, ArrayList<TaskParams> realTaskParams) {
    taskParam.key = uniqueKey;
    Bundle data = new Bundle();
    ArrayList<MeasurementTask> realTasks = new ArrayList<MeasurementTask>();
    for ( TaskParams param : realTaskParams) {
      param.key = uniqueKey;
      MeasurementTask task = null;
      switch ( param.taskType ) {
        case TaskParams.DNSLookup:
          task = new DnsLookupTask(new DnsLookupDesc(
            param.key, param.startTime, param.endTime
            , param.intervalSec, param.count, param.priority, param.contextIntervalSec
            , param.params ));
          break;
        case TaskParams.HTTP:
          task = new HttpTask(new HttpDesc(
            param.key, param.startTime, param.endTime
            , param.intervalSec, param.count, param.priority, param.contextIntervalSec
            , param.params ));
          break;
        case TaskParams.Ping:
          task = new PingTask(new PingDesc(
            param.key, param.startTime, param.endTime
            , param.intervalSec, param.count, param.priority, param.contextIntervalSec
            , param.params ));
          break;
        case TaskParams.Traceroute:
          task = new TracerouteTask(new TracerouteDesc(
            param.key, param.startTime, param.endTime
            , param.intervalSec, param.count, param.priority, param.contextIntervalSec
            , param.params ));
          break;
        default:
          throw new InvalidParameterException("Unknown measurement type");
      }
      realTasks.add(task);
    }
    switch (taskParam.taskType) {
      case TaskParams.Parallel:
        data.putParcelable("measurementTask", new ParallelTask(new ParallelDesc(
          taskParam.key, taskParam.startTime, taskParam.endTime
          , taskParam.intervalSec, taskParam.count, taskParam.priority, taskParam.contextIntervalSec
          , taskParam.params ), realTasks));
        break;
      case TaskParams.Sequential:
        data.putParcelable("measurementTask", new SequentialTask(new SequentialDesc(
          taskParam.key, taskParam.startTime, taskParam.endTime
          , taskParam.intervalSec, taskParam.count, taskParam.priority, taskParam.contextIntervalSec
          , taskParam.params ), realTasks));
        break;
    }
    return data;
  }
  /**
   * @param taskType
   * @param priority
   * @param param
   * @param handler
   * @return
   */
  public int addTask ( TaskParams taskParams )
          throws InvalidParameterException {
    Messenger currentMessenger = getScheduler();
    if ( currentMessenger != null ) {
      Logger.d("Adding new task");
      Message msg = Message.obtain(null, Config.MSG_SUBMIT_TASK, localId, taskParams.taskType, null);      
      //Bundle data = getData(taskParams);
      Bundle data = packTask(taskParams);
      msg.setData(data);  
      try {
        currentMessenger.send(msg);
      } catch (RemoteException e) {
        Logger.e("remote scheduler failed!");
        return -1;
      }
      return localId++;
    }
    else {
      Logger.d("scheduler doesn't exist");
      return -1;
    }
  }
  
  public int addMultipleTasks( TaskParams taskParam, ArrayList<TaskParams> realTaskParams) {
    Messenger currentMessenger = getScheduler();
    if (currentMessenger != null) {
      Logger.d("Adding new task");
      Message msg = Message.obtain(null, Config.MSG_SUBMIT_TASK, localId, taskParam.taskType, null);
      Bundle data = packMultipleTasks(taskParam, realTaskParams);
      msg.setData(data);
      try {
        currentMessenger.send(msg);
      } catch (RemoteException e) {
        Logger.e("remote scheduler failed!");
        return -1;
      }
      return localId++;
    }
    else {
      Logger.d("schduler doesn't exist");
      return -1;
    }
  }
  
  public int addSequentialTask( TaskParams taskParam ) {
    return localId++;
  }
  /**
   * Cancel the task 
   * @param localId task to be cancelled
   * @return true for succeed, false for fail
   * @throws InvalidParameterException
   */
  public void cancelTask(int localId) throws Exception{
    Messenger currentMessenger = getScheduler();
    if ( currentMessenger != null ) {
      String taskId = localToGlobalMap.get(localId);
      Logger.d("Cancel task! local: " + localId + ", global: " + taskId);
      Message msg = Message.obtain(null, Config.MSG_CANCEL_TASK, 0, 0, null);      
      Bundle data = new Bundle();
      data.putString("taskId", taskId);
      data.putString("clientKey", uniqueKey);
      msg.setData(data);  
      try {
        currentMessenger.send(msg);
      } catch (RemoteException e) {
        Logger.e("remote scheduler failed!");
        throw e;
      }      
    }
    else {
      Logger.d("scheduler doesn't exist");
      throw new Exception();
    }
  }
}
