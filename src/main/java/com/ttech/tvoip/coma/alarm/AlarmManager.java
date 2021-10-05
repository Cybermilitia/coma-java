package com.ttech.tvoip.coma.alarm;


import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
//import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AlarmManager {
	
	private static final String ALARM_SEVERITY_MAJOR = "major";
	private static final String ALARM_SEVERITY_CRITICAL = "critical";

	@Value("${coma.proxies.alarm.period:0}000")
	private int comaProxiesAlarmPeriod;
	
	@Value("${coma.proxies.alarm.threshold:1}")
	private int comaProxiesAlarmThreshold;
	
	@Value("${coma.workers.alarm.period:10}000")
	private int comaWorkersAlarmPeriod;
	
	@Value("${coma.workers.alarm.threshold:10}")
	private int comaWorkersAlarmThreshold;
	
	private static Logger				logger		= LoggerFactory.getLogger(AlarmManager.class);
	private static Logger				alarmLogger	= LoggerFactory.getLogger("AlarmLogger");
	
	private Alarm						comaProxiesAlarm;
	private Alarm						comaWorkersAlarm;	
	
	private AlarmManager() {
		
	}
	
	@PostConstruct
	public void init() throws Exception {

		comaProxiesAlarm = new Alarm("COMA_PROXIES_ALARM");
		comaProxiesAlarm.setSeverity(ALARM_SEVERITY_CRITICAL);
		comaProxiesAlarm.setThreshold(comaProxiesAlarmThreshold);
		comaProxiesAlarm.setTimePeriod(comaProxiesAlarmPeriod);

		comaWorkersAlarm = new Alarm("COMA_WORKERS_ALARM");
		comaWorkersAlarm.setSeverity(ALARM_SEVERITY_CRITICAL);
		comaWorkersAlarm.setThreshold(comaWorkersAlarmThreshold);
		comaWorkersAlarm.setTimePeriod(comaWorkersAlarmPeriod);
		
		
		
		comaProxiesAlarm.clear();
		comaWorkersAlarm.clear();
		
		logger.info("AlarmManager started... {}|{}", comaProxiesAlarmPeriod, comaWorkersAlarmPeriod);
	}
	
	public void stop() {
		logger.debug("AlarmManager stopped... {}", this);
	}
	
	public Alarm getComaProxiesAlarm() {
		return comaProxiesAlarm;
	}
	
	public Alarm getComaWorkersAlarm() {
		return comaWorkersAlarm;
	}
	
}