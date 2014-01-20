package com.mobiperf_library.measurements;



import java.io.InvalidClassException;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.mobiperf_library.Config;
import com.mobiperf_library.MeasurementDesc;
import com.mobiperf_library.MeasurementResult;
import com.mobiperf_library.MeasurementTask;
import com.mobiperf_library.exceptions.MeasurementError;
import com.mobiperf_library.measurements.ParallelTask.ParallelDesc;
import com.mobiperf_library.util.Logger;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

public class SequentialTask extends MeasurementTask{

  private long duration;
  private List<MeasurementTask> tasks;

  private ExecutorService executor;

  // Type name for internal use
  public static final String TYPE = "sequential";
  // Human readable name for the task
  public static final String DESCRIPTOR = "sequential";


  public static class SequentialDesc extends MeasurementDesc {     

    public SequentialDesc(String key, Date startTime,
                          Date endTime, double intervalSec, long count, long priority, int contextIntervalSec,
                          Map<String, String> params) throws InvalidParameterException {
      super(SequentialTask.TYPE, key, startTime, endTime, intervalSec, count,
        priority, contextIntervalSec, params);  
      //      initializeParams(params);

    }

    @Override
    protected void initializeParams(Map<String, String> params) {
    }

    @Override
    public String getType() {
      return SequentialTask.TYPE;
    }  
    
    protected SequentialDesc(Parcel in) {
      super(in);
    }

    public static final Parcelable.Creator<SequentialDesc> CREATOR
    = new Parcelable.Creator<SequentialDesc>() {
      public SequentialDesc createFromParcel(Parcel in) {
        return new SequentialDesc(in);
      }

      public SequentialDesc[] newArray(int size) {
        return new SequentialDesc[size];
      }
    };

    @Override
    public int describeContents() {
      return super.describeContents();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
      super.writeToParcel(dest, flags);
    }
  }

  @SuppressWarnings("rawtypes")
  public static Class getDescClass() throws InvalidClassException {
    return SequentialDesc.class;
  }



  public SequentialTask(MeasurementDesc desc, ArrayList<MeasurementTask> tasks) {
    super(new SequentialDesc(desc.key, desc.startTime, desc.endTime, desc.intervalSec,
      desc.count, desc.priority, desc.contextIntervalSec, desc.parameters));
    this.tasks=(List<MeasurementTask>) tasks.clone();
    executor=Executors.newSingleThreadExecutor();
    long totalduration=0;
    for(MeasurementTask mt: tasks){
      totalduration+=mt.getDuration();

    }
    this.duration=totalduration;

  }
  
  protected SequentialTask(Parcel in) {
    super(in);
    ClassLoader loader = Thread.currentThread().getContextClassLoader();
    MeasurementTask[] tempTasks = (MeasurementTask[])in.readParcelableArray(loader);
    executor = Executors.newFixedThreadPool(tasks.size());
    tasks = new ArrayList<MeasurementTask>();
    long maxDuration = 0;
    for ( MeasurementTask mt : tempTasks ) {
      tasks.add(mt);
      if (mt.getDuration() > maxDuration) {
        maxDuration = mt.getDuration();
      }
    }
    this.duration = maxDuration;
  }

  public static final Parcelable.Creator<SequentialTask> CREATOR
  = new Parcelable.Creator<SequentialTask>() {
    public SequentialTask createFromParcel(Parcel in) {
      return new SequentialTask(in);
    }

    public SequentialTask[] newArray(int size) {
      return new SequentialTask[size];
    }
  };

  @Override
  public int describeContents() {
    return super.describeContents();
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    super.writeToParcel(dest, flags);
    dest.writeParcelableArray((MeasurementTask[])tasks.toArray(), flags);
  }
  

  @Override
  public String getDescriptor() {
    return DESCRIPTOR;
  }

  @Override
  public MeasurementResult[] call() throws MeasurementError {

    ArrayList<MeasurementResult> allresults=new ArrayList<MeasurementResult>();
    try {
      //      futures=executor.invokeAll(this.tasks,timeout,TimeUnit.MILLISECONDS);
      for(MeasurementTask mt: tasks){
        Future<MeasurementResult[]> f=executor.submit(mt);
        MeasurementResult[] r;
        try {
          r = f.get(mt.getDuration()==0?Config.DEFAULT_TASK_DURATION_TIMEOUT*2:mt.getDuration()*2,TimeUnit.MILLISECONDS);
          for(int i=0;i<r.length;i++){//TODO
            allresults.add(r[i]);
          }
        } catch (TimeoutException e) {
          f.cancel(true);//TODO
        }

      }

    } catch (InterruptedException e) {
      Logger.e("Sequential task " + this.getTaskId() + " got interrupted!");
    }catch (ExecutionException e) {
      throw new MeasurementError("Execution error: " + e.getCause());
    }
    finally{
      executor.shutdown();
    }
    
//    MeasurementResult[] tempResults = new MeasurementResult[allresults.size()];
//    int counter = 0;
//    for ( MeasurementResult mr : allresults ) {
//      tempResults[counter++] = mr;
//    }
//    return tempResults;
    return (MeasurementResult[])allresults.toArray(
      new MeasurementResult[allresults.size()]);
  }

  @Override
  public String getType() {
    return SequentialTask.TYPE;
  }

  @Override
  public MeasurementTask clone() {
    MeasurementDesc desc = this.measurementDesc;
    SequentialDesc newDesc = new SequentialDesc(desc.key, desc.startTime, desc.endTime, 
      desc.intervalSec, desc.count, desc.priority, desc.contextIntervalSec, desc.parameters);
    ArrayList<MeasurementTask> newTaskList=new ArrayList<MeasurementTask>();
    for(MeasurementTask mt: tasks){
      newTaskList.add(mt.clone());
    }
    return new SequentialTask(newDesc,newTaskList);
  }

  @Override
  public boolean stop() {
    return false;
  }

  @Override
  public long getDuration() {
    return duration;
  }

  @Override
  public void setDuration(long newDuration) {
    if(newDuration<0){
      this.duration=0;
    }else{
      this.duration=newDuration;
    }
  }

  public MeasurementTask[] getTasks() {
    return tasks.toArray(new MeasurementTask[tasks.size()]);
  }
}
