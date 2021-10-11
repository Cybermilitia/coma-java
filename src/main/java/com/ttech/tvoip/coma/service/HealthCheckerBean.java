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


@Service
@Slf4j
@Scope("prototype")
public class HealthCheckerBean {
	
	@Autowired
	AlarmGroup alarmGroup;
	
	private String proxyCoturn;
	
	private String proxyCliSecret;
	
	private String[] workersList;

    Vector<String> alternateServerListFromCli = new Vector<String>();

	ArrayList<String> serverlist = new ArrayList<String>();
	
	@Value("${telnet.port:5767}")
	private int telnetPort;
	
	public HealthCheckerBean(String corpusEntity) {
		
		String[] corpusEntityDatas = corpusEntity.split("\\|");
		
		this.proxyCoturn = corpusEntityDatas[0];
		this.proxyCliSecret = corpusEntityDatas[1];
		this.workersList = corpusEntityDatas[2].split(",");
		
		log.info("Cli: {} Secret:{} Workers:{}", proxyCoturn, proxyCliSecret, workersList);
	}
	
	@PostConstruct
	public void init() {
		log.info("init method invoked for proxy:{} and workers:{}", proxyCoturn, workersList);
		alarmGroup.addAlarm(proxyCoturn);
		for (String worker : workersList) {
			alarmGroup.addAlarm(worker);
		}
    	/*Get Alternative Server List from Cli*/        
    	try {
			alternateServerListFromCli = getAlternateServersFromCli(proxyCoturn, proxyCliSecret);
		} catch (IOException e1) {
			log.error("List command not running for proxy:{}", proxyCoturn, e1);
			return;
		}
    	
		log.info("Alarm map:{}", alarmGroup.getAlarmList());
	}	
	
	@Scheduled(initialDelay=60000, fixedRateString="600000")  
	public void Syncronization()  {
    	/*Get Alternative Server List from Cli*/        
    	try {
			log.error("Syncronization");
			alternateServerListFromCli = getAlternateServersFromCli(proxyCoturn, proxyCliSecret);
		} catch (IOException e1) {
			log.error("List command not running for proxy:{}", proxyCoturn, e1);
			return;
		}
	}

	
	@Scheduled(initialDelay=1000, fixedRateString="10000")  
	public void coturnAutoCheck()  {
		
        Vector<String> alternateServerListFromConfig = new Vector<String>();
       
		log.error("Cycle Starts");
		
		for(String s:alternateServerListFromCli)
			log.error("alternateServerListFromCli " + s);

		
    		for(String worker : workersList) {
    			
    			String[] configIpPort = worker.split(":");
    			alternateServerListFromConfig.add(worker);
    			
    			try {
    				
    				InetAddress server = InetAddress.getByName(configIpPort[0]);
    				SocketAddress sockaddr = new InetSocketAddress(server, Integer.parseInt(configIpPort[1]));
    				Socket socket = new Socket();
    				socket.connect(sockaddr, 1000);
    				socket.close();
    				socket = null;
    				
				
					if(!alternateServerListFromCli.contains(worker)) {
	    				log.error("AAS " + alternateServerListFromCli.contains(worker) + " " + worker);
    					try {
        					alternateServerConfiguration(worker, true, proxyCoturn, proxyCliSecret);
        				    alternateServerListFromCli.add(worker);

						} catch (IOException e) {
							log.error("Command not running for proxy:{} worker:{} during AAS", proxyCoturn, worker, e);
						}	
    				}
    				
					alarmGroup.getAlarm(worker).clear();
				
    			} catch (SocketTimeoutException ex) {
    				
    				alarmGroup.getAlarm(worker).raise("TIMEOUT");
				
    				if(alternateServerListFromCli.contains(worker))
    				{
        				log.error("DAS due to timeout " + alternateServerListFromCli.contains(worker)+ " " + worker);
    					try {
							alternateServerConfiguration(worker, false, proxyCoturn, proxyCliSecret);
							alternateServerListFromCli.remove(worker);
						} catch (IOException e) {
							log.error("Command not running for proxy:{} worker:{} During DAS", proxyCoturn, worker, e);
						}		    			
    				}
		        
    			} catch( ConnectException  e ) {
    				
    				alarmGroup.getAlarm(worker).raise("Connection Refused");
    				log.error("Connection refused: " + worker,e);
    				
    				if(alternateServerListFromCli.contains(worker))
    				{
        				log.error("DAS due to connection refused" + alternateServerListFromCli.contains(worker)+ " " + worker);
    					try {
							alternateServerConfiguration(worker, false, proxyCoturn, proxyCliSecret);
        				    alternateServerListFromCli.remove(worker);
						} catch (IOException cliE) {
							log.error("Command not running for proxy:{} worker:{} ", proxyCoturn, worker, cliE);
						}		    			
    				}
    				
    			} catch( Exception e ) {
    				
				alarmGroup.getAlarm(worker).raise("UNKNOWN");
				log.error("Unknown workers socket exception: " + worker,e);
				
    			}
   		}

	}

