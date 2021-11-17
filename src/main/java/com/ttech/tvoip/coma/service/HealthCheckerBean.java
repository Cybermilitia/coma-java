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
import java.util.stream.*;
import java.util.Arrays;

import javax.annotation.PostConstruct;

import org.apache.commons.net.telnet.TelnetClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
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
	
	@Autowired
	private ApplicationContext appContext;
	
	private String proxyCoturn;
	
	private String proxyCliSecret;
	
	private String[] totalWorkersList;

	private String[] spareWorkersList;

	private String[] mainWorkersList;

	private String[] workersList;
	
	Boolean sparesNeeded = true;

	Vector<String> alternateServerListFromCli = new Vector<String>();
	Vector<String> alternateServerListFromConfig = new Vector<String>();

	ArrayList<String> serverlist = new ArrayList<String>();
	
	@Value("${telnet.port:8000}")
	private int telnetPort;

	@Value("${check.sync.time:600000}")
	private String syncTimeString;
	
	public HealthCheckerBean(String corpusEntity) {
				
		String[] corpusEntityDatas = corpusEntity.split("\\|");
		
		this.proxyCoturn = corpusEntityDatas[0];
		this.proxyCliSecret = corpusEntityDatas[1];
		this.totalWorkersList = corpusEntityDatas[2].split("/spares/");
		this.mainWorkersList = totalWorkersList[0].split(",");
		this.spareWorkersList = totalWorkersList[1].split(",");
		this.workersList = Stream.concat(Arrays.stream(this.mainWorkersList), Arrays.stream(this.spareWorkersList)).toArray(String[]::new);
		
		//this.workersList = corpusEntityDatas[2].split(",");

		log.info("Cli: {} Secret:{} Workers:{} Spare Workers:{}", proxyCoturn, proxyCliSecret, mainWorkersList, spareWorkersList );
	}
	
	@PostConstruct
	public void init() {
		log.info("init method invoked for proxy:{} and whole workers:{}", proxyCoturn, workersList);
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
    	
    	/*Get Alternative Server List from Config*/        		
		for(String mainWorkerCandidate : mainWorkersList) {			
			alternateServerListFromConfig.add(mainWorkerCandidate); 
		}
		
		for(String spareWorkerCandidate : spareWorkersList) {
			alternateServerListFromConfig.add(spareWorkerCandidate); 
		}
		/*Collate alternateServerListFromCli with alternateServerListFromConfig for any stowaway*/
		for(String worker:alternateServerListFromCli)
		{
			if(!alternateServerListFromConfig.contains(worker))
			{
				log.error("There is a stowaway in the proxy coturn: {}", worker);
			}
		}
		
		/*Check the initial spareNeeded condition*/
		
		for(String mainWorkerCandidate : mainWorkersList) {			
			if(alternateServerListFromCli.contains(mainWorkerCandidate))
			{
				sparesNeeded = false;
				log.info("SPARES DEACTIVATED! " + mainWorkerCandidate);
			}
		}
		//log.info("Alarm map:{}", alarmGroup.getAlarmList());
	}	
	
	@Scheduled(initialDelay=6500, fixedRateString="${check.sync.time:10}000")
