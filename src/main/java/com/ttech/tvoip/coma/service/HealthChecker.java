//package com.ttech.tvoip.coma.service;
//
//import lombok.extern.slf4j.Slf4j;
//import org.apache.commons.net.telnet.*;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.scheduling.annotation.Scheduled;
//import org.springframework.stereotype.Service;
//
//import com.ttech.tvoip.coma.alarm.AlarmManager;
//
//import java.net.*;
//import java.util.*;
//import java.util.concurrent.TimeUnit;
//import java.util.regex.Matcher;
//import java.util.regex.Pattern;
//import java.util.stream.Stream;
//import java.io.*;
//
//
//@Service
//@Slf4j
//public class HealthChecker {
//	
//	@Autowired
//	AlarmManager alarmManager;
//
//	ArrayList<String> serverlist = new ArrayList<String>();
//	
//	@Value("#{'${list.of.coturns.corpus:172.21.193.105|qwerty|86.108.188.238:443,86.108.188.237:42000,86.108.188.239:443,86.108.188.240:443,86.108.188.241:443,86.108.188.242:443;}'.split(';')}") 
//	private String[] corpusEntityList;
//	
//	@Scheduled(initialDelay=1000, fixedRateString="60000")  
//	public void coturnAutoCheck()  {
//		
//        Vector<String> v_alternate_list_from_config = new Vector<String>();
//        Vector<String> v_alternate_server_list_from_cli = new Vector<String>();
//        String line = null;
//        
//        
//        for(String a:corpusEntityList)
//        {
//        	log.info("Corpuslar: " + a);
//        }
//        
//        for(String corpusEntity : corpusEntityList)
//        {
//        	String[] corpusEntityDatas = corpusEntity.split("\\|");
//       
//        	
//        	String proxyCoturn = corpusEntityDatas[0];
//        	String proxyCliPassword = corpusEntityDatas[1];
//        	String workersList = corpusEntityDatas[2];
//
//            log.debug("Cli: " + proxyCoturn + " Password: " + proxyCliPassword + " WorkersList: " + workersList);
//
//        	
//        	String[] workersListEntity = workersList.split(",");
//
//            for(String a:workersListEntity)
//            {
//            	log.debug("workersListEntity: " + a);
//            }
//        	
//        	/*Get Alternative Server List from Cli*/        
//        	v_alternate_server_list_from_cli = getAlternateServersFromCli(proxyCoturn, proxyCliPassword);
//       
//        	try
//        	{
//        		String serverentity;
//        		String ip_from_config;
//        		String port_from_config;
//        		boolean exist = false;
//			
//        		for(String serversFromOverride:workersListEntity) 
//        		{
//        			serverentity = serversFromOverride;
//        			String[] config_ip_ports = serverentity.split(":");
//        			v_alternate_list_from_config.add(config_ip_ports[0] + ":" + config_ip_ports[1]);
//        			try
//        			{
//        				InetAddress server = InetAddress.getByName(config_ip_ports[0]);
//        				SocketAddress sockaddr = new InetSocketAddress(server, Integer.parseInt(config_ip_ports[1]));
//        				Socket socket = new Socket();
//        				socket.connect(sockaddr, 1000);
//        				socket.close();
//        				socket = null;
//					
//        				for (String a : v_alternate_server_list_from_cli)
//        				{
//						
//        					if (a.equals(serverentity))
//        					{
//        						exist = true;
//        					}						
//        				}
//		    		
//        				if(!exist)
//        				{
//        					alternateServerConfiguration(serverentity, true, proxyCoturn, proxyCliPassword);		    			
//        				}
//        				alarmManager.getComaWorkersAlarm().clear();
//					
//        			}
//        			catch (SocketTimeoutException ex) 
//        			{
//        				alarmManager.getComaWorkersAlarm().raise();
//        				int remove = -1;					
//
//        				for (String a : v_alternate_server_list_from_cli)
//        				{
//        					if (a.equals(serverentity))
//        					{
//        						exist = true;
//        						break;						
//        					}
//        					else
//        					{
//        						exist = false;							
//        					}						
//        				}
//					
//        				if(exist)
//        				{
//        					alternateServerConfiguration(serverentity, false, proxyCoturn, proxyCliPassword);		    			
//        				}
//			        
//        			}
//        			catch( Exception e )
//        			{
//        				alarmManager.getComaWorkersAlarm().raise();
//        				log.error("Unknown workers socket exception: " + serversFromOverride,e);
//        			}
//        		}
//        	}
//        	catch( Exception e )
//        	{
//				alarmManager.getComaWorkersAlarm().raise();
//        		log.error("Config file problem.",e);
//        	}
//		
//        }
//
//	}
//
//	public void alternateServerConfiguration(String serverentity, boolean operation, String proxyCoturn, String proxyCliPassword) throws InterruptedException, IOException {
//		TelnetClient telnet = new TelnetClient();
//		for (int retry_count = 0; retry_count < 3; retry_count++) 
//		{
//			  try 
//			  {
//				  telnet.connect(proxyCoturn,5767);
//				  break;
//			  } 
//			  catch (IOException e) 
//			  {
//				  if (retry_count < 3) 
//				  {
//					  Thread.sleep(1);
//				  } 
//				  else 
//				  {
//					  throw e;
//				  }
//			  }
//		}
//		
//		String pwd = proxyCliPassword;
//		telnet.getOutputStream().write(pwd.getBytes());
//		telnet.getOutputStream().flush();
//		
//		if(operation == true)
//		{
//			String command1 = "aas " + serverentity + "\r\n";
//			log.debug("AAS COMMAND: " + command1);
//		
//			telnet.getOutputStream().write(command1.getBytes());
//			telnet.getOutputStream().flush();
//		
//			InputStream instr = telnet.getInputStream();
//			InputStreamReader is = new InputStreamReader(instr);
//			BufferedReader breader = new BufferedReader(is);
//			byte[] buff = new byte[1024];
//			int ret_read = 0;
//	        breader.close();
//	        is.close();
//		}
//		
//		else if(operation == false)
//		{
//			String command1 = "das " + serverentity + "\r\n";
//			log.debug("DAS COMMAND: " + command1);
//			
//            telnet.getOutputStream().write(command1.getBytes());
//            telnet.getOutputStream().flush();
//			
//			/*Initialized log*/
//			InputStream instr = telnet.getInputStream();
//			InputStreamReader is = new InputStreamReader(instr);
//			BufferedReader breader = new BufferedReader(is);
//			byte[] buff = new byte[1024];
//			int ret_read = 0;
//	        breader.close();
//	        is.close();
//		}
//		
//		else
//		{
//			log.error("Neither add nor remove!" + operation);			
//		}
//		
//		telnet.disconnect();
//	}
//
//	public Vector<String> getAlternateServersFromCli(String proxyCoturn, String cliPassword) {
//		String line;
//        Vector<String> v_alternate_server_list_from_cli = new Vector<String>();
//    	
//        try
//        {
//    		TelnetClient telnet = new TelnetClient();
//	        for (int retry_count = 0; retry_count < 3; retry_count++) 
//	        {
//	        	  try 
//	        	  {
//	        		  telnet.connect(proxyCoturn,5767);
//	        		  break;
//	        	  } 
//	        	  catch (IOException e) 
//	        	  {
//	        		  if (retry_count < 3) 
//	        		  {
//	        			  Thread.sleep(1);
//	        		  } 
//	        		  else 
//	        		  {
//	        			  throw e;
//	        		  }
//	        	  }
//	        }
//	                    
//	        String pwd = cliPassword;
//            telnet.getOutputStream().write(pwd.getBytes());
//            telnet.getOutputStream().flush();
//			    		 
//            String pc = "pc";
//            telnet.getOutputStream().write(pc.getBytes());
//            telnet.getOutputStream().flush();
//            
//	        InputStream instr = telnet.getInputStream();
//	        InputStreamReader is = new InputStreamReader(instr);
//	        BufferedReader breader = new BufferedReader(is);
//	                    
//            while ((line = breader.readLine()) != null)
//            {
//	            Pattern p = Pattern.compile("Alternate");
//	            Matcher m = p.matcher(line);
//	            while(m.find())
//	            {
//	                System.out.println(m.group());
//					String[] arrOfStr = line.split(":");
//					
//					v_alternate_server_list_from_cli.add(arrOfStr[1].trim() + ":" + arrOfStr[2]);
//	            }
//            	if(line.contains("cli-max-output-sessions")) {
//                    break;
//                } 
//            }
//            			
//    		telnet.disconnect();
//			alarmManager.getComaProxiesAlarm().clear();
//        }
//        catch( Exception e )
//        {
//			alarmManager.getComaProxiesAlarm().raise();
//        	log.error("Telnet exception while getting the information from the proxy server.",e);
//        }
//        
//		return v_alternate_server_list_from_cli;
//	}
//}
