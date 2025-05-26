package utilities;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class SvcCtl {

    private String                  serviceName;
    private int                     maxAttempts;
    private int                     intervalToCheck;
    private static final Logger     LOGGER = LogManager.getLogger(SvcCtl.class);

    /**
     * sc command
     */
    public enum ScCmd {

        START("start"),
        STOP("stop"),
        QUERY("query");

        private String val;

        private ScCmd(String val) {
            this.val = val;
        }

        public String getVal() {
            return val;
        }
    }

    /**
     * Sevice state
     */
    public enum SvcState {

        UNKNOWN(-1),
        STOPPED(1),
        START_PENDING(2),
        STOP_PENDING(3),
        RUNNING(4);

        private Integer val;

        private SvcState(int val){
            this.val = val;
        }

        public Integer getVal() {
            return val;
        }

        private static Map<Integer, SvcState> intToSvcStateMap = new HashMap<Integer, SvcState>();


        static{
            for(SvcState svcState : values()){
                intToSvcStateMap.put(svcState.getVal(), svcState);
            }
        }

        public static SvcState fromValue(int val){
            return (intToSvcStateMap.get(val) != null ? intToSvcStateMap.get(val) : SvcState.UNKNOWN);
        }
    }

    /**
     * Constructor
     * @param serviceName service name
     * @param maxAttempts  max attempt to check state of service
     * @param intervalToCheck sleep time for next check
     */
    public SvcCtl(String serviceName, int maxAttempts, int intervalToCheck) {

        this.serviceName = serviceName;
        this.maxAttempts = maxAttempts;
        this.intervalToCheck = intervalToCheck;
    }

    /**
     * Start the service by invoking sc command.
     * @return the state of the service
     * @throws Exception
     */
    protected SvcState startService() throws Exception {

        return execServiceController(ScCmd.START);
    }

    /**
     * Stop the service by invoking sc command
     * @return the state of the service
     * @throws Exception
     */
    protected SvcState stopService() throws Exception {

        return execServiceController(ScCmd.STOP);
    }

    /**
     * Query the state of the service by invoking the sc command
     * @return  the state of the service
     * @throws Exception
     */
    protected SvcState queryService() throws Exception {

        return execServiceController(ScCmd.QUERY);
    }

    /**
     * perform the windows sc command and return the state of service
     * @param scCmd one of the scCmd
     * @return the state of the service after the command is executed.
     */
    protected SvcState execServiceController(ScCmd scCmd){

        String command =  "sc " + scCmd.getVal() + " " + serviceName;
        int state = -1;
        try {
            final Process proc = Runtime.getRuntime().exec(command);
            BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            state = parseSCCmdOutput(br);
            int retCode = proc.waitFor();
            if (retCode != 0) {
                LOGGER.error("SvcCtl:execServiceController: Error Code of 'sc' is:" + retCode);
            }
        }
        catch (IOException e) {
            LOGGER.error("SvcCtl:execServiceController: IOException caught:" + e);
        }
        catch (InterruptedException e) {
            LOGGER.error("SvcCtl:execServiceController: InterruptedException caught:" + e);
        }
        return SvcState.fromValue(state);
    }

    /**
     * stop service and check the state of service
     * @return true if succeed, false if fail
     */
    public boolean stopServiceAndCheckState() {
        boolean results = false;
        try {
            if (queryService() == SvcState.STOPPED) {
                return true;
            }
            if (stopService() == SvcState.STOPPED) {
                return true;
            }
            for (int i = 0; i < maxAttempts; i++) {
                if (queryService() == SvcState.STOPPED)  {
                    results = true;
                    break;
                }
                Thread.sleep(intervalToCheck);
            }
            if (results)   //a bit sleep after successful service stop
                Thread.sleep(10000);
            return results;
        }
        catch (InterruptedException e) {
            LOGGER.error("SvcCtl:stopServiceAndCheckState: InterruptedException caught:" + e);
        }
        catch (Exception e) {
            LOGGER.error("SvcCtl:stopServiceAndCheckState: Exception caught:" + e);
        }
        return results;
    }

    /**
     * start service and check the state of service
     * @return true if succeed, false if fail
     */
    public boolean startServiceAndCheckState(){

        try {
            if (queryService() == SvcState.RUNNING) {
                return true;
            }
            if (startService() == SvcState.RUNNING) {
                return true;
            }
            for (int i = 0; i < maxAttempts; i++) {
                if (queryService() == SvcState.RUNNING)
                    return true;
                Thread.sleep(intervalToCheck);
            }
        }
        catch (InterruptedException e) {
            LOGGER.error("SvcCtl:startServiceAndCheckState: InterruptedException caught:" + e);
        }
        catch (Exception e) {
            LOGGER.error("SvcCtl:startServiceAndCheckState: Exception caught:" + e);
        }
        return false;
    }

    /**
     * parse the sc command output, return the state of the service
     * @param bufferReader stream from sc command output
     * @return state of service
     */
    protected int parseSCCmdOutput(BufferedReader bufferReader){

        String line;
        int state = SvcState.UNKNOWN.getVal();
        try {
            // searches for state in the child process output and check the status code
            while ((line = bufferReader.readLine()) != null) {
                int index;
                LOGGER.debug("readline:" + line + "\n");
                if ((index = line.indexOf(" STATE ")) != -1) {
                    if ((index = line.indexOf(" : ", index)) != -1)
                        state = Integer.parseInt(line.substring(index + 3, index + 4));
                }
            }
        }
        catch(IOException e) {
            LOGGER.error("SvcCtl:parseSCCmdOutput: IOException caught:" + e);
        }
        return state;
    }
}