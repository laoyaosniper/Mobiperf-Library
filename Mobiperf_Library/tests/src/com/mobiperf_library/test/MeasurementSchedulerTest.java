package com.mobiperf_library.test;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.mobiperf_library.MeasurementScheduler;
import com.mobiperf_library.MeasurementTask;
import com.mobiperf_library.measurements.TestTask;
import com.mobiperf_library.measurements.TestTask.TestDesc;
import com.mobiperf_library.util.Util;


public class MeasurementSchedulerTest  extends TestMeasurementTaskBase{

//	public MeasurementSchedulerTest(Class<MainService> serviceClass) {
//		super(serviceClass);
//	}
//	
//	public MeasurementSchedulerTest(){
//		super(MainService.class);
//	}
//	
	@Override
	public void setUp() throws Exception {
		super.setUp();
	}
	
	public void testPreemptionTask() throws Exception {
		MeasurementTask newTask1;
		MeasurementTask newTask2;
		MeasurementTask newTask3;
		Map<String, String> params = new HashMap<String, String>();
        params.put("num",20+"");
		TestDesc desc1 = new TestDesc("mykey1",Calendar.getInstance().getTime(),null,120,1,MeasurementTask.USER_PRIORITY+2,params);
        TestDesc desc2 = new TestDesc("mykey2",Calendar.getInstance().getTime(),new Date(Calendar.getInstance().getTimeInMillis()+(5*1000)),120,1,MeasurementTask.USER_PRIORITY+1,params);
        newTask1 = new TestTask(desc1, this.activity);
        newTask2 = new TestTask(desc2, this.activity);
        
        String taskId1=this.scheduler.submitTask(newTask1);
//        assertEquals(newTask1.generateTaskID(), taskId1);
        assertEquals(this.scheduler.getTaskStatus(taskId1), MeasurementScheduler.TaskStatus.SCHEDULED);
        Thread.sleep(1000);
        assertEquals(this.scheduler.getTaskStatus(taskId1), MeasurementScheduler.TaskStatus.RUNNING);
        Thread.sleep(1000);
        String taskId2=this.scheduler.submitTask(newTask2);
        Thread.sleep(2000);
        assertEquals(this.scheduler.getTaskStatus(taskId1), MeasurementScheduler.TaskStatus.PAUSED);
        assertEquals(this.scheduler.getTaskStatus(taskId2), MeasurementScheduler.TaskStatus.RUNNING);
        Thread.sleep(3000);
//        assertEquals(this.scheduler.getTaskStatus(taskId2), MeasurementScheduler.TaskStatus.FINISHED);
//        assertEquals(this.scheduler.getTaskStatus(taskId1), MeasurementScheduler.TaskStatus.RUNNING);
//        Thread.sleep(2000);
        TestDesc desc3 = new TestDesc("mykey3",Calendar.getInstance().getTime(),new Date(Calendar.getInstance().getTimeInMillis()+(2*1000)),120,1,MeasurementTask.USER_PRIORITY,params);
        newTask3 = new TestTask(desc3, this.activity);
        String taskId3=this.scheduler.submitTask(newTask3);
        Thread.sleep(2000);
        assertEquals(this.scheduler.getTaskStatus(taskId2), MeasurementScheduler.TaskStatus.PAUSED);
        assertEquals(this.scheduler.getTaskStatus(taskId3), MeasurementScheduler.TaskStatus.RUNNING);
        Thread.sleep(20000);
        assertEquals(this.scheduler.getTaskStatus(taskId2), MeasurementScheduler.TaskStatus.RUNNING);
        assertEquals(this.scheduler.getTaskStatus(taskId1), MeasurementScheduler.TaskStatus.PAUSED);
        assertEquals(this.scheduler.getTaskStatus(taskId3), MeasurementScheduler.TaskStatus.FINISHED);
        Thread.sleep(35000);
        assertEquals(this.scheduler.getTaskStatus(taskId1), MeasurementScheduler.TaskStatus.FINISHED);
	}

}
