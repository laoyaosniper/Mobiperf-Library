/* Copyright 2012 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mobiperf_library.test;


import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.test.ActivityInstrumentationTestCase2;
import android.test.ServiceTestCase;














import com.mobiperf_library.MainActivity;
import com.mobiperf_library.MainService;
import com.mobiperf_library.MeasurementScheduler;
import com.mobiperf_library.MeasurementTask;
import com.mobiperf_library.measurements.TestTask;
import com.mobiperf_library.measurements.TestTask.TestDesc;
import com.mobiperf_library.util.Logger;

/**
 * Base class for test cases that involves UI. 
 *
 */
public class TestMeasurementTaskBase extends ActivityInstrumentationTestCase2<MainActivity>{

	protected MainActivity activity;
	// Required by the ActivityInstrumentationTestCase2 as shown in the Android tutorial
	protected Instrumentation inst;
	// The system console for the test case to print debugging message to the phone screen
	//protected TextView systemConsole;
	protected MeasurementScheduler scheduler;
//	protected MainService mainservice;

	public TestMeasurementTaskBase(Class<MainActivity> activityClass) {
		super(activityClass);
	}

//	public TestMeasurementTaskBase(Class<MainService> serviceClass) {
//		super(serviceClass);
//	}

	public TestMeasurementTaskBase(){
		super(MainActivity.class);
	}
	




	@Override
	public void setUp() throws Exception {
		super.setUp();
	    this.activity = getActivity();
	    this.inst = getInstrumentation();
	    this.scheduler = this.activity.getScheduler();
	    assertNotNull(this.scheduler);		
//		this.scheduler = getService();
//		Map<String, String> params = new HashMap<String, String>();
//        params.put("num",20+"");
//		MeasurementTask newTask1;
//		TestDesc desc1 = new TestDesc("mykey1",Calendar.getInstance().getTime(),null,120,1,MeasurementTask.USER_PRIORITY,params);
//        newTask1 = new TestTask(desc1,getContext() );
//        this.scheduler.submitTask(newTask1);



		//    getSystemContext().startService(new Intent(getSystemContext(), MainService.class));
		//    TestMeasurementTaskBase.

		////    this.startService(intent);
		////    this.inst = getInstrumentation();
		//    

		//    //this.systemConsole = (TextView) 
		//    //    activity.findViewById(com.mobiperf.speedometer.R.viewId.systemConsole);
	}
	

}
