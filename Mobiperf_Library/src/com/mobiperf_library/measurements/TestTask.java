package com.mobiperf_library.measurements;


import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Map;

import android.os.Parcel;
import android.os.Parcelable;

import com.mobiperf_library.Config;
import com.mobiperf_library.MeasurementDesc;
import com.mobiperf_library.MeasurementResult;
import com.mobiperf_library.MeasurementResult.TaskProgress;
import com.mobiperf_library.MeasurementTask;
import com.mobiperf_library.PreemptibleMeasurementTask;
import com.mobiperf_library.exceptions.MeasurementError;
import com.mobiperf_library.util.Logger;
import com.mobiperf_library.util.PhoneUtils;



public class TestTask extends MeasurementTask implements PreemptibleMeasurementTask{
  public static final String TYPE = "test";
  public static final String DESCRIPTOR = "test";

  private volatile boolean stopFlag;
  private volatile boolean pauseFlag;

  private long duration;

  public ArrayList<Double> resultsArray;
  TaskProgress taskProgress;

  public TestTask(MeasurementDesc desc) {
    super(new TestDesc(desc.key, desc.startTime, desc.endTime, desc.intervalSec,
      desc.count, desc.priority, desc.parameters));
    this.stopFlag=false;
    this.pauseFlag=false;
    resultsArray=new ArrayList<Double>();
    duration=((TestDesc) measurementDesc).num*1000;
    taskProgress=TaskProgress.FAILED;

  }


  public static class TestDesc extends MeasurementDesc {    


    public int num;
    public TestDesc(String key, Date startTime, Date endTime, double intervalSec, long count, long priority, 
                    Map<String, String> params) throws InvalidParameterException {
      super(TestTask.TYPE, key, startTime, endTime, intervalSec, count,
        priority, params);
      initializeParams(params);

      if (this.num == 0){
        throw new InvalidParameterException("TestTask cannot be created due to ...");
      }    
    }
    @Override
    public String getType() {
      return TestTask.TYPE;
    }

    @Override
    protected void initializeParams(Map<String, String> params) {

      if (params != null) {
        this.num = Integer.parseInt(params.get("num"));

      }

    }

    protected TestDesc(Parcel in) {
      super(in);
      num = in.readInt();
    }

    public static final Parcelable.Creator<TestDesc> CREATOR
    = new Parcelable.Creator<TestDesc>() {
      public TestDesc createFromParcel(Parcel in) {
        return new TestDesc(in);
      }

      public TestDesc[] newArray(int size) {
        return new TestDesc[size];
      }
    };

    @Override
    public int describeContents() {
      return super.describeContents();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
      super.writeToParcel(dest, flags);
      dest.writeInt(num);
    }
  }


  @Override
  public String getDescriptor() {
    return DESCRIPTOR;
  }

  @Override
  public MeasurementResult[] call() throws MeasurementError {
    stopFlag=false;
    pauseFlag=false;
    taskProgress=TaskProgress.FAILED;
    MeasurementResult[] result = null;
    TestDesc desc=(TestDesc) measurementDesc;

    String res="";
    for(int i=resultsArray.size();i<desc.num ;i++){
      synchronized (this) {
        if(stopFlag){
          taskProgress=TaskProgress.FAILED;
          break;
        }else if(pauseFlag){
          taskProgress=TaskProgress.PAUSED;
          break;
        }
      }
      Logger.i(i+" "+desc.key);
      res=i+"";
      resultsArray.add((double) i);
      try{
        Thread.sleep(1000);}
      catch(InterruptedException e){
        Logger.e("Intruppted ");
      }

    }


    if(taskProgress!=TaskProgress.FAILED && taskProgress!=TaskProgress.PAUSED){
      taskProgress=TaskProgress.COMPLETED;
    }

    PhoneUtils phoneUtils = PhoneUtils.getPhoneUtils();

    //    result=new MeasurementResult(phoneUtils.getDeviceInfo().deviceId,
    //        phoneUtils.getDeviceProperty(), TestTask.TYPE, System.currentTimeMillis() * 1000,
    //        tp, this.measurementDesc);//TODO
    result=new MeasurementResult[1];
    result[0]=new MeasurementResult(phoneUtils.getDeviceInfo().deviceId,
      null, TestTask.TYPE, System.currentTimeMillis() * 1000,
      taskProgress, this.measurementDesc, taskId);


    return result;
  }


  @Override
  public String getType() {
    return TestTask.TYPE;

  }


  @Override
  public MeasurementTask clone() {
    MeasurementDesc desc = this.measurementDesc;
    TestDesc newDesc = new TestDesc(desc.key, desc.startTime, desc.endTime, 
      desc.intervalSec, desc.count, desc.priority, desc.parameters);
    return new TestTask(newDesc);
  }


  @Override
  public boolean stop() {
    //    Logger.e(this.generateTaskID()+" stopped "+resultsArray.size());
    resultsArray.clear();
    this.stopFlag=true;
    return true;
  }

  @Override
  public boolean pause() {
    this.pauseFlag=true;
    return true;
  }


  //  @Override
  //  public void resume() {
  //    // TODO Auto-generated method stub
  //
  //  }


  @Override
  public long getDuration() {
    return this.duration;
  }


  @Override
  public void setDuration(long newDuration) {
    if(newDuration<0){
      this.duration=0;
    }else{
      this.duration=newDuration;
    }


  }
}
