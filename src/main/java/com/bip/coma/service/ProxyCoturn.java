package com.bip.coma.service;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ProxyCoturn {
    private String cliIp;
    private int cliPort;
    private String cliSecret;
    private List<String> primaryWorkers;
    private List<String> secondaryWorkers;
    private Boolean checkIntegrity = false;


    public String getId() {
        return this.cliIp + ":" + this.getCliPort();
    }

    public String toString(){
        StringBuilder str = new StringBuilder();
        str.append(cliIp).append("|");
        str.append(cliPort).append("|");
        str.append(cliSecret).append("|");
        str.append(primaryWorkers).append("|");
        str.append(secondaryWorkers);

        return str.toString();
    }

    public String getCliIp() {
        return cliIp;
    }

    public void setCliIp(String cliIp) {
        this.cliIp = cliIp;
    }

    public int getCliPort() {
        return cliPort;
    }

    public void setCliPort(int cliPort) {
        this.cliPort = cliPort;
    }

    public String getCliSecret() {
        return cliSecret;
    }

    public void setCliSecret(String cliSecret) {
        this.cliSecret = cliSecret;
    }

    public List<String> getPrimaryWorkers() {
        return primaryWorkers;
    }

    public void setPrimaryWorkers(List<String> primaryWorkers) {
        this.primaryWorkers = primaryWorkers;
    }

    public List<String> getSecondaryWorkers() {
        return secondaryWorkers;
    }

    public void setSecondaryWorkers(List<String> secondaryWorkers) {
        this.secondaryWorkers = secondaryWorkers;
    }

    public Boolean getCheckIntegrity() {
        return checkIntegrity;
    }

    public void setCheckIntegrity(Boolean checkIntegrity) {
        this.checkIntegrity = checkIntegrity;
    }
}
