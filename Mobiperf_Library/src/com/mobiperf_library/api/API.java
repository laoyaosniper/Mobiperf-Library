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
import java.util.Date;
import java.util.Map;

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
import com.mobiperf_library.Config;
import com.mobiperf_library.MeasurementResult;
import com.mobiperf_library.MeasurementScheduler;
import com.mobiperf_library.MeasurementTask;
import com.mobiperf_library.UpdateIntent;
import com.mobiperf_library.exceptions.MeasurementError;
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
import com.mobiperf_library.measurements.TCPThroughputTask;
import com.mobiperf_library.measurements.TCPThroughputTask.TCPThroughputDesc;
import com.mobiperf_library.measurements.TracerouteTask;
import com.mobiperf_library.measurements.TracerouteTask.TracerouteDesc;
import com.mobiperf_library.measurements.UDPBurstTask;
import com.mobiperf_library.measurements.UDPBurstTask.UDPBurstDesc;
import com.mobiperf_library.util.Logger;

/**
 * @author jackjia,Hongyi Yao (hyyao@umich.edu)
 * The user API for Mobiperf library. Use singleton design pattern to ensure
 * that there only exist one instance of API
 * User: add task => Scheduler: run task, send finish event
 * => User: implement OnResultReturn to get result
 * thread safe
 * The client and server change TaskID as a Long type.
 */
public final class API {
  public final static int DNSLookup = 1;
  public final static int HTTP = 2;
  public final static int Ping = 3;
  public final static int Traceroute = 4;
  public final static int TCPThroughput = 5;
  public final static int UDPBurst = 6;
  
  public final static int Parallel = 101;
  public final static int Sequential = 102;

  private Context parent;
  
  private boolean isBound = false;
  private boolean isBindingToService = false;
  Messenger mSchedulerMessenger = null;
  
  private String clientKey;
  
  private static API apiObject;
  
  private API(Context parent, String clientKey) {
    this.parent = parent;
    this.clientKey = clientKey;
    bind();
  }

  public static API getAPI(Context parent, String clientKey) {
    if ( apiObject == null ) {
      apiObject = new API(parent, clientKey);
    }
    return apiObject;
  }
  
  @Override
  public Object clone() throws CloneNotSupportedException {
    throw new CloneNotSupportedException();
  }
  
  /**
   * Handler of incoming messages from service.
   */
  class IncomingHandler extends Handler {
    @Override
    public void handleMessage(Message msg) {
      Bundle data = msg.getData();
      data.setClassLoader(MeasurementScheduler.class.getClassLoader());
      
      String taskId;
      int priority;
      MeasurementResult[] results;
      switch (msg.what) {
        case Config.MSG_SEND_RESULT:
          Parcelable[] parcels = data.getParcelableArray("results");
          if ( parcels != null ) {
            results = new MeasurementResult[parcels.length];
            for ( int i = 0; i < results.length; i++ ) {
              results[i] = (MeasurementResult) parcels[i];
            }

            taskId = data.getString("taskId");
            priority = data.getInt("priority");
            
            Intent intent = new Intent();
            if ( priority == MeasurementTask.USER_PRIORITY ) {
              intent.setAction(UpdateIntent.USER_RESULT_ACTION);
//              handleResults(taskId, results);
            }
            else {
              intent.setAction(UpdateIntent.SERVER_RESULT_ACTION);
//              handleServerTaskResults(taskId, results);
            }
            intent.putExtra(UpdateIntent.TASKID_PAYLOAD, taskId);
            intent.putExtra(UpdateIntent.RESULT_PAYLOAD, parcels);
            parent.sendBroadcast(intent);
          }
          break;
        default:
          super.handleMessage(msg);
      }
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
      data.putString("clientKey", clientKey);
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
          bind();
          // TODO(Hongyi): currently always return null
          if ( isBound ) {
            return mSchedulerMessenger;
          }
          else {
            return null;
          }
      }
  }
  
  public void bind() {
    Logger.d("MainActivity-> bindToService called");
    if (!isBindingToService && !isBound) {
        // Bind to the scheduler service if it is not bounded
        Intent intent = new Intent("com.mobiperf_library.MeasurementScheduler");
        parent.bindService(intent, serviceConn, Context.BIND_AUTO_CREATE);
        isBindingToService = true;
    }
  }
  