	@Retryable(value = { IOException.class }, maxAttempts = 2)
	public void alternateServerConfiguration(String worker, boolean operation, String proxyCoturn, String proxyCliSecret) throws IOException {
		
		log.info("Command running for proxy:{} operation:{} worker:{}", proxyCoturn, operation, worker);

		try {
		
			TelnetClient telnet = new TelnetClient();
    		telnet.setConnectTimeout(1000);
			telnet.connect(proxyCoturn,telnetPort);
		
			String pwd = proxyCliSecret;
			telnet.getOutputStream().write(pwd.getBytes());
			telnet.getOutputStream().flush();
			
			if(operation) {
				
				String command1 = "aas " + worker + "\r\n";
				log.debug("AAS COMMAND: " + command1);
			
				telnet.getOutputStream().write(command1.getBytes());
				telnet.getOutputStream().flush();
			
				InputStream instr = telnet.getInputStream();
				InputStreamReader is = new InputStreamReader(instr);
				BufferedReader breader = new BufferedReader(is);
		        breader.close();
		        is.close();
		        
			} else {
				
				String command1 = "das " + worker + "\r\n";
				log.debug("DAS COMMAND: " + command1);
				
		        telnet.getOutputStream().write(command1.getBytes());
		        telnet.getOutputStream().flush();
				
				/*Initialized log*/
				InputStream instr = telnet.getInputStream();
				InputStreamReader is = new InputStreamReader(instr);
				BufferedReader breader = new BufferedReader(is);
		        breader.close();
		        is.close();
		        
			} 
			
			 telnet.disconnect();
        } catch( Exception e ) {
			 alarmGroup.getAlarm(proxyCoturn).raise("TELNET");
        	 log.error("Telnet exception while getting the information from the proxy server.", e);
        	 throw e;
        }
	}

	@Retryable(value = { IOException.class }, maxAttempts = 2)
	public Vector<String> getAlternateServersFromCli(String proxyCoturn, String cliPassword) throws IOException {
		
        Vector<String> alternateServerListFromCli = new Vector<String>();
    	
        log.info("Server list command running for proxy:{} ", proxyCoturn);
        try
        {
    		TelnetClient telnet = new TelnetClient();	
    		telnet.setConnectTimeout(1000);
	        telnet.connect(proxyCoturn,telnetPort);
	        	
	                    
	        String pwd = cliPassword;
            telnet.getOutputStream().write(pwd.getBytes());
            telnet.getOutputStream().flush();
			    		 
            String pc = "pc";
            telnet.getOutputStream().write(pc.getBytes());
            telnet.getOutputStream().flush();
            
	        InputStream instr = telnet.getInputStream();
	        InputStreamReader is = new InputStreamReader(instr);
	        BufferedReader breader = new BufferedReader(is);
	        String line;  
	        
            while ((line = breader.readLine()) != null)
            {
	            Pattern p = Pattern.compile("Alternate");
	            Matcher m = p.matcher(line);
	            while(m.find())
	            {
	                log.debug(m.group());
					String[] arrOfStr = line.split(":");
					
					alternateServerListFromCli.add(arrOfStr[1].trim() + ":" + arrOfStr[2]);
	            }
            	if(line.contains("cli-max-output-sessions")) {
                    break;
                } 
            }
            			
    		telnet.disconnect();
			alarmGroup.getAlarm(proxyCoturn).clear();

        } catch( Exception e ) {
			alarmGroup.getAlarm(proxyCoturn).raise("TELNET");
        	log.error("Telnet exception while getting the information from the proxy server.", e);
        	throw e;
        }
        
		return alternateServerListFromCli;
	}
}