//	@Scheduled(initialDelay=6500, fixedRateString="60000")
	public void Syncronization()  {
    	/*Get Alternative Server List from Cli*/        
    	try {
			log.info("Syncronization");
			alternateServerListFromCli = getAlternateServersFromCli(proxyCoturn, proxyCliSecret);
		} catch (IOException e1) {
			log.error("List command not running for proxy:{}", proxyCoturn, e1);
			return;
		}
	}

	
	@Scheduled(initialDelay=1000, fixedRateString="10000")  
	public void coturnAutoCheck()  {
		
        Vector<Worker> alternateWorkerListFromConfig = new Vector<Worker>();
       
		log.info("***************************************************************************************************");
		log.info("Cycle Starts");
		
		for(String s:alternateServerListFromCli)
			log.info("alternateServerListFromCli " + s);

		/*At first main workers*/
		
    		for(String mainWorkerCandidate : mainWorkersList) {
    			
    			String[] configIpPort = mainWorkerCandidate.split(":");
    			
    			try{
    				alternateWorkerListFromConfig.add((Worker) appContext.getBean("worker", mainWorkerCandidate, false));
        		}
    			catch(Exception e ){
					log.error("Main worker problem: " + mainWorkerCandidate);   					
    			}
    			
    		}
    		
    		/*Then blackleg - spare workers*/
    		
    		for(String spareWorkerCandidate : spareWorkersList) {
    			
    			String[] configIpPort = spareWorkerCandidate.split(":");
    			
    			try{
    				alternateWorkerListFromConfig.add((Worker) appContext.getBean("worker", spareWorkerCandidate, true));
        		}
    			catch(Exception e ){
					log.error("Spare worker problem: " + spareWorkerCandidate);   					
    			}
    			
    		}
    		
    		/*
				State(Up/DOWN)	cliContains	SpareNeeded	 Redundancy(Main 0/Spare 1)   Process
			0					0				 0					0					0
			0					0				 0					1					0
			0					0				 1					0					0
			0					0				 1					1					0
			
			0					1				 0					0					D (if size 0 => spareNeeded)
			0					1				 0					1					D
			0					1				 1					0					D (no matter spareNeeded)
			0					1				 1					1					D
				
			1					0				 0					0					A (if not redundant => !spareNeeded)
			1					0				 0					1					0 (no need spare - Do not add it)
			1					0				 1					0					A (if not redundant => !spareNeeded)
			1					0				 1					1					A
				
			1					1				 0					0					0
			1					1				 0					1					D (no need spare - Delete it)
			1					1				 1					0					0
			1					1				 1					1					0
    		 */
    		
    		for(Worker workerObject :alternateWorkerListFromConfig)
    		{
				
    			String workerCandidate = workerObject.getIp() + ":" + workerObject.getPort();
    			
    			Boolean state = workerObject.getState();
    			
    			Boolean contain = alternateServerListFromCli.contains(workerCandidate);
    			
    			Boolean redundant = workerObject.getRedundancy();
    			
    			log.debug("workerCandidate: " + workerCandidate + " state: " + state + " contain: " + contain 
    					+ " sparesNeeded: " + sparesNeeded + " redundant: " + redundant);
    			

    			if((state == false) && (contain == false)) 
    				continue;
    			
    			else if((state == false) && (contain == true)) 
    			{
        			log.info("DAS " + workerCandidate);

        			try {
						alternateServerConfiguration(workerCandidate, false, proxyCoturn, proxyCliSecret);
						alternateServerListFromCli.remove(workerCandidate);
						if((alternateServerListFromCli.size() == 0) && (redundant == false))
						{
							sparesNeeded = true;
							log.info("SPARES ACTIVATED! " + sparesNeeded);
						}
		
					} catch (IOException e) {
						log.error("Command not running for proxy:{} mainWorkerCandidate:{} During DAS", proxyCoturn, workerCandidate, e);
					}		    			

    			}
        			
    			else if((state == true) && (contain == false)) 
    			{
    				if((sparesNeeded == true) || ((sparesNeeded == false) && (redundant == false)))
    				{
    	    			log.info("AAS " + workerCandidate);
    	    			
        				try {
            				alternateServerConfiguration(workerCandidate, true, proxyCoturn, proxyCliSecret);
            			    alternateServerListFromCli.add(workerCandidate); 
    					} catch (IOException e) {
    						log.error("Command not running for proxy:{} worker:{} during AAS", proxyCoturn, workerCandidate, e);
    					}
						if(redundant == false)
						{
							sparesNeeded = false;
							log.info("SPARES DEACTIVATED! " + sparesNeeded);
						}
    				}
    			}
    			
    			else if((state == true) && (contain == true) && (sparesNeeded == false) && (redundant == true)) 
    			{
        			log.info("DAS " + workerCandidate);

        			try {
						alternateServerConfiguration(workerCandidate, false, proxyCoturn, proxyCliSecret);
						alternateServerListFromCli.remove(workerCandidate);
		
					} catch (IOException e) {
						log.error("Command not running for proxy:{} mainWorkerCandidate:{} During DAS", proxyCoturn, workerCandidate, e);
					}	    				
   				}
    			
    			else
    			{
    				continue;
    			}
    			
    		}   
   		}

	@Retryable(value = { IOException.class }, maxAttempts = 2)
	public void alternateServerConfiguration(String worker, boolean operation, String proxyCoturn, String proxyCliSecret) throws IOException {
		
		log.info("Command running for proxy:{} operation:{} worker:{}", proxyCoturn, operation, worker);

		TelnetClient telnet = new TelnetClient();
		telnet.setConnectTimeout(1000);
		try {

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
			
        } catch( Exception e ) {
        	alarmGroup.getAlarm(proxyCoturn).raise("TELNET");
        	log.error("Telnet exception while getting the information from the proxy server.", e);
       		telnet.disconnect();			
       		throw e;
        }
		finally {
    		telnet.disconnect();	
		}
	}

	@Retryable(value = { IOException.class }, maxAttempts = 2)
	public Vector<String> getAlternateServersFromCli(String proxyCoturn, String cliPassword) throws IOException {
		
        Vector<String> alternateServerListFromCli = new Vector<String>();
    	
        log.info("Server list command running for proxy:{} ", proxyCoturn);
		TelnetClient telnet = new TelnetClient();	
		telnet.setConnectTimeout(1000);
		try
        {
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
            			
			alarmGroup.getAlarm(proxyCoturn).clear();

        } catch( Exception e ) {
			alarmGroup.getAlarm(proxyCoturn).raise("TELNET");
        	log.error("Telnet exception while getting the information from the proxy server.", e);
       		telnet.disconnect();			
        	throw e;
        }
		finally {
    		telnet.disconnect();
		}
        
		return alternateServerListFromCli;
	}
}