  public void unbind() {
    if (isBound) {
      // Hongyi: unregister client messenger in the service
      Message msg = Message.obtain(null, Config.MSG_UNREGISTER_CLIENT);
      Bundle data = new Bundle();
      data.putString("clientKey", clientKey);
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

  public MeasurementTask createTask( int taskType, Date startTime
    , Date endTime, double intervalSec, long count, long priority
    , int contextIntervalSec, Map<String, String> params) {
    MeasurementTask task = null;    
    switch ( taskType ) {
      case API.DNSLookup:
        task = new DnsLookupTask(new DnsLookupDesc(clientKey, startTime, endTime
          , intervalSec, count, priority, contextIntervalSec, params));
        break;
      case API.HTTP:
        task = new HttpTask(new HttpDesc(clientKey, startTime, endTime
          , intervalSec, count, priority, contextIntervalSec, params));
        break;
      case API.Ping:
        task = new PingTask(new PingDesc(clientKey, startTime, endTime
          , intervalSec, count, priority, contextIntervalSec, params));
        break;
      case API.Traceroute:
        task = new TracerouteTask(new TracerouteDesc(clientKey, startTime, endTime
          , intervalSec, count, priority, contextIntervalSec, params));
        break;
      case API.TCPThroughput:
        task = new TCPThroughputTask(new TCPThroughputDesc(clientKey, startTime
          , endTime, intervalSec, count, priority, contextIntervalSec, params));
        break;
      case API.UDPBurst:
        task = new UDPBurstTask(new UDPBurstDesc(clientKey, startTime, endTime
          , intervalSec, count, priority, contextIntervalSec, params));
        break;
      default:
          break;
    }
    return task;
  }
  
  public MeasurementTask composeTasks(int manner, Date startTime
    , Date endTime, double intervalSec, long count, long priority
    , int contextIntervalSec, Map<String, String> params
    , ArrayList<MeasurementTask> taskList) {
    MeasurementTask task = null;
    switch ( manner ) {
      case Parallel:
        task = new ParallelTask(new ParallelDesc(clientKey, startTime, endTime
          , intervalSec, count, priority, contextIntervalSec, params), taskList);
        break;
      case Sequential:
        task = new SequentialTask(new SequentialDesc(clientKey, startTime, endTime
          , intervalSec, count, priority, contextIntervalSec, params), taskList);
        break;
      default:
          break;
    }
    return task;
  }
 
  public void addTask ( MeasurementTask task )
      throws MeasurementError {
    Messenger messenger = getScheduler();
    if ( messenger != null ) {
      Logger.d("Adding new task");
      Message msg = Message.obtain(null, Config.MSG_SUBMIT_TASK);
      Bundle data = new Bundle();
      if ( task != null ) {
        data.putParcelable("measurementTask", task);
        msg.setData(data);  
        try {
          messenger.send(msg);
        } catch (RemoteException e) {
          String err = "remote scheduler failed!";
          Logger.e(err);
          throw new MeasurementError(err);
        }
      }
    }
    else {
      String err = "scheduler doesn't exist";
      Logger.e(err);
      throw new MeasurementError(err);
    }
  }

  /**
   * Cancel the task 
   * @param localId task to be cancelled
   * @return true for succeed, false for fail
   * @throws InvalidParameterException
   */
  public void cancelTask(String taskId) throws MeasurementError{
    Messenger messenger = getScheduler();
    if ( messenger != null ) {
      Message msg = Message.obtain(null, Config.MSG_CANCEL_TASK);      
      Bundle data = new Bundle();
      Logger.d("API: CANCEL task " + taskId);
      data.putString("taskId", taskId);
      data.putString("clientKey", clientKey);
      msg.setData(data);  
      try {
        messenger.send(msg);
      } catch (RemoteException e) {
        String err = "remote scheduler failed!";
        Logger.e(err);
        throw new MeasurementError(err);
      }      
    }
    else {
      String err = "scheduler doesn't exist";
      Logger.e(err);
      throw new MeasurementError(err);
    }
  }

}
