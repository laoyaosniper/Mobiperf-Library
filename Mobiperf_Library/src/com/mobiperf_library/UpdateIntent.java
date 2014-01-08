package com.mobiperf_library;



import java.security.InvalidParameterException;

import android.content.Intent;
/**
 * A repackaged Intent class that includes MobiLib-specific information. 
 */
public class UpdateIntent extends Intent {
  
  // Different types of payloads that this intent can carry:
  public static final String MSG_PAYLOAD = "MSG_PAYLOAD";
  public static final String RESULT_STRING_PAYLOAD = "RESULT_STRING_PAYLOAD";
  public static final String ERROR_STRING_PAYLOAD = "ERROR_STRING_PAYLOAD";
  public static final String TASK_STATUS_PAYLOAD = "TASK_STATUS_PAYLOAD";
  public static final String TASKID_PAYLOAD = "TASKID_PAYLOAD";
  public static final String TASK_PRIORITY_PAYLOAD = "TASK_PRIORITY_PAYLOAD";//TODO Do we need this?
  
  
  // Different types of actions that this intent can represent:
  private static final String PACKAGE_PREFIX = UpdateIntent.class.getPackage().getName();
//  public static final String MSG_ACTION = PACKAGE_PREFIX + ".MSG_ACTION";
//  public static final String PREFERENCE_ACTION = PACKAGE_PREFIX + ".PREFERENCE_ACTION";
  public static final String MEASUREMENT_ACTION = PACKAGE_PREFIX + ".MEASUREMENT_ACTION";
  public static final String CHECKIN_ACTION = PACKAGE_PREFIX + ".CHECKIN_ACTION";
  public static final String CHECKIN_RETRY_ACTION = PACKAGE_PREFIX + ".CHECKIN_RETRY_ACTION";
  public static final String MEASUREMENT_PROGRESS_UPDATE_ACTION = PACKAGE_PREFIX + ".MEASUREMENT_PROGRESS_UPDATE_ACTION";
//  public static final String SYSTEM_STATUS_UPDATE_ACTION = PACKAGE_PREFIX + ".SYSTEM_STATUS_UPDATE_ACTION";
//  public static final String SCHEDULER_CONNECTED_ACTION = PACKAGE_PREFIX + ".SCHEDULER_CONNECTED_ACTION";
//  public static final String SCHEDULE_UPDATE_ACTION = PACKAGE_PREFIX + ".SCHEDULE_UPDATE_ACTION";
//  
  // TODO(Hongyi): make it formal
  public static final String APP_ACTION = PACKAGE_PREFIX + ".APP_ACTION";
  /**
   * Creates an intent of the specified action with an optional message
   */
  protected UpdateIntent(String strMsg, String action) throws InvalidParameterException {
    super();
    if (action == null) {
      throw new InvalidParameterException("action of UpdateIntent should not be null");
    }
    this.setAction(action);
    this.putExtra(MSG_PAYLOAD, strMsg);
  }
}
