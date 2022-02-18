package com.bip.coma.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties("proxy")
public class ProxyProperties {

    private List<ProxyCoturn> list = new ArrayList<>();

    public List<ProxyCoturn> getList() {
        return list;
    }

    public void setList(List<ProxyCoturn> list) {
        this.list = list;
    }
}
