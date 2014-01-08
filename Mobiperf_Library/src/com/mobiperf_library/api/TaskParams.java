/**
 * 
 */
package com.mobiperf_library.api;

import java.util.Date;
import java.util.Map;

/**
 * @author Hongyi Yao
 *
 */
public class TaskParams {
  public final static int DNSLookup = 1;
  public final static int HTTP = 2;
  public final static int Ping = 3;
  public final static int Traceroute = 4;
  public final static int TCPThroughput = 5;
  public final static int UDPBurst = 6;
  
  public int taskType;
  // TODO(Hongyi): discuss on those temporary fields
  public String key;
  public Date startTime;
  public Date endTime;
  public double intervalSec;
  public long count;
  public long priority;
  
  Map<String, String> params; 
  public TaskParams( int taskType, String key, Date startTime, Date endTime
    , double intervalSec, long count, long priority, Map<String, String> params) {
    this.taskType = taskType;
    this.key = key;
    this.startTime = startTime;
    this.endTime = endTime;
    this.intervalSec = intervalSec;
    this.count = count;
    this.priority = priority;
    this.params = params;
  }
  
}
