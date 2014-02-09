package com.mobiperf_library;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.mobiperf_library.MeasurementResult;
import com.mobiperf_library.MeasurementScheduler;
import com.mobiperf_library.MeasurementTask;
import com.mobiperf_library.R;
import com.mobiperf_library.UpdateIntent;

import com.mobiperf_library.api.API;
import com.mobiperf_library.exceptions.MeasurementError;
import com.mobiperf_library.util.Logger;

import android.os.Bundle;
import android.os.Parcelable;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.view.Menu;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;


public class MainActivity extends Activity {
  private ListView consoleView;
  private ArrayAdapter<String> resultList;

  private API libraryAPI;
  private BroadcastReceiver broadcastReceiver;
  private int counter = 0;
  private String clientKey;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Logger.d("MainActivity-> onCreate called");

    setContentView(R.layout.activity_main);
    startService(new Intent(this, MeasurementScheduler.class));

    this.clientKey = "myKey0";
    this.libraryAPI = API.getAPI(this, clientKey);

    IntentFilter filter = new IntentFilter();
    filter.addAction(libraryAPI.userResultAction);
    filter.addAction(libraryAPI.serverResultAction);
    broadcastReceiver = new BroadcastReceiver() {
      private int counter_temp = 0;

      @Override
      public void onReceive(Context context, Intent intent) {
        long ts_api_recv = System.currentTimeMillis();
        long ts_scheduler_send = intent.getLongExtra("ts_scheduler_send", 0l);
        Logger.e("New Library: Received intent");
        Parcelable[] parcels = intent.getParcelableArrayExtra(UpdateIntent.RESULT_PAYLOAD);
        MeasurementResult[] results = null;
        if (parcels != null) {
          results = new MeasurementResult[parcels.length];
          for (int i = 0; i < results.length; i++) {
            results[i] = (MeasurementResult) parcels[i];
          }
        }

        if (results != null) {
          for (MeasurementResult r : results) {
            resultList.insert(r.toString(), 0);
            counter_temp++;
            long api2scheduler =
                Long.parseLong(r.getParameter("ts_scheduler_recv"))
                    - Long.parseLong(r.getParameter("ts_api_send"));
            long scheduler2api = ts_api_recv - ts_scheduler_send;
            long end2end = ts_api_recv - Long.parseLong(r.getParameter("ts_api_send"));
            Logger.e("MARKER 0 " + api2scheduler + " " + scheduler2api + " " + end2end);
          }
        } else {
          resultList.insert("Task failed!", 0);
          counter_temp++;
        }

        if (intent.getAction().equals(libraryAPI.userResultAction)) {
          resultList.add("Get user result, counter: " + counter_temp);
        } else if (intent.getAction().equals(libraryAPI.serverResultAction)) {
          resultList.add("Get server result, counter: " + counter_temp);
        }
        runOnUiThread(new Runnable() {
          public void run() {
            resultList.notifyDataSetChanged();
          }
        });

      }

    };

    this.registerReceiver(broadcastReceiver, filter);
    // this.libraryAPI = new API(this, "mykey1")
    // {
    // private int counter_temp = 0;
    // @Override
    // public void handleResults(String taskId, MeasurementResult[] results) {
    // if ( results != null ) {
    // for ( MeasurementResult r : results ) {
    // resultList.insert(r.toString(), 0);
    // counter_temp++;
    // }
    // }
    // else {
    // resultList.insert("Task failed!", 0);
    // counter_temp++;
    // }
    //
    // resultList.add("Get user result, counter: " + counter_temp);
    // runOnUiThread(new Runnable() {
    // public void run() { resultList.notifyDataSetChanged(); }
    // });
    //
    // }
    // @Override
    // public void handleServerTaskResults(String taskId, MeasurementResult[] results) {
    // if ( results != null ) {
    // for ( MeasurementResult r : results ) {
    // resultList.insert(r.toString(), 0);
    // counter_temp++;
    // }
    // }
    // else {
    // resultList.insert("Task failed!", 0);
    // counter_temp++;
    // }
    //
    // resultList.add("Get server result, counter: " + counter_temp);
    // runOnUiThread(new Runnable() {
    // public void run() { resultList.notifyDataSetChanged(); }
    // });
    //
    // }
    //
    // };

    this.consoleView = (ListView) this.findViewById(R.id.resultConsole);
    this.resultList = new ArrayAdapter<String>(getApplicationContext(), R.layout.list_item);
    this.consoleView.setAdapter(this.resultList);
    this.findViewById(R.id.resultConsole);
    Button button = (Button) this.findViewById(R.id.close);
    button.setText("Start Scheduler");

