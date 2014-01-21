package com.mobiperf_library;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.mobiperf_library.MeasurementTask;
import com.mobiperf_library.R;

import com.mobiperf_library.api.API;
import com.mobiperf_library.exceptions.MeasurementError;
import com.mobiperf_library.util.Logger;

import android.os.Bundle;
import android.app.Activity;
import android.content.Intent;
import android.view.Menu;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;


public class MainActivity extends Activity {
  private ListView consoleView;
  private ArrayAdapter<String> resultList;

  private API libraryAPI;
  private int counter = 0;
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Logger.d("MainActivity-> onCreate called");

    setContentView(R.layout.activity_main);
    startService(new Intent(this, MeasurementScheduler.class));

    this.libraryAPI = new API(this, "mykey1") {
      private int counter_temp = 0;
      @Override
      public void handleResults(String taskId, MeasurementResult[] results) {
        if ( results != null ) {
          for ( MeasurementResult r : results ) {
            resultList.insert(r.toString(), 0);
            counter_temp++;
          }
        }
        else {
          resultList.insert("Task failed!", 0);
          counter_temp++;
        }

        resultList.add("Get user result, counter: " + counter_temp);
        runOnUiThread(new Runnable() {
          public void run() { resultList.notifyDataSetChanged(); }
        });

      }
      @Override
      public void handleServerTaskResults(String taskId, MeasurementResult[] results) {
        if ( results != null ) {
          for ( MeasurementResult r : results ) {
            resultList.insert(r.toString(), 0);
            counter_temp++;
          }
        }
        else {
          resultList.insert("Task failed!", 0);
          counter_temp++;
        }

        resultList.add("Get server result, counter: " + counter_temp);
        runOnUiThread(new Runnable() {
          public void run() { resultList.notifyDataSetChanged(); }
        });
        
      }

    };

    this.consoleView = (ListView) this.findViewById(R.id.resultConsole);
    this.resultList = new ArrayAdapter<String>(getApplicationContext(), R.layout.list_item);
    this.consoleView.setAdapter(this.resultList);
    this.findViewById(R.id.resultConsole);
    Button button = (Button)this.findViewById(R.id.close);  
    button.setText("Start Scheduler");  
    
    button.setOnClickListener(new View.OnClickListener()   
    {
      private MeasurementTask prevTask;
      public void onClick(View view) 
      {
        Map<String, String> params = new HashMap<String, String>();
        int taskType = 0;
        int priority = MeasurementTask.USER_PRIORITY;
        Date endTime = null;
        int contextIntervalSec = 1;
        MeasurementTask task = null;
        ArrayList<MeasurementTask> taskList = new ArrayList<MeasurementTask>();
        switch (counter % 5) {   
          case 0:
            taskType = API.UDPBurst;
            params.put("packet_burst", "16");
            //params.put("packet_burst", String.valueOf(50));
            //params.put("direction", "Up");
            //params.put("target","www.google.com");
            //endTime = new Date(System.currentTimeMillis() + 5000L);
            priority = MeasurementTask.INVALID_PRIORITY;

            task = libraryAPI.createTask(taskType
              , Calendar.getInstance().getTime(), endTime, 120, 1
              , priority, contextIntervalSec, params);
            break;
          case 1:
//            try {
//              libraryAPI.cancelTask(prevTask.getTaskId());
//            } catch (Exception e) {
//              // TODO Auto-generated catch block
//              e.printStackTrace();
//            }
            taskType = API.UDPBurst;
            params.put("direction", "Up");
            params.put("packet_burst", "16");
            //params.put("target","www.google.com");
            //endTime = new Date(System.currentTimeMillis() + 5000L);
            task = libraryAPI.createTask(taskType
              , Calendar.getInstance().getTime(), endTime, 120, 1
              , priority, contextIntervalSec, params);
            break;
          case 2:
            taskType = API.HTTP;
            params.put("url","www.google.com");
            //endTime = new Date(System.currentTimeMillis() + 5000L);
            task = libraryAPI.createTask(taskType
              , Calendar.getInstance().getTime(), endTime, 120, 1
              , priority, 1, params);
            taskList.add(task);

            taskType = API.DNSLookup;
            params.put("target","www.google.com");
            //endTime = new Date(System.currentTimeMillis() + 5000L);
            task = libraryAPI.createTask(taskType
              , Calendar.getInstance().getTime(), endTime, 120, 1
              , priority, 1, params);
            taskList.add(task);

            taskType = API.Sequential;
            task = libraryAPI.composeTasks(taskType
              , Calendar.getInstance().getTime(), endTime, 120, 1
              , priority, 1, params, taskList);
            break;  
          case 3:
            taskType = API.Traceroute;
            params.put("target","www.google.com");
            priority = MeasurementTask.INVALID_PRIORITY;
            task = libraryAPI.createTask(taskType
              , Calendar.getInstance().getTime(), endTime, 120, 1
              , priority, 1,  params);
            taskList.add(task);

            taskType = API.Ping;
            params.put("target","www.google.com");
            endTime = new Date(System.currentTimeMillis() + 5000L);
            task = libraryAPI.createTask(taskType
              , Calendar.getInstance().getTime(), endTime, 120, 1
              , priority, 1,  params);
            taskList.add(task);

            taskType = API.Parallel;
            task = libraryAPI.composeTasks(taskType
              , Calendar.getInstance().getTime(), endTime, 120, 1
              , priority, 1,  params, taskList);
            break;   
          case 4:
            taskType = API.Ping;
            params.put("target","www.google.com");
            endTime = new Date(System.currentTimeMillis() + 5000L);

            task = libraryAPI.createTask(taskType
              , Calendar.getInstance().getTime(), endTime, 120, 1
              , priority, contextIntervalSec, params);
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
    super.onDestroy();
  }
}
