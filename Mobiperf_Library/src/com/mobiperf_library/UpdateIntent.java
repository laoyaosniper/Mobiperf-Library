package com.mobiperf_library;
import java.security.InvalidParameterException;

import android.content.Intent;

import android.os.Process;
/**
 * A repackaged Intent class that includes MobiLib-specific information. 
 */
public class UpdateIntent extends Intent {
  
  // Different types of payloads that this intent can carry:
  public static final String MSG_PAYLOAD = "MSG_PAYLOAD";
  
  public static final String TASK_STATUS_PAYLOAD = "TASK_STATUS_PAYLOAD";
  public static final String TASKID_PAYLOAD = "TASKID_PAYLOAD";
  public static final String TASKKEY_PAYLOAD = "TASKKEY_PAYLOAD";
  public static final String TASK_PRIORITY_PAYLOAD = "TASK_PRIORITY_PAYLOAD";
  public static final String RESULT_PAYLOAD = "RESULT_PAYLOAD";
  
  
  // Different types of actions that this intent can represent:
  private static final String PACKAGE_PREFIX =
      UpdateIntent.class.getPackage().getName();
  private static final String APP_PREFIX = 
      UpdateIntent.class.getPackage().getName() + Process.myPid();
  
  public static final String MEASUREMENT_ACTION =
      APP_PREFIX + ".MEASUREMENT_ACTION";
  public static final String CHECKIN_ACTION =
      APP_PREFIX + ".CHECKIN_ACTION";
  public static final String CHECKIN_RETRY_ACTION =
      APP_PREFIX + ".CHECKIN_RETRY_ACTION";
  public static final String MEASUREMENT_PROGRESS_UPDATE_ACTION =
      APP_PREFIX + ".MEASUREMENT_PROGRESS_UPDATE_ACTION";
  
  public static final String USER_RESULT_ACTION =
      PACKAGE_PREFIX + ".USER_RESULT_ACTION";  
  public static final String SERVER_RESULT_ACTION =
      PACKAGE_PREFIX + ".SERVER_RESULT_ACTION";

  /**
   * Creates an intent of the specified action with an optional message
   */
  protected UpdateIntent(String strMsg, String action)
      throws InvalidParameterException {
    super();
    if (action == null) {
      throw new InvalidParameterException("action of UpdateIntent should not be null");
    }
    this.setAction(action);
    this.putExtra(MSG_PAYLOAD, strMsg);
  }
}
