package com.bip.coma.service;

import com.bip.coma.alarm.Alarm;
import com.bip.coma.alarm.AlarmManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.Vector;

@Service
@Slf4j
@Scope("prototype")
public class ProxyHealthChecker {
    @Autowired
    AlarmManager alarmManager;

    @Autowired
    private ApplicationContext appContext;

    @Autowired
    private CLIService cliService;

    private Alarm proxyAlarm;

    private ProxyCoturn proxyCoturn;

    private Vector<String> tmpCliAlternateServers = new Vector<>();
    private Vector<WorkerCoturn> primaryWorkers = new Vector<>();
    private Vector<WorkerCoturn> secondaryWorkers = new Vector<>();
    private Boolean sparesNeeded = true;

    //@Value("${workerPingPeriodCount:10}")
    //@Value("#{${jdbc.timeout}/1000})
    //private int workerPingPeriodCount; // TODO implement this in a smart way
    //private int readProxyCounter = 0;

    public ProxyHealthChecker(ProxyCoturn proxyCoturn) {
        this.proxyCoturn = proxyCoturn;

        log.info("ProxyHealthChecker created [{}]", this.proxyCoturn.toString());
    }

    @PostConstruct
    public void init() {

        try {
            log.info("Init method invoked for proxy:{} with primary workers:{}, secondary workers {}",
                    this.proxyCoturn.getId(), this.proxyCoturn.getPrimaryWorkers(), this.proxyCoturn.getSecondaryWorkers());

            this.cliService.setProxyCoturn(proxyCoturn);

            this.proxyAlarm = alarmManager.createProxyAlarm(proxyCoturn.getId());

            /*Get Alternative Server List from Cli*/
            this.getAlernateServers();
            if (tmpCliAlternateServers.size() == 0) {
                log.warn("Init getAlternate servers failed or no alternate server proxy={}", this.proxyCoturn.getId());
            }

            log.info("Alternate server list read from cli proxy={}, alterSrvList={}",
                    this.proxyCoturn.getId(), tmpCliAlternateServers);

            /*Primary workers*/
            for (String worker : this.proxyCoturn.getPrimaryWorkers()) {
                primaryWorkers.add((WorkerCoturn) appContext.getBean("workerCoturn", worker, this.proxyCoturn, false));
            }
            log.info("Primary Workers: {}", this.proxyCoturn.getPrimaryWorkers());

            if(this.proxyCoturn.getSecondaryWorkers() != null && this.proxyCoturn.getSecondaryWorkers().size() > 0) {
                /*Secondary workers*/
                for (String worker : this.proxyCoturn.getSecondaryWorkers()) {
                    secondaryWorkers.add((WorkerCoturn) appContext.getBean("workerCoturn", worker, this.proxyCoturn, true));
                }
                log.info("Secondary Workers: {}", this.proxyCoturn.getSecondaryWorkers());
            } else {
                log.info("Secondary Workers are not defined. proxy={}", this.proxyCoturn.getId());
            }


            // Check consistency of data and check if spares are  needed
            Boolean found = false;
            for (String alternateServer : tmpCliAlternateServers) {
                for (WorkerCoturn workerCoturn : primaryWorkers) {
                    if (alternateServer.equals(workerCoturn.getId())) {
                        sparesNeeded = false;
                        found = true;
                        break;
                    }
                }
                if(!found) {
                    for (WorkerCoturn workerCoturn : secondaryWorkers) {
                        if (alternateServer.equals(workerCoturn.getId())) {
                            found = true;
                            break;
                        }
                    }
                    if(!found){
                        log.warn("There is a mismatch with the alternate server conf !!! proxy={} altSrv={}",
                                proxyCoturn.getId(), alternateServer);
                        //TODO what?
                    }
                }
                found = false;
            }

            proxyAlarm.clear();

        } catch (Exception e) {
            log.error("Exiting application!! Exception while initializing proxy {}:", proxyCoturn.getId() , e);
            int exitCode = SpringApplication.exit(appContext, () -> 0);
            System.exit(exitCode);
        }
    }

