package com.mobiperf_library;

import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
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
  private ArrayAdapter<String> results;

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
      public void handleResult(MeasurementResult result) {
        if ( result != null ) {
          results.insert(result.toString(), 0);
        }
        else {
          results.insert("Task failed!", 0);
        }
        counter_temp++;

        results.add("laoyao Counter: " + counter_temp);
        runOnUiThread(new Runnable() {
          public void run() { results.notifyDataSetChanged(); }
        });

      }

    };

    this.consoleView = (ListView) this.findViewById(R.id.resultConsole);
    this.results = new ArrayAdapter<String>(getApplicationContext(), R.layout.list_item);
    this.consoleView.setAdapter(this.results);
    this.findViewById(R.id.resultConsole);
    Button button = (Button)this.findViewById(R.id.close);  
    button.setText("Start Scheduler");  
    button.setOnClickListener(new View.OnClickListener()   
    {
      public void onClick(View view) 
      {
        Map<String, String> params = new HashMap<String, String>();
        int taskType = 0;
        switch (counter % 4) {
          case 0:
            taskType = TaskParams.DNSLookup;
            params.put("target","www.google.com");
            break;
          case 1:
            taskType = TaskParams.HTTP;
            params.put("url","www.google.com");
            break;            
          case 2:
            taskType = TaskParams.Ping;
            params.put("target","www.google.com");
            break;            
          case 3:
            taskType = TaskParams.Traceroute;
            params.put("target","www.google.com");
            break;
        }
        TaskParams taskparam = new TaskParams(taskType, "mykey1"
          , Calendar.getInstance().getTime(), null, 120, 1
          , MeasurementTask.USER_PRIORITY+1,params);
        libraryAPI.addTask(taskparam);
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
