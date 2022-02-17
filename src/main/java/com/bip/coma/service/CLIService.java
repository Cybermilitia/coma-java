package com.bip.coma.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.net.telnet.TelnetClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@Scope("prototype")
public class CLIService {

    final static private String CLI_PC_COMMAND = "pc";
    final static public  String CLI_AAS_COMMAND = "aas";
    final static public  String CLI_DAS_COMMAND = "das";

    final static private String CLI_PASSWORD_PROMPT = "Enter password:";
    final static private String CLI_COMMAND_PROMPT = ">";

    @Value("${cli.connectTimeout:1000}")
    private int connectTimeout;

    @Value("${cli.readTimeout:1000}")
    private int readTimeout;

    private InputStream in = null;
    private PrintStream out = null;

    TelnetClient client = null;

    ProxyCoturn proxyCoturn = null;

    private void connect() throws IOException {

        log.info("Connecting proxy cli:{} ", proxyCoturn.getId());

        client = new TelnetClient();

        try {
            client.setConnectTimeout(connectTimeout);

            // Connect
            client.connect(proxyCoturn.getCliIp(), proxyCoturn.getCliPort());
            client.setSoTimeout(readTimeout);

            // Wait for the password prompt
            in = client.getInputStream();
            out = new PrintStream(client.getOutputStream());

            readUntil(CLI_PASSWORD_PROMPT);
            write(proxyCoturn.getCliSecret());

            readUntil(CLI_COMMAND_PROMPT);

        } catch(Exception e) {
            //alarmGroup.getAlarm(proxyCoturn).raise("TELNET");
            log.error("Telnet exception while connecting to cli proxy:{} ", proxyCoturn.getId(), e);
            disconnect();
            throw e;
        }
    }

    private void disconnect() throws IOException {
        try {
            if(client != null) {
                client.disconnect();
            }
        } catch (IOException ioe) {
            throw ioe;
        } finally {
            client = null;
        }

    }

    @Retryable(value = { IOException.class }, maxAttempts = 2)
    public void runCliCommand(String command, String worker) throws IOException {

        log.info("Command running for proxy:{} operation:{} worker:{}", proxyCoturn.getId(), command, worker);

        try {

            // Connect
            connect();

            // Write command
            write(command + " " + worker);

            // Read any response
            readUntil(CLI_COMMAND_PROMPT);

        } catch( Exception e ) {
            //alarmGroup.getAlarm(proxyCoturn).raise("TELNET");
            log.error("Telnet exception while running cli command proxy:{} operation:{} worker:{}",
                    proxyCoturn.getId(), command, worker, e);
            throw e;
        } finally {
            disconnect();
        }
    }


    @Retryable(value = { IOException.class }, maxAttempts = 2)
    public Vector<String> getAlternateServers() throws IOException {

        Vector<String> alternateServerListFromCli = new Vector<>();
        BufferedReader bReader = null;

        log.info("GetAlternateServers running for proxy:{}", proxyCoturn.getId());

        try
        {
            connect();

            // Write Command
            write(CLI_PC_COMMAND);

            // Read Response
            bReader = new BufferedReader(new InputStreamReader(in));

            String line = bReader.readLine();
            while (line != null) {
                Pattern p = Pattern.compile("Alternate");
                Matcher m = p.matcher(line);
                while(m.find())
                {
                    log.debug(line);
                    String[] arrOfStr = line.split(":");

                    alternateServerListFromCli.add(arrOfStr[1].trim() + ":" + arrOfStr[2]);
                }
                if(line.contains("cli-max-output-sessions")) {
                    break;
                }
                line = bReader.readLine();
            }

        } catch(Exception e) {
            //alarmGroup.getAlarm(proxyCoturn).raise("TELNET");
            log.error("Telnet exception while getting alternate servers proxy:{}:{}",
                    proxyCoturn.getCliIp(), proxyCoturn.getCliPort(), e);
            throw e;
        } finally {
            if(bReader != null) {
                bReader.close();
            }
            disconnect();
        }

        return alternateServerListFromCli;
    }

    /**
     * Reads input stream until the given pattern is reached. The
     * pattern is discarded and what was read up until the pattern is
     * returned.
     */
    private String readUntil(String pattern) throws IOException {
        char lastChar = pattern.charAt(pattern.length() - 1);
        StringBuilder sb = new StringBuilder();
        int c;

        while((c = in.read()) != -1) {
            char ch = (char) c;
            //System.out.print(ch);
            sb.append(ch);
            if(ch == lastChar) {
                String str = sb.toString();
                if(str.endsWith(pattern)) {
                    return str.substring(0, str.length() -
                            pattern.length());
                }
            }
        }

        return null;
    }

    private void write(String command) {
        out.println(command);
        out.flush();
    }

    public ProxyCoturn getProxyCoturn() {
        return proxyCoturn;
    }

    public void setProxyCoturn(ProxyCoturn proxyCoturn) {
        this.proxyCoturn = proxyCoturn;
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public int getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(int readTimeout) {
        this.readTimeout = readTimeout;
    }
}
