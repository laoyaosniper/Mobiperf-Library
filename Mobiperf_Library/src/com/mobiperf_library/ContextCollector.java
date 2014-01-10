package com.mobiperf_library;

import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

import android.net.TrafficStats;

import com.mobiperf_library.util.PhoneUtils;

public class ContextCollector {
	private Thread contextThread;
	public static int isBusy = 0;
	// this thread is used for collecting context information in a time
	// interval.
	private volatile Vector<Map<String, Object>> contextResultVector;
	private Map<String, Object> currentContext;
	public int busyInterval = 1000;
	public int idleInterval = 6000;
	Thread runnable = new Thread() {
		public static final String TYPE = "context";
		public MeasurementResult result;

		public void run() {
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
			int interval = busyInterval;
			while (true) {
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
				PhoneUtils phoneUtils = PhoneUtils.getPhoneUtils();
				Map<String, Object> contextResult = new HashMap<String, Object>();
				contextResult.put("timestamp",
						System.currentTimeMillis() * 1000);
				contextResult.put("rssi", phoneUtils.getCurrentRssi());
				contextResult.put("inc_mobile_bytes_send", intervalSend);
				contextResult.put("inc_mobile_bytes_recv", intervalRecv);
				contextResult.put("inc_mobile_pkt_send", intervalPktSend);
				contextResult.put("inc_mobile_pkt_recv", intervalPktRecv);
				contextResult.put("interval", interval);
				contextResult.put("battery_level",
						phoneUtils.getCurrentBatteryLevel());
				currentContext = contextResult;
				contextResultVector.add(contextResult);
				System.out.println("context result="
						+ currentContext.toString());
				if (isBusy == 0) {
					interval = idleInterval;
				} else {
					interval = busyInterval;
				}
				try {
					Thread.sleep(interval);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	};

	public Vector<Map<String, Object>> getContextResult() {
		Vector<Map<String, Object>> contextResultVec = new Vector<Map<String, Object>>(
				contextResultVector);
		// System.out.println("context result = "+contextResultMap.toString());
		contextResultVector.clear();
		return contextResultVec;
	}

	public Map<String, Object> getContext() {
		return currentContext;
	}

	public void setPeriodSecond(double second) {
		busyInterval = (int) (second * 1000);
	}

	public void start(double interval) {
		busyInterval = (int) (interval * 1000);
		contextResultVector = new Vector<Map<String, Object>>();
		currentContext = new HashMap<String, Object>();
		if (contextThread == null) {
			contextThread = new Thread(runnable);
			contextThread.start();
		}
	}

	public void start() {
		contextResultVector = new Vector<Map<String, Object>>();
		currentContext = new HashMap<String, Object>();
		if (contextThread == null) {
			contextThread = new Thread(runnable);
			contextThread.start();
		}
	}
}
