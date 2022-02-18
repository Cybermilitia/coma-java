package com.bip.coma.alarm;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

@Service
public class AlarmManager {
	
	public static final String ALARM_SEVERITY_MAJOR = "major";
	public static final String ALARM_SEVERITY_CRITICAL = "critical";

	public static final String ALARM_SYSTEM = "SYSTEM";
	public static final String ALARM_NAME_PROXY = "PROXY_";
	public static final String ALARM_NAME_WORKER = "WORKER_";


	@Value("${alarm.proxy.period:10}000")
	private int proxyAlarmPeriod;
	
	@Value("${alarm.proxy.threshold:1}")
	private int proxyAlarmThreshold;

	@Value("${alarm.worker.period:10}000")
	private int workerAlarmPeriod;
	
	@Value("${alarm.worker.threshold:10}")
	private int workerAlarmThreshold;

	private Alarm systemAlarm;

	private AlarmManager() {
		
	}

	@PostConstruct
	public void init(){
		this.systemAlarm = createSystemAlarm();
		this.systemAlarm.clear();
	}

	private Alarm createSystemAlarm(){
		Alarm proxyAlarm = new Alarm(ALARM_SYSTEM);
		proxyAlarm.setSeverity(ALARM_SEVERITY_CRITICAL);
		return proxyAlarm;
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

	public Alarm getSystemAlarm() {
		return systemAlarm;
	}

}