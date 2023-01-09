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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

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

    private Set<String> tmpAlternateServerSet;

    private ArrayList<WorkerCoturn> primaryWorkers = new ArrayList<>();
    private ArrayList<WorkerCoturn> secondaryWorkers = new ArrayList<>();
    private Boolean sparesNeeded = false;


    public ProxyHealthChecker(ProxyCoturn proxyCoturn) {
        this.proxyCoturn = proxyCoturn;

        log.info("ProxyHealthChecker created [{}]", this.proxyCoturn.toString());
    }

    @PostConstruct
    public void init() {

        try {
            log.info("Init method invoked for proxy:{} integrityCheck:{} with primary workers:{} secondary workers {}",
                    this.proxyCoturn.getId(), this.proxyCoturn.getCheckIntegrity(),
                    this.proxyCoturn.getPrimaryWorkers(), this.proxyCoturn.getSecondaryWorkers());

            this.cliService.setProxyCoturn(proxyCoturn);

            this.proxyAlarm = alarmManager.createProxyAlarm(proxyCoturn.getId());

            /*Get Alternative Server List from Cli*/
            this.getAlternateServers();
            if (tmpAlternateServerSet.size() == 0) {
                log.warn("Init getAlternate servers failed or no alternate server proxy={}", this.proxyCoturn.getId());
            }

            log.info("Alternate server list read from cli proxy={}, alterSrvList={}",
                    this.proxyCoturn.getId(), tmpAlternateServerSet);

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

            proxyAlarm.clear();

        } catch (Exception e) {
            log.error("Exiting application!! Exception while initializing proxy {}:", proxyCoturn.getId() , e);
            alarmManager.getSystemAlarm().raise("InitFailed");
            int exitCode = SpringApplication.exit(appContext, () -> 0);
            System.exit(exitCode);
        }
    }

    private void getAlternateServers() throws IOException {

        /*Get Alternative Server List from Cli*/
        try {
            ArrayList<String> tmpList = cliService.getAlternateServers();
            log.info("Alternate servers read from cli {}", tmpList);
            proxyAlarm.clear();

            if(this.proxyCoturn.getCheckIntegrity()) {
                tmpAlternateServerSet = checkAlternateServerIntegrity(tmpList);
            } else {
                tmpAlternateServerSet = new HashSet<>(tmpList);
            }

        } catch (IOException e) {
            log.error("Alternate servers read from cli failed! {}", this.proxyCoturn.getId(), e);
            proxyAlarm.raise("Telnet"); // TODO: test this
            throw e;
        }
    }


    @Scheduled(initialDelay = 2000, fixedDelayString = "${system.auto_check_period:60}000")
    public void coturnAutoCheck() {

        log.info("CoTurnAutoCheck: started proxy={}, cliAltServers={}", this.proxyCoturn.getId(), tmpAlternateServerSet);

        try {
            this.getAlternateServers();
        } catch (Exception e){
            log.error("CoTurnAutoCheck: Exception received proxy={}. Discarding this cycle.", this.proxyCoturn.getId());
            return;
        }

        log.info("CoTurnAutoCheck: checking primary workers {}", this.proxyCoturn.getPrimaryWorkers());
        checkWorkerList(primaryWorkers);

        if(secondaryWorkers.size() != 0) {
            log.info("CoTurnAutoCheck: checking secondary workers {}", this.proxyCoturn.getSecondaryWorkers());
            checkWorkerList(secondaryWorkers);
        }
    }

    private Set<String> checkAlternateServerIntegrity(ArrayList<String> serverList){

        // Eliminate duplicates
        Set<String> serverSet = new HashSet<>();
        for(String server:  serverList){
            if(server == null){
                continue;
            }
            if(serverSet.add(server) == false){
                try {
                    /* add function fail means the same item is in the set.
                       das operation removes only the first found item in coturn.
                     */
                    cliService.runCliCommand(CLIService.CLI_DAS_COMMAND, server);
                }catch(Exception e){
                    log.error("DAS command failed proxy:{} alternateServer:{}",
                            this.proxyCoturn.getId(), server, e);
                }
            }
        }

        // Remove undefined ones
        Boolean found;
        for(String server: serverSet){
            found = false;
            for (WorkerCoturn workerCoturn : primaryWorkers) {
                if (server.equals(workerCoturn.getId())) {
                    found = true;
                    break;
                }
            }
            if(!found) {
                for (WorkerCoturn workerCoturn : secondaryWorkers) {
                    if (server.equals(workerCoturn.getId())) {
                        found = true;
                        break;
                    }
                }
            }
            // Alternate server is not defined in coma. Remove it.
            if(!found){
                try{
                    cliService.runCliCommand(CLIService.CLI_DAS_COMMAND, server);
                }catch(Exception e){
                    log.error("DAS failed while integrity check. proxy:{} alternateServer:{}",
                            this.proxyCoturn.getId(), server, e);
                }
            }
        }
        return serverSet;
    }


    private void checkWorkerList(ArrayList<WorkerCoturn> workerList){
        for (WorkerCoturn worker : workerList) {

            worker.checkState();
            worker.setPresent(this.tmpAlternateServerSet.contains(worker.getId()));

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
            if ((tmpAlternateServerSet.size() == 0) && worker.isRedundant() == false) {
                sparesNeeded = true;
                log.info("Spares Needed at the beginning - proxy={}", this.proxyCoturn.getId());
            }
            return;

        } else if (worker.isActive() == false && worker.isPresent() == true) {

            // Delete alternate server from proxy coturn
            log.info("DAS command stage1 proxy={} worker={}", this.proxyCoturn.getId(), worker.getId());

            try {
                cliService.runCliCommand(CLIService.CLI_DAS_COMMAND, worker.getId());
                tmpAlternateServerSet.remove(worker.getId());

                if ((tmpAlternateServerSet.size() == 0) && worker.isRedundant() == false) {
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
                    tmpAlternateServerSet.add(worker.getId());

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
                tmpAlternateServerSet.remove(worker.getId());

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
