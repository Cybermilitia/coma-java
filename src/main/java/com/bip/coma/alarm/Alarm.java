package com.ttech.tvoip.coma.alarm;

import org.slf4j.Logger;

import lombok.extern.slf4j.Slf4j;


/**
 * @author TTKVATANSEVER
 * 
 */
@Slf4j
public class Alarm {
	
	public static final int				ALARM_CLEARED	= 0;
	public static final int				ALARM_RAISED	= 1;
	public static final int				ALARM_NONE		= 2;
	
	private String						name;
	private String 						severity;
	private int							raiseId;
	private int							clearId;
	private int							threshold;
	private long						timePeriod;
	
	private int							counter;
	private int							status;
	private long						lastSentTime;
	private boolean						reRaiseEnable = false;
	
	public Alarm(){
		this.raiseId = -1;
		this.clearId = -1;
		this.counter = 0;
		this.threshold = 0;
		this.status = ALARM_NONE;
		this.lastSentTime = 0;
		this.timePeriod = 0;
		this.severity = "critial";		
	}
	
	public Alarm(String n) {		
		this();
		this.name = n;
	}
	
	public synchronized void raise() {
		this.raise(null);
	}
	
	public synchronized void raise(String message) {
		counter++;
		if (counter >= threshold && (status != ALARM_RAISED || isReraiseRequired())) {
			log.info(severity + "|" + (message!=null?message:"") + "|" + name);
			status = ALARM_RAISED;
			lastSentTime = System.currentTimeMillis();
		}
	}
	
	public void clear() {
		this.clear(null);
	}
	
	public synchronized void clear(String message) {
		if (status != ALARM_CLEARED) {
			log.info("normal|" + (message!=null?message:"") + "|" + name);
			status = ALARM_CLEARED;
			counter = 0;
			lastSentTime = 0;
		}
	}
	
	private boolean isReraiseRequired() {
		if (reRaiseEnable && status == ALARM_RAISED && ((System.currentTimeMillis() - lastSentTime) >= timePeriod)) {
			return true;
		} else {
			return false;
		}
	}
	
	public int getRaiseId() {
		return raiseId;
	}
	
	public void setRaiseId(int raiseId) {
		this.raiseId = raiseId;
	}
	
	public int getClearId() {
		return clearId;
	}
	
	public void setClearId(int clearId) {
		this.clearId = clearId;
	}
	
	public int getThreshold() {
		return threshold;
	}
	
	public void setThreshold(int threshold) {
		this.threshold = threshold;
	}
	
	public long getTimePeriod() {
		return timePeriod;
	}
	
	public void setTimePeriod(long timePeriod) {
		this.timePeriod = timePeriod;
	}
	
	public int getCounter() {
		return counter;
	}
	
	public int getStatus() {
		return status;
	}
	
	public long getLastSentTime() {
		return lastSentTime;
	}
	
	
	public String getName() {
		return name;
	}
	
	public void setName(String name) {
		this.name = name;
	}
	
	public String getSeverity() {
		return severity;
	}
	
	public void setSeverity(String severity) {
		this.severity = severity;
	}
	
}
