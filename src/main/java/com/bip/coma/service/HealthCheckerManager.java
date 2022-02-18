package com.bip.coma.service;

import java.util.List;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class HealthCheckerManager {

	@Autowired
	private ApplicationContext appContext;

	private ProxyProperties proxyProperties;

	@PostConstruct
	public void init() {

		List<ProxyCoturn> proxyCoturnList = this.proxyProperties.getList();
		for (ProxyCoturn proxyCoturn : proxyCoturnList) {
			log.info("proxy.list {}", proxyCoturn.getId());
			appContext.getBean("proxyHealthChecker", proxyCoturn);
		}
	}

	@Autowired
	public void setProxyProperties(ProxyProperties proxyProperties) {
		this.proxyProperties = proxyProperties;
	}
}
