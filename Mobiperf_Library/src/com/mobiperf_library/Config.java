package com.mobiperf_library;


/**
 * The system defaults.
 */

public interface Config {

  /**
   * Strings migrated from string.xml
   */
  public static final String SERVER_URL = "https://openmobiledata.appspot.com";
  public static final String ANONYMOUS_SERVER_URL = "https://openmobiledata.appspot.com/anonymous";
  public static final String TEST_SERVER_URL = "";
  public static final String DEFAULT_USER = "Anonymous";

  public static final int MAX_TASK_QUEUE_SIZE = 100;

  public static final String USER_AGENT = "Linux; Android";
  public static final String DEFAULT_USER_AGENT = "MobiPerf-2.0 (Linux; Android)";
  public static final String PING_EXECUTABLE = "ping";
  public static final String PING6_EXECUTABLE = "ping6";
  
  public static final String SERVER_TASK_CLIENT_KEY = "LibraryServerTask";

  public static final String TASK_STARTED = "TASK_STARTED";
  public static final String TASK_FINISHED = "TASK_FINISHED";
  public static final String TASK_PAUSED = "TASK_PAUSED";
  public static final String TASK_RESUMED = "TASK_RESUMED";
  public static final String TASK_CANCELED = "TASK_CENCELED";
  public static final String TASK_STOPPED = "TASK_STOPPED";

  public static final int MIN_BATTERY_THRESHOLD = 20;


  /** Types for message between API and scheduler**/
  public static final int MSG_SUBMIT_TASK = 1;
  public static final int MSG_RESULT = 2;
  public static final int MSG_CANCEL_TASK = 3;

  /** The default battery level if we cannot read it from the system */
  public static final int DEFAULT_BATTERY_LEVEL = 0;
  /** The default maximum battery level if we cannot read it from the system */
  public static final int DEFAULT_BATTERY_SCALE = 100;



  /** Tasks expire in one day. Expired tasks will be removed from the scheduler */
  public static final long TASK_EXPIRATION_MSEC = 24 * 3600 * 1000;
  /** Default interval in seconds between system measurements of a given measurement type */
  public static final double DEFAULT_SYSTEM_MEASUREMENT_INTERVAL_SEC = 15 * 60;
  /** Default interval in seconds between context collection */
  public static final int DEFAULT_CONTEXT_INTERVAL_SEC = 1;
  public static final int MAX_CONTEXT_INFO_COLLECTIONS_PER_TASK = 120;



  // TODO check these static values
  public static final int DEFAULT_DNS_COUNT_PER_MEASUREMENT = 1;
  public static final int PING_COUNT_PER_MEASUREMENT = 10;
  public static final float PING_FILTER_THRES = (float) 1.4;
  public static final double DEFAULT_INTERVAL_BETWEEN_ICMP_PACKET_SEC = 0.5;


  public static final int TRACEROUTE_TASK_DURATION = 30 * 500;
  public static final int DEFAULT_DNS_TASK_DURATION = 0;
  public static final int DEFAULT_HTTP_TASK_DURATION = 0;
  public static final int DEFAULT_PING_TASK_DURATION = PING_COUNT_PER_MEASUREMENT * 500;
  public static final int DEFAULT_UDPBURST_DURATION = 1000;
  public static final int DEFAULT_PARALLEL_TASK_DURATION = 5 * 1000;
  public static final int DEFAULT_TASK_DURATION_TIMEOUT = 5 * 1000;


  // Checkin
  public static final String PREF_KEY_SELECTED_ACCOUNT = "PREF_KEY_SELECTED_ACCOUNT";


  // The default checkin interval in seconds
  public static final long DEFAULT_CHECKIN_INTERVAL_SEC = 60 * 60L;
  public static final long MIN_CHECKIN_RETRY_INTERVAL_SEC = 20L;
  public static final long MAX_CHECKIN_RETRY_INTERVAL_SEC = 60L;
  public static final int MAX_CHECKIN_RETRY_COUNT = 3;
  public static final long PAUSE_BETWEEN_CHECKIN_CHANGE_MSEC = 10 * 1000L;
  public static final long MIN_CHECKIN_INTERVAL_SEC = 3600;
}
