package com.ttech.tvoip.coma.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.net.telnet.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.net.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.io.*;

@Service
@Slf4j
public class HealthChecker {

	@Value("${server.list.path:D:\\Dev\\WORK\\coturn-healthchecker\\src\\main\\java\\com\\ttech\\tvoip\\coturnhealthchecker\\servers.cfg}")
	private String serverListPath;
	
	String server = "86.108.188.237";
	ArrayList<String> serverlist = new ArrayList<String>();
	
	@Scheduled(initialDelay=1000, fixedRateString="15000")   // TODO add check period
	public void coturnAutoCheck()  {	
		
        Vector<String> v_alternate_list_from_config = new Vector<String>();
        Vector<String> v_alternate_server_list_from_cli = new Vector<String>();
        String line = null;
       
		log.info("*****1) CLI'a gir ve Alternate Server'ların IPlerini ActualIP listesine yaz.***");

        /*Get Alternative Server List from Cli*/
        /*	1) CLI'a gir ve Alternate Server'ların IPlerini ActualIP listesine yaz.*/
        
        v_alternate_server_list_from_cli = getAlternateServersFromCli();
       
		try
		{
			File file = new File(serverListPath);
			BufferedReader br = new BufferedReader(new FileReader(file));
			String serverentity;
			String ip_from_config;
			String port_from_config;
			boolean exist = false;

			log.info("*****	2) Default IP listesini oku. IP:portlara tek tek ping at.***");
			
			while ((serverentity = br.readLine()) != null)
			{
				String[] config_ip_ports = serverentity.split(":");
				v_alternate_list_from_config.add(config_ip_ports[0] + ":" + config_ip_ports[1]);
				try
				{
					InetAddress server = InetAddress.getByName(config_ip_ports[0]);
				    SocketAddress sockaddr = new InetSocketAddress(server, Integer.parseInt(config_ip_ports[1]));
				    Socket socket = new Socket();
				    socket.connect(sockaddr, 1000);
		            socket.close();
					socket = null;
					int add = 1;
					log.info("****************2-1-1) Ping başarılıysa IP'yi Actual IP'de ara." + serverentity );
					
		    		for (String a : v_alternate_server_list_from_cli)
		            {
						//log.info("COMPARE!!! " + a + " =? " + problematic_alternate_server + " Result: " + a.equals(problematic_alternate_server));
						
						if (a.equals(serverentity))
						{
							log.info("*****	2-1-2) Varsa default IP listesindeki bir sonrakine geç.***" + serverentity);
							exist = true;
						}						
		            }
		    		
		    		if(!exist)
		    		{
						log.info("*****	2-1-3) Yoksa aas ip:port***" + serverentity);
				        alternateServerConfiguration(serverentity, add);		    			
		    		}
					
				}
			    catch (SocketTimeoutException ex) 
			    {
			    	int remove = -1;					
			        log.info("*****	2-2-1) Ping başarısızsa IP'yi Actual IP'de ara.***" + serverentity);

					for (String a : v_alternate_server_list_from_cli)
		            {
						//log.info("COMPARE!!! " + a + " =? " + problematic_alternate_server + " Result: " + a.equals(problematic_alternate_server));
						
						if (a.equals(serverentity))
						{
							exist = true;
							break;						
						}
						else
						{
							log.info("***** 2-2-3) Yoksa default IP listesindeki bir sonrakine geç.***" + a);
						}						
		            }
					
		    		if(exist)
		    		{
						log.info("***** 2-2-2) Varsa das ip:port***" + serverentity);
				        alternateServerConfiguration(serverentity, remove);		    			
		    		}
		            
		            log.info("***** Liste bittiyse 60 sn bekle.***" + serverentity);
		            TimeUnit.SECONDS.sleep(60);
			        
			    }
		        catch( Exception e )
		        {
		            log.error("Unknown socket exception.");
		        }
			}
			br.close();
		}
        catch( Exception e )
        {
            log.error("Config file problem.");
        }
	}

