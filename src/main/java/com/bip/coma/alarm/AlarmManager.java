package com.bip.coma.alarm;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AlarmManager {
	
	public static final String ALARM_SEVERITY_MAJOR = "major";
	public static final String ALARM_SEVERITY_CRITICAL = "critical";

	public static final String ALARM_NAME_PROXY = "PROXY_";
	public static final String ALARM_NAME_WORKER = "WORKER_";


	@Value("${alarm.proxy.period:0}000")
	private int proxyAlarmPeriod;
	
	@Value("${alarm.proxy.threshold:1}")
	private int proxyAlarmThreshold;

	@Value("${alarm.worker.period:10}000")
	private int workerAlarmPeriod;
	
	@Value("${alarm.worker.threshold:10}")
	private int workerAlarmThreshold;


	private AlarmManager() {
		
	}

	public Alarm createProxyAlarm(String instance){
		Alarm proxyAlarm = new Alarm(ALARM_NAME_PROXY + instance);
		proxyAlarm.setSeverity(ALARM_SEVERITY_CRITICAL);
		proxyAlarm.setThreshold(proxyAlarmThreshold);
		proxyAlarm.setTimePeriod(proxyAlarmPeriod);
		return proxyAlarm;
	}

	public Alarm createWorkerAlarm(String instance){
		Alarm workerAlarm = new Alarm(ALARM_NAME_WORKER + instance);
		workerAlarm.setSeverity(ALARM_SEVERITY_CRITICAL);
		workerAlarm.setThreshold(workerAlarmThreshold);
		workerAlarm.setTimePeriod(workerAlarmPeriod);
		return workerAlarm;
	}
}