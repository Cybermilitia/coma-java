package com.bip.coma.service;

import java.net.ConnectException;
import java.net.InetAddress;

import java.net.SocketTimeoutException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.Vector;

import com.bip.coma.alarm.Alarm;
import com.bip.coma.alarm.AlarmManager;
import de.javawi.jstun.attribute.ChangeRequest;
import de.javawi.jstun.header.MessageHeader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;

import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

@Slf4j
@Service
@Scope("prototype")
public class WorkerCoturn {

    @Autowired
    AlarmManager alarmManager;

    private ProxyCoturn proxyCoturn;
    private Alarm workerAlarm;
    private String ip = null;
    private int port = 0;
    private Boolean active = false;
    private Boolean redundant = false;
    private Boolean present = false;

    private byte[] bindingRequestData;

    public WorkerCoturn(String workerAddr, ProxyCoturn proxyCoturn, Boolean redundant) {
        String[] configIpPort = workerAddr.split(":");
        this.ip = configIpPort[0];
        this.port = Integer.parseInt(configIpPort[1]);
        this.proxyCoturn = proxyCoturn;
        this.redundant = redundant;
    }

    @PostConstruct
     private void init() {
        try {
            this.workerAlarm = alarmManager.createWorkerAlarm(this.getId()+"_"+proxyCoturn.getId());

            MessageHeader bindingRequest = new MessageHeader(MessageHeader.MessageHeaderType.BindingRequest);
            //bindingRequest.generateTransactionID();
            bindingRequestData = bindingRequest.getBytes();

            this.workerAlarm.clear();
        } catch (Exception e){
            log.error("Exception received while creating WorkerCoturn {}", this.getId(), e);
            //TODO what
        }
    }

    public Boolean checkState()  {
        this.active = false;
        try {

            DatagramSocket datagramSocket = new DatagramSocket();
            datagramSocket.setReuseAddress(true);
            datagramSocket.setSoTimeout(1000);

            DatagramPacket sendPacket = new DatagramPacket(this.bindingRequestData, this.bindingRequestData.length,
                    InetAddress.getByName(this.ip), this.port);
            datagramSocket.send(sendPacket);

            DatagramPacket receivePacket = new DatagramPacket(new byte[32], 32);
            datagramSocket.receive(receivePacket);

            datagramSocket.close();

            /*Sync OK. State must be set true*/
            this.active = true;

            log.debug("WorkerCoturn alive " + this.getId());
            this.workerAlarm.clear();

        } catch (SocketTimeoutException ex) {

            log.error("In Worker constructor: TIMEOUT " + this.getId());
            workerAlarm.raise("Timeout");
            //alarmGroup.getAlarm(this.toString()).raise("TIMEOUT");
            /*Sync NOK. State must be set false*/
            this.active = false;

        } catch( ConnectException  e ) {

            log.error("In Worker constructor: Connection Refused " + this.getId());
            workerAlarm.raise("Connection Refused");
            //alarmGroup.getAlarm(this.toString()).raise("Connection Refused");
            //log.error("Connection refused: " + workerCandidate,e);
            /*Sync NOK. State must be set false*/
            this.active = false;

        } catch( Exception e ) {

            log.error("In Worker constructor: UNKNOWN " + this.getId(),e);
            workerAlarm.raise();
            //alarmGroup.getAlarm(this.toString()).raise("UNKNOWN");
            //log.error("Unknown workers socket exception: " + workerCandidate,e);
            /*Sync NOK. State must be set false*/
            this.active = false;
        }
        return this.active;
    }

    public String getId() {return this.ip + ":" + this.port;}

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public Boolean isActive() {
        return active;
    }

    public void setActive(Boolean state) {
        this.active = state;
    }

    public Boolean isRedundant() {
        return redundant;
    }

    public void setRedundant(Boolean redundant) {
        this.redundant = redundant;
    }

    public Boolean isPresent() {
        return present;
    }

    public void setPresent(Boolean present) {
        this.present = present;
    }
}