    button.setOnClickListener(new View.OnClickListener() {
      private MeasurementTask prevTask;

      public void onClick(View view) {
        Map<String, String> params = new HashMap<String, String>();
        int taskType = 0;
        int priority = MeasurementTask.USER_PRIORITY;
        Date endTime = null;
        int contextIntervalSec = 1;
        MeasurementTask task = null;
        ArrayList<MeasurementTask> taskList = new ArrayList<MeasurementTask>();
        switch (counter % 5) {
          case 0:
            taskType = API.DNSLookup;
            params.put("target", "www.google.com");
            // endTime = new Date(System.currentTimeMillis() + 5000L);
            task =
                libraryAPI.createTask(taskType, Calendar.getInstance().getTime(), endTime, 120, 1,
                    priority, 1, params);

            // taskType = API.UDPBurst;
            // params.put("packet_burst", "16");
            // //params.put("packet_burst", String.valueOf(50));
            // //params.put("direction", "Up");
            // //params.put("target","www.google.com");
            // //endTime = new Date(System.currentTimeMillis() + 5000L);
            // priority = MeasurementTask.INVALID_PRIORITY;
            //
            // task = libraryAPI.createTask(taskType
            // , Calendar.getInstance().getTime(), endTime, 120, 1
            // , priority, contextIntervalSec, params);
            break;
          case 1:
            taskType = API.HTTP;
            params.put("url", "www.google.com");
            // endTime = new Date(System.currentTimeMillis() + 5000L);
            task =
                libraryAPI.createTask(taskType, Calendar.getInstance().getTime(), endTime, 120, 1,
                    priority, 1, params);
            // try {
            // libraryAPI.cancelTask(prevTask.getTaskId());
            // } catch (Exception e) {
            // // TODO Auto-generated catch block
            // e.printStackTrace();
            // }
            // taskType = API.UDPBurst;
            // params.put("direction", "Up");
            // params.put("packet_burst", "16");
            // //params.put("target","www.google.com");
            // //endTime = new Date(System.currentTimeMillis() + 5000L);
            // task = libraryAPI.createTask(taskType
            // , Calendar.getInstance().getTime(), endTime, 120, 1
            // , priority, contextIntervalSec, params);
            break;
          case 2:
            taskType = API.HTTP;
            params.put("url", "www.google.com");
            // endTime = new Date(System.currentTimeMillis() + 5000L);
            task =
                libraryAPI.createTask(taskType, Calendar.getInstance().getTime(), endTime, 120, 1,
                    priority, 1, params);
            taskList.add(task);

            taskType = API.DNSLookup;
            params.put("target", "www.google.com");
            // endTime = new Date(System.currentTimeMillis() + 5000L);
            task =
                libraryAPI.createTask(taskType, Calendar.getInstance().getTime(), endTime, 120, 1,
                    priority, 1, params);
            taskList.add(task);

            taskType = API.Sequential;
            task =
                libraryAPI.composeTasks(taskType, Calendar.getInstance().getTime(), endTime, 120,
                    1, priority, 1, params, taskList);
            break;
          case 3:
            taskType = API.Traceroute;
            params.put("target", "www.google.com");
            priority = MeasurementTask.INVALID_PRIORITY;
            task =
                libraryAPI.createTask(taskType, Calendar.getInstance().getTime(), endTime, 120, 1,
                    priority, 1, params);
            taskList.add(task);

            taskType = API.Ping;
            params.put("target", "www.google.com");
            endTime = new Date(System.currentTimeMillis() + 5000L);
            task =
                libraryAPI.createTask(taskType, Calendar.getInstance().getTime(), endTime, 120, 1,
                    priority, 1, params);
            taskList.add(task);

            taskType = API.Parallel;
            task =
                libraryAPI.composeTasks(taskType, Calendar.getInstance().getTime(), endTime, 120,
                    1, priority, 1, params, taskList);
            break;
          case 4:
            taskType = API.Ping;
            params.put("target", "www.google.com");
            endTime = new Date(System.currentTimeMillis() + 5000L);

            task =
                libraryAPI.createTask(taskType, Calendar.getInstance().getTime(), endTime, 120, 1,
                    priority, contextIntervalSec, params);
            break;

        }
        counter++;
        prevTask = task;

        try {
          libraryAPI.addTask(task);
        } catch (MeasurementError e) {
          Logger.e(e.getMessage());
        }
      }
    });
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    // Inflate the menu; this adds items to the action bar if it is present.
    getMenuInflater().inflate(R.menu.main, menu);
    return true;
  }

  @Override
  protected void onStart() {
    Logger.d("MainActivity-> onStart called");
    super.onStart();
  }

  @Override
  protected void onResume() {
    Logger.d("MainActivity-> onResume called");
    libraryAPI.bind();
    super.onResume();
  }

  @Override
  protected void onStop() {
    Logger.d("MainActivity -> onStop called");
    libraryAPI.unbind();
    super.onStop();
  }

  @Override
  protected void onDestroy() {
    Logger.d("MainActivity -> onDestroy called");
    this.unregisterReceiver(broadcastReceiver);
    super.onDestroy();
  }
}
