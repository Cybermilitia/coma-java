package com.ttech.tvoip.coma.alarm;


import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AlarmGroup extends Alarm {
	
	
	@Value("${coma.proxies.alarm.threshold:1}")
	private int alarmThreshold;
	
	@Value("${coma.workers.alarm.period:10}000")
	private int alarmPeriod;
	
	
	private static final String ALARM_SEVERITY_CRITICAL = "critical";
	private static final String WORKER_ALARM = "worker";
	
	private Map<String,Alarm> alarmList = new ConcurrentHashMap<String,Alarm>();
	
	public AlarmGroup(String n) {
		this.setName(n);
	}	
	
	public Alarm addAlarm(String instance){
		
		Alarm newAlarm = new Alarm();
		newAlarm.setName(instance);
		newAlarm.setSeverity(ALARM_SEVERITY_CRITICAL);
		newAlarm.setThreshold(alarmThreshold);
		newAlarm.setTimePeriod(alarmPeriod);
		
		alarmList.put(instance, newAlarm);
		return newAlarm;
	}
	
	public AlarmGroup() {
		
	}
	
	public Alarm getAlarm(String instance) {
		return alarmList.get(instance);
	}
	
	public synchronized void raise(String instance) {
		Alarm alarm = alarmList.get(instance);
		alarm.raise(instance);
		alarmList.put(instance, alarm);
	}
	
	public synchronized void clear(String instance) {
		Alarm alarm = alarmList.get(instance);
		alarm.clear(instance);
		alarmList.put(instance, alarm);
	}

	public Map<String, Alarm> getAlarmList() {
		return alarmList;
	}

	public void setAlarmList(Map<String, Alarm> alarmList) {
		this.alarmList = alarmList;
	}
	
	
	
}