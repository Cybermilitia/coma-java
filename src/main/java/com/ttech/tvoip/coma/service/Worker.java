package com.ttech.tvoip.coma.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.ArrayList;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.PostConstruct;
import org.apache.commons.net.telnet.TelnetClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.ttech.tvoip.coma.alarm.AlarmGroup;

import lombok.extern.slf4j.Slf4j;

import de.javawi.jstun.attribute.ChangeRequest;
import de.javawi.jstun.attribute.ChangedAddress;
import de.javawi.jstun.attribute.ErrorCode;
import de.javawi.jstun.attribute.MappedAddress;
import de.javawi.jstun.attribute.MessageAttribute;
import de.javawi.jstun.attribute.MessageAttributeException;
import de.javawi.jstun.attribute.MessageAttributeParsingException;
import de.javawi.jstun.header.MessageHeader;
import de.javawi.jstun.header.MessageHeaderParsingException;
import de.javawi.jstun.util.UtilityException;

@Slf4j
@Service
@Scope("prototype")
public class Worker {
	
	@Autowired
	AlarmGroup alarmGroup;
	
	private String workerIp;
	private int workerPort;
	private Boolean state;
	private Boolean redundancy;

	public Worker(String workerCandidate, Boolean RedundancyData)  {
		/*Is is spare or main Worker?*/
		redundancy = RedundancyData;	
		state = false;
		String[] configIpPort = workerCandidate.split(":");
		
		workerIp = configIpPort[0];
		workerPort = Integer.parseInt(configIpPort[1]);
	}
	
	public Boolean getRedundancy()  {
		return redundancy;
	}
	
	public Boolean getState()  {
		String workerCandidate = workerIp + ":" + workerPort;
		state = false;
		try {
			
/*				InetAddress server = InetAddress.getByName(configIpPort[0]);
			SocketAddress sockaddr = new InetSocketAddress(server, Integer.parseInt(configIpPort[1]));
			Socket socket = new Socket();
			socket.connect(sockaddr, 1000);
			socket.close();
			socket = null;*/
			
	        MessageHeader sendMH = new MessageHeader(MessageHeader.MessageHeaderType.BindingRequest);
	        // sendMH.generateTransactionID();

	        // add an empty ChangeRequest attribute. Not required by the
	        // standard,
	        // but JSTUN server requires it

	        ChangeRequest changeRequest = new ChangeRequest();
	        sendMH.addMessageAttribute(changeRequest);

	        byte[] data = sendMH.getBytes();

	        DatagramSocket s = new DatagramSocket();
	        s.setReuseAddress(true);
	        s.setSoTimeout(1000);

	        DatagramPacket p = new DatagramPacket(data, data.length, InetAddress.getByName(workerIp), workerPort);
	        s.send(p);

	        DatagramPacket rp;

	        rp = new DatagramPacket(new byte[32], 32);
			
	        s.receive(rp);
	        	        
	        /*MessageHeader receiveMH = new MessageHeader(MessageHeader.MessageHeaderType.BindingResponse);
	        // System.out.println(receiveMH.getTransactionID().toString() + "Size:"
	        // + receiveMH.getTransactionID().length);
			log.error("In Worker constructor: BEFORE PARSING ");
	        try {
	                receiveMH.parseAttributes(rp.getData());
	        } catch (MessageAttributeParsingException e) {
	                e.printStackTrace();
	        }
			log.error("In Worker constructor: BEFORE MAPPED ADDRESS ");
	        MappedAddress ma = (MappedAddress) receiveMH
	                .getMessageAttribute(MessageAttribute.MessageAttributeType.MappedAddress);
	        System.out.println(ma.getAddress()+" "+ma.getPort());*/
			
			/*Sync OK. State must be set true*/
			state = true;
			
			log.info("workerCandidate: " + workerCandidate); 
			log.info("alarmGroup: {}", alarmGroup.getAlarmList()); 
			/*Alarm clear*/				
			alarmGroup.getAlarm(workerCandidate).clear();
			s.close();
		
		} catch (SocketTimeoutException ex) {
			
			log.error("In Worker constructor: TIMEOUT " + workerCandidate);
			alarmGroup.getAlarm(workerCandidate).raise("TIMEOUT");
			/*Sync NOK. State must be set false*/
			return state = false;
        
		} catch( ConnectException  e ) {
			
			log.error("In Worker constructor: Connection Refused " + workerCandidate);
			alarmGroup.getAlarm(workerCandidate).raise("Connection Refused");
			//log.error("Connection refused: " + workerCandidate,e);
			/*Sync NOK. State must be set false*/
			return state = false;	
			
		} catch( Exception e ) {
			
			log.error("In Worker constructor: UNKNOWN " + workerCandidate,e);
			alarmGroup.getAlarm(workerCandidate).raise("UNKNOWN");
			//log.error("Unknown workers socket exception: " + workerCandidate,e);
			/*Sync NOK. State must be set false*/
			return state = false;			
		} 
		return state;
	}
	
	public String getIp()  {
		return workerIp;
	}

	public Integer getPort()  {
		return workerPort;
	}
}
