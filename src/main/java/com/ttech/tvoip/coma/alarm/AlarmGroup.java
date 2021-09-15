package com.ttech.tvoip.coma.alarm;


import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public class AlarmGroup extends Alarm {
	private Map<Integer,Alarm> alarmList = new ConcurrentHashMap<Integer,Alarm>();
	
	public AlarmGroup() {
		
	}

	public AlarmGroup(String n) {
		this.setName(n);
	}	
	
	public Alarm addAlarm(int instance){
		
		Alarm newAlarm = new Alarm();
		newAlarm.setName(this.getName());
		newAlarm.setSeverity(this.getSeverity());
		newAlarm.setThreshold(this.getThreshold());
		newAlarm.setTimePeriod(this.getTimePeriod());
		newAlarm.setLogger(this.getLogger());
		
		alarmList.put(instance, newAlarm);
		return newAlarm;
	}
	
	public Alarm getAlarm(int instance) {
		return alarmList.get(instance);
	}
	
	public synchronized void raise(int instance) {
		Alarm alarm = alarmList.get(instance);
		alarm.raise(Integer.toString(instance));
		alarmList.put(instance, alarm);
	}
	
	public synchronized void clear(int instance) {
		Alarm alarm = alarmList.get(instance);
		alarm.clear(Integer.toString(instance));
		alarmList.put(instance, alarm);
	}	
}