    private void getAlernateServers() throws IOException {

        /*Get Alternative Server List from Cli*/
        try {
            tmpCliAlternateServers = cliService.getAlternateServers();
            log.info("Alternate servers read from cli {}", tmpCliAlternateServers);
            proxyAlarm.clear();
        } catch (IOException e) {
            log.error("Alternate servers read from cli failed! {}", this.proxyCoturn.getId(), e);
            proxyAlarm.raise("Telnet"); // TODO: test this
            throw e;
        }
    }


    @Scheduled(initialDelay = 2000, fixedDelayString = "${auto_check_period:60}000")
    public void coturnAutoCheck() {

        log.info("CoTurnAutoCheck: started proxy={}, cliAltServers={}", this.proxyCoturn.getId(), tmpCliAlternateServers);

        try {
            this.getAlernateServers();
        } catch (Exception e){
            log.error("CoTurnAutoCheck: Exception received proxy={}. Discarding this cycle.", this.proxyCoturn.getId());
            return;
        }

        /*
        readProxyCounter++;
        if (readProxyCounter < workerPingPeriodCount) {
            return;
        }
        readProxyCounter = 0;
        */

        log.info("CoTurnAutoCheck: checking primary workers {}", this.proxyCoturn.getPrimaryWorkers());
        checkWorkerList(primaryWorkers);

        if(secondaryWorkers.size() != 0) {
            log.info("CoTurnAutoCheck: checking secondary workers {}", this.proxyCoturn.getSecondaryWorkers());
            checkWorkerList(secondaryWorkers);
        }
    }

    private void checkWorkerList(Vector<WorkerCoturn> workerList){
        for (WorkerCoturn worker : workerList) {

            worker.checkState();
            worker.setPresent(this.tmpCliAlternateServers.contains(worker.getId()));

            log.debug("workerCandidate:" + worker.getId() + " state:" + worker.checkState() + " contain:" + worker.isPresent()
                    + " sparesNeeded:" + this.sparesNeeded + " redundant:" + worker.isRedundant());

            this.takeAction(worker);
        }
    }

/* Decision matrix
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
    private void takeAction(WorkerCoturn worker) {

        if (worker.isActive() == false && worker.isPresent() == false) {
            // Do nothing
            return;

        } else if (worker.isActive() == false && worker.isPresent() == true) {

            // Delete alternate server from proxy coturn
            log.info("DAS command stage1 proxy={} worker={}", this.proxyCoturn.getId(), worker.getId());

            try {
                cliService.runCliCommand(CLIService.CLI_DAS_COMMAND, worker.getId());
                tmpCliAlternateServers.remove(worker.getId());

                if ((tmpCliAlternateServers.size() == 0) && worker.isRedundant() == false) {
                    sparesNeeded = true;
                    log.info("Spares Needed proxy={}", this.proxyCoturn.getId());
                }
            } catch (IOException e) {
                log.error("DAS command stage1 failed proxy:{} worker:{}",
                        this.proxyCoturn.getId(), worker.getId(), e);
            }

        } else if (worker.isActive() == true && worker.isPresent() == false) {

            if (this.sparesNeeded == true ||
                    (this.sparesNeeded == false && worker.isRedundant() == false)) {

                log.info("AAS command proxy={} worker={}", this.proxyCoturn.getId(), worker.getId());

                try {
                    cliService.runCliCommand(CLIService.CLI_AAS_COMMAND, worker.getId());
                    tmpCliAlternateServers.add(worker.getId());

                } catch (IOException e) {
                    log.error("AAS command failed for proxy:{} worker:{}",
                            this.proxyCoturn.getId(), worker.getId(), e);
                }

                if (worker.isRedundant() == false) {

                    sparesNeeded = false;
                    log.info("Spares not needed any more proxy={}", this.proxyCoturn.getId());
                }
            }
        } else if (worker.isActive() == true && worker.isPresent() == true &&
                sparesNeeded == false && worker.isRedundant() == true) {

            log.info("DAS command redundant proxy={} worker={}", this.proxyCoturn.getId(), worker.getId());

            try {
                cliService.runCliCommand(CLIService.CLI_DAS_COMMAND, worker.getId());
                tmpCliAlternateServers.remove(worker.getId());

            } catch (IOException e) {
                log.error("DAS command reduntant failed proxy:{} worker:{}",
                        this.proxyCoturn.getId(), worker.getId(), e);
            }
        } else {
            //TODO nothing
            return;
        }
    }
}
