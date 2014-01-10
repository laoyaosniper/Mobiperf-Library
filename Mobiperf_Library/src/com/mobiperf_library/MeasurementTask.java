package com.mobiperf_library;


import java.io.InvalidClassException;
import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.Callable;

import com.mobiperf_library.exceptions.MeasurementError;
import com.mobiperf_library.measurements.DnsLookupTask;
import com.mobiperf_library.measurements.HttpTask;
import com.mobiperf_library.measurements.PingTask;
import com.mobiperf_library.measurements.TestTask;
import com.mobiperf_library.measurements.TracerouteTask;
import com.mobiperf_library.util.Logger;
import com.mobiperf_library.util.Util;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

public abstract class MeasurementTask implements Callable<MeasurementResult[]>, Comparable, Parcelable {
  protected MeasurementDesc measurementDesc;
  //  protected Context parent;


  public static final int USER_PRIORITY = Integer.MIN_VALUE;
  public static final int INVALID_PRIORITY = Integer.MAX_VALUE;
  public static final int INFINITE_COUNT = -1;

  private static HashMap<String, Class> measurementTypes;
  // Maps between the type of task and its readable name
  private static HashMap<String, String> measurementDescToType;

  static {    
    measurementTypes = new HashMap<String, Class>();
    measurementDescToType = new HashMap<String, String>();
    measurementTypes.put(PingTask.TYPE, PingTask.class);
    measurementDescToType.put(PingTask.DESCRIPTOR, PingTask.TYPE);
    measurementTypes.put(HttpTask.TYPE, HttpTask.class);
    measurementDescToType.put(HttpTask.DESCRIPTOR, HttpTask.TYPE);
    measurementTypes.put(TracerouteTask.TYPE, TracerouteTask.class);
    measurementDescToType.put(TracerouteTask.DESCRIPTOR, TracerouteTask.TYPE);
    measurementTypes.put(DnsLookupTask.TYPE, DnsLookupTask.class);
    measurementDescToType.put(DnsLookupTask.DESCRIPTOR, DnsLookupTask.TYPE);
    //      measurementTypes.put(TCPThroughputTask.TYPE, TCPThroughputTask.class);
    //      measurementDescToType.put(TCPThroughputTask.DESCRIPTOR, TCPThroughputTask.TYPE);
  }

  /**
   * @param measurementDesc
   * @param parent
   */
  protected MeasurementTask(MeasurementDesc measurementDesc/*, Context parent*/) {
    super();
    this.measurementDesc = measurementDesc;
    //    this.parent = parent;
  }

  /* Compare priority as the first order. Then compare start time.*/
  @Override
  public int compareTo(Object t) {
    MeasurementTask another = (MeasurementTask) t;

    if (this.measurementDesc.startTime != null && another.measurementDesc.startTime != null) {
      return this.measurementDesc.startTime.compareTo(another.measurementDesc.startTime);
    }
    return 0;
  }  

  public long timeFromExecution() {
    return this.measurementDesc.startTime.getTime() - System.currentTimeMillis();
  }

  public boolean isPassedDeadline() {
    if (this.measurementDesc.endTime == null) { 
      return false;
    } else {
      long endTime = this.measurementDesc.endTime.getTime();
      return endTime <= System.currentTimeMillis();
    }
  }

  public String getMeasurementType() {
    return this.measurementDesc.type;
  }

  public String getKey(){
    return this.measurementDesc.key;
  }

  public void setKey(String key){
    this.measurementDesc.key=key;
  }


  public MeasurementDesc getDescription() {
    return this.measurementDesc;
  }

  /** Gets the currently available measurement descriptions*/
  public static Set<String> getMeasurementNames() {
    return measurementDescToType.keySet();
  }

  /** Gets the currently available measurement types*/
  public static Set<String> getMeasurementTypes() {
    return measurementTypes.keySet();
  }

  /** Get the type of a measurement based on its name. Type is for JSON interface only
   * where as measurement name is a readable string for the UI */
  public static String getTypeForMeasurementName(String name) {
    return measurementDescToType.get(name);
  }

  public static Class getTaskClassForMeasurement(String type) {
    return measurementTypes.get(type);
  }

  /* This is put here for consistency that all MeasurementTask should
   * have a getDescClassForMeasurement() method. However, the MeasurementDesc is abstract 
   * and cannot be instantiated */
  public static Class getDescClass() throws InvalidClassException {
    throw new InvalidClassException("getDescClass() should only be invoked on "
        + "subclasses of MeasurementTask.");
  }

  /**
   * Returns a brief human-readable descriptor of the task.
   */
  public abstract String getDescriptor();

  @Override
  public abstract MeasurementResult[] call() throws MeasurementError; 

  /** Return the string indicating the measurement type. */
  public abstract String getType();



  //    @Override//TODO
  //    public String toString() {
  //      String result = "[Measurement " + getDescriptor() + " scheduled to run at " + 
  //          getDescription().startTime + "]";
  //      
  //      return this.measurementDesc.toString();
  //    }

  @Override
  public abstract MeasurementTask clone();

  /**
   * Stop the measurement, even when it is running. There is no side effect 
   * if the measurement has not started or is already finished.
   */
  public abstract boolean stop();


  public abstract long getDuration();
  public abstract void setDuration( long newDuration);



  @Override
  public boolean equals(Object o) {
    MeasurementTask another=(MeasurementTask)o;
    if(this.getDescription().equals(another.getDescription()) && this.getType().equals(another.getType())){
      return true;
    }
    return false;
  }

  @Override
  public int hashCode() {
    StringBuilder taskstrbld=new StringBuilder(getMeasurementType());
    taskstrbld.append(",").append(this.measurementDesc.key).append(",").append(this.measurementDesc.startTime)
    .append(",").append(this.measurementDesc.endTime).append(",").append(this.measurementDesc.intervalSec)
    .append(",").append(this.measurementDesc.priority);
    //.append(",").append(this.measurementDesc.count)

    Object [] keys=this.measurementDesc.parameters.keySet().toArray();
    Arrays.sort(keys);
    for(Object k : keys){
      taskstrbld.append(",").append(this.measurementDesc.parameters.get((String)k));
    }

    return taskstrbld.toString().hashCode();

    //    BigInteger bigInt=null;
    //    try {
    //      bigInt=Util.getMD5(taskstr);
    //    } catch (NoSuchAlgorithmException e) {
    //      Logger.e("Error in getting the MD5 hash");
    //      return -1;
    //    }

    //    return bigInt.intValue();


  }

  public String generateTaskID(){
    //    String taskstr=getMeasurementType()+this.measurementDesc.key+this.measurementDesc.startTime+this.measurementDesc.endTime+
    //        this.measurementDesc.intervalSec+this.measurementDesc.count+this.measurementDesc.priority;
    //    Object [] keys=this.measurementDesc.parameters.keySet().toArray();
    //    Arrays.sort(keys);
    //    for(Object k : keys){
    //      taskstr+=k+this.measurementDesc.parameters.get((String)k);
    //    }
    //    BigInteger bigInt=null;
    //    try {
    //      bigInt=Util.getMD5(taskstr);
    //    } catch (NoSuchAlgorithmException e) {
    //      Logger.e("Error in getting the MD5 hash");
    //      return null;
    //    }
    //    return bigInt.toString();
    return this.hashCode()+"";

  }
  
  protected MeasurementTask(Parcel in) {
    ClassLoader loader = Thread.currentThread().getContextClassLoader();
    measurementDesc = in.readParcelable(loader);
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeParcelable(measurementDesc, flags);
  }
  
}
