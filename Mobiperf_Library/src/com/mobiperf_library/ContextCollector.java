package com.mobiperf_library;

import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

import android.net.TrafficStats;

import com.mobiperf_library.util.PhoneUtils;

public class ContextCollector {
	
	// this thread is used for collecting context information in a time
	// interval.
	private volatile Vector<Map<String, Object>> contextResultVector;
	private PhoneUtils phoneUtils;
	private int interval;
	private Timer timer;
	private volatile boolean isRunning;
	public ContextCollector() {
		 phoneUtils= PhoneUtils.getPhoneUtils();
		 this.isRunning=false;
		 this.timer=new Timer();
		 contextResultVector= new Vector<Map<String,Object>>();
	}
	
	public void setInterval(int intervalSecond){
		this.interval=intervalSecond;
		if(intervalSecond ==0){
			 this.interval=Config.DEFAULT_CONTEXT_INTERVAL_SEC;
		}
	}
	
	private Map<String, Object> getCurrentContextInfo(){
		Map<String, Object> currentContext= new HashMap<String, Object>();;
		long prevSend = 0;
		long prevRecv = 0;
		long sendBytes = 0;
		long recvBytes = 0;
		long intervalSend = 0;
		long intervalRecv = 0;
		long prevPktSend = 0;
		long prevPktRecv = 0;
		long sendPkt = 0;
		long recvPkt = 0;
		long intervalPktSend = 0;
		long intervalPktRecv = 0;
		
		sendBytes = TrafficStats.getMobileTxBytes();
		recvBytes = TrafficStats.getMobileRxBytes();
		sendPkt = TrafficStats.getMobileTxPackets();
		sendPkt = TrafficStats.getMobileRxPackets();
		if (prevSend > 0 || prevRecv > 0) {
			intervalSend = sendBytes - prevSend;
			intervalRecv = recvBytes - prevRecv;
		}
		if (prevPktSend > 0 || prevPktRecv > 0) {
			intervalPktSend = sendPkt - prevPktSend;
			intervalPktRecv = recvPkt - prevPktRecv;
		}
		prevSend = sendBytes;
		prevRecv = recvBytes;
		prevPktSend = sendPkt;
		prevPktRecv = recvPkt;
		
		currentContext.put("timestamp",
				System.currentTimeMillis() * 1000);
		currentContext.put("rssi", phoneUtils.getCurrentRssi());
		currentContext.put("inc_mobile_bytes_send", intervalSend);
		currentContext.put("inc_mobile_bytes_recv", intervalRecv);
		currentContext.put("inc_mobile_pkt_send", intervalPktSend);
		currentContext.put("inc_mobile_pkt_recv", intervalPktRecv);
		currentContext.put("battery_level",
				phoneUtils.getCurrentBatteryLevel());
		return currentContext;
	}
	
	public boolean startCollector(){
		if(isRunning){
			return false;
		}
		isRunning=true;
		timer.scheduleAtFixedRate(timerTask ,0 , interval*1000);
		return true;
		
		
	}
	
	public Vector<Map<String, Object>> stopCollector(){
		if(!isRunning){
			return null;
		}
		timerTask.cancel();
		timer.cancel();
		isRunning=false;
		contextResultVector.add(getCurrentContextInfo());
		return contextResultVector;
	}
	
	
	
	 private TimerTask timerTask= new TimerTask(){
		  @Override
		  public void run() {
			  contextResultVector.add(getCurrentContextInfo());
		  }
	  };
	

}