	public void alternateServerConfiguration(String serverentity, int operation) throws InterruptedException, IOException {
		/*AAS*/
		/*Enter CLI*/
		TelnetClient telnet = new TelnetClient();
		//telnet.connect("172.21.193.105",5767);
		for (int retry_count = 0; retry_count < 3; retry_count++) 
		{
			  try 
			  {
				  telnet.connect("172.21.193.105",5767);
				  //log.info("AGAIN Trying to connect to " + server + ":" + config_ip_ports[1]);
				  break;
			  } 
			  catch (IOException e) 
			  {
				  if (retry_count < 3) 
				  {
					  Thread.sleep(1);
				  } 
				  else 
				  {
					  throw e;
				  }
			  }
		}
		
		/*PASSWORD*/
		String pwd = "qwerty";
		telnet.getOutputStream().write(pwd.getBytes());
		telnet.getOutputStream().flush();
		
		if(operation == 1)
		{
			String command1 = "aas " + serverentity + "\r\n";
			log.info("AAS COMMAND: " + command1);
		
			telnet.getOutputStream().write(command1.getBytes());
			telnet.getOutputStream().flush();
		
			/*Initialized log*/
			InputStream instr = telnet.getInputStream();
			InputStreamReader is = new InputStreamReader(instr);
			BufferedReader breader = new BufferedReader(is);
			byte[] buff = new byte[1024];
			int ret_read = 0;
			System.out.println("AAS RESPONSE: " + new String(buff, 0, ret_read));
			log.info("AAS RESPONSE: " + new String(buff, 0, ret_read));
	        breader.close();
	        is.close();
		}
		
		else if(operation == -1)
		{
			String command1 = "das " + serverentity + "\r\n";
			log.info("DAS COMMAND: " + command1);
			
            telnet.getOutputStream().write(command1.getBytes());
            telnet.getOutputStream().flush();
			
			/*Initialized log*/
			InputStream instr = telnet.getInputStream();
			InputStreamReader is = new InputStreamReader(instr);
			BufferedReader breader = new BufferedReader(is);
			byte[] buff = new byte[1024];
			int ret_read = 0;
		    System.out.println("DAS RESPONSE: " + new String(buff, 0, ret_read));
			log.info("DAS RESPONSE: " + new String(buff, 0, ret_read));
	        breader.close();
	        is.close();
		}
		
		else
		{
			log.error("Neither add nor remove!");			
		}
		
		telnet.disconnect();
	}

	public Vector<String> getAlternateServersFromCli() {
		String line;
        Vector<String> v_alternate_server_list_from_cli = new Vector<String>();

        try
        {
			/*Enter CLI*/
	        //telnet.connect("172.21.193.105",5767);
    		TelnetClient telnet = new TelnetClient();
	        for (int retry_count = 0; retry_count < 3; retry_count++) 
	        {
	        	  try 
	        	  {
	        		  telnet.connect("172.21.193.105",5767);
	        		  //log.info("CLI Trying to connect to " + "172.21.193.105:5767");
	        		  break;
	        	  } 
	        	  catch (IOException e) 
	        	  {
	        		  if (retry_count < 3) 
	        		  {
	        			  Thread.sleep(1);
	        		  } 
	        		  else 
	        		  {
	        			  throw e;
	        		  }
	        	  }
	        }
	                    
			/*PASSWORD*/
	        String pwd = "qwerty";
            telnet.getOutputStream().write(pwd.getBytes());
            telnet.getOutputStream().flush();
			    		 
            String pc = "pc";
            telnet.getOutputStream().write(pc.getBytes());
            telnet.getOutputStream().flush();
            
	        /*Initialized log*/
	        InputStream instr = telnet.getInputStream();
	        InputStreamReader is = new InputStreamReader(instr);
	        BufferedReader breader = new BufferedReader(is);
	                    
            while ((line = breader.readLine()) != null)
            {
	            Pattern p = Pattern.compile("Alternate");
	            Matcher m = p.matcher(line);
	            while(m.find())
	            {
	                System.out.println(m.group());
					String[] arrOfStr = line.split(":");
					
					v_alternate_server_list_from_cli.add(arrOfStr[1].trim() + ":" + arrOfStr[2]);
	            }
            	if(line.contains("cli-max-output-sessions")) {
                    break;
                } 
            }
            			
    		telnet.disconnect();    	   
        }
        catch( Exception e )
        {
        	log.error("Telnet exception while getting the information from the proxy server.");
        }
        
		return v_alternate_server_list_from_cli;
	}
}
