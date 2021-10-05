package com.ttech.tvoip.coma.service;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class HealthCheckerManager {

	@Autowired
	private ApplicationContext appContext;
	
	@Value("#{'${list.of.coturns.corpus:172.21.193.105|qwerty|86.108.188.238:443,86.108.188.237:42000,86.108.188.239:443,86.108.188.240:443,86.108.188.241:443,86.108.188.242:443;}'.split(';')}") 
	private String[] corpusEntityList;
	
	final Map<String, HealthCheckerBean> healthCheckerServiceMap = new HashMap<>();
	
	@PostConstruct
	public void HealthCheckerManager() {
		
		for(String corpusEntity : corpusEntityList) {
			
			String[] corpusEntityDatas = corpusEntity.split("\\|");
               	
        	String proxyCoturn = corpusEntityDatas[0];
        	healthCheckerServiceMap.put(proxyCoturn, (HealthCheckerBean) appContext.getBean("healthCheckerBean", corpusEntity));
        	log.info("Proxy coturn added serviceMap: " + proxyCoturn);
        			
        }
		
	}
	
	
	
	
	
	
}
