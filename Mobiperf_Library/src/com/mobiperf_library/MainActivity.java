package com.mobiperf_library;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.mobiperf_library.MeasurementTask;
import com.mobiperf_library.R;

import com.mobiperf_library.api.API;
import com.mobiperf_library.api.TaskParams;
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

    this.libraryAPI = new API(this) {
      private int counter_temp = 0;
      @Override
      public void handleResult(MeasurementResult[] results) {
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

        resultList.add("laoyao Counter: " + counter_temp);
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
      private int lastLocalId = -1;
      public void onClick(View view) 
      {
        Map<String, String> params = new HashMap<String, String>();
        int taskType = 0;
        int priority = MeasurementTask.USER_PRIORITY;
        Date endTime = null;
        TaskParams taskparam;
        ArrayList<TaskParams> realTaskParams = new ArrayList<TaskParams>();
        switch (counter % 5) {   
          case 0:
//          try {
//            libraryAPI.cancelTask(lastLocalId);
//          } catch (Exception e) {
//          }
          taskType = TaskParams.DNSLookup;
          params.put("target","www.google.com");
          endTime = new Date(System.currentTimeMillis() + 5000L);
          priority = MeasurementTask.INVALID_PRIORITY;
          taskparam = new TaskParams(taskType, "mykey1"
            , Calendar.getInstance().getTime(), endTime, 120, 1
            , priority, params);
          lastLocalId = libraryAPI.addTask(taskparam);
          break;
          case 1:
//          try {
//            libraryAPI.cancelTask(lastLocalId);
//          } catch (Exception e) {
//          }
          taskType = TaskParams.DNSLookup;
          params.put("target","www.google.com");
          endTime = new Date(System.currentTimeMillis() + 5000L);
          taskparam = new TaskParams(taskType, "mykey1"
            , Calendar.getInstance().getTime(), endTime, 120, 1
            , priority, params);
          lastLocalId = libraryAPI.addTask(taskparam);
          break;
          case 2:
//            try {
//              libraryAPI.cancelTask(lastLocalId);
//            } catch (Exception e) {
//            }
            taskType = TaskParams.HTTP;
            params.put("url","www.google.com");
            endTime = new Date(System.currentTimeMillis() + 5000L);
            taskparam = new TaskParams(taskType, "mykey1"
              , Calendar.getInstance().getTime(), endTime, 120, 1
              , priority, params);
            realTaskParams.add(taskparam);
            
            taskType = TaskParams.DNSLookup;
            params.put("target","www.google.com");
            endTime = new Date(System.currentTimeMillis() + 5000L);
            taskparam = new TaskParams(taskType, "mykey1"
              , Calendar.getInstance().getTime(), endTime, 120, 1
              , priority, params);
            realTaskParams.add(taskparam);
            
            taskType = TaskParams.Sequential;
            taskparam = new TaskParams(taskType, "mykey1"
              , Calendar.getInstance().getTime(), endTime, 120, 1
              , priority, params);
            lastLocalId = libraryAPI.addMultipleTasks(taskparam, realTaskParams);
            break;  
          case 3:
            taskType = TaskParams.Traceroute;
            params.put("target","www.google.com");
            priority = MeasurementTask.INVALID_PRIORITY;
            taskparam = new TaskParams(taskType, "mykey1"
              , Calendar.getInstance().getTime(), endTime, 120, 1
              , priority, params);
            realTaskParams.add(taskparam);
            
            taskType = TaskParams.Ping;
            params.put("target","www.google.com");
            endTime = new Date(System.currentTimeMillis() + 5000L);
            taskparam = new TaskParams(taskType, "mykey1"
              , Calendar.getInstance().getTime(), endTime, 120, 1
              , priority, params);
            realTaskParams.add(taskparam);
            
            taskType = TaskParams.Parallel;
            taskparam = new TaskParams(taskType, "mykey1"
              , Calendar.getInstance().getTime(), endTime, 120, 1
              , priority, params);
            lastLocalId = libraryAPI.addMultipleTasks(taskparam, realTaskParams);
            
//            try {
//              libraryAPI.cancelTask(lastLocalId);
//            } catch (Exception e) {
//            }
            break;   
          case 4:
//          try {
//            libraryAPI.cancelTask(lastLocalId);
//          } catch (Exception e) {
//          }
          taskType = TaskParams.Ping;
          params.put("target","www.google.com");
          endTime = new Date(System.currentTimeMillis() + 5000L);
          taskparam = new TaskParams(taskType, "mykey1"
            , Calendar.getInstance().getTime(), endTime, 120, 1
            , priority, params);
          lastLocalId = libraryAPI.addTask(taskparam);
          break;         
        }
//        TaskParams taskparam = new TaskParams(taskType, "mykey1"
//          , Calendar.getInstance().getTime(), endTime, 120, 1
//          , priority, params);
//        lastLocalId = libraryAPI.addTask(taskparam);
        counter++;
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
