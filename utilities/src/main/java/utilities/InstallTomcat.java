package utilities;

import com.sun.jna.platform.win32.WinReg;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import java.io.File;
import java.io.FileFilter;
import java.io.FileWriter;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.AclEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import org.apache.commons.io.IOUtils;
import org.w3c.dom.*;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;


public class InstallTomcat {

    //expected new tomcat installer file name prefix
    public static final String         TOMCAT_INSTALLER_PREFIX = "apache-tomcat-9.0";  //major must be 9 and minor must be 0, only support build to build upgrade.
    public static final String         TOMCAT_INSTALLER_EXTENSION = ".exe";

    //server.xml location
    private static final String         TOMCAT_SERVER_XML_PATH = "\\conf\\server.xml";

    //catalina.properties location
    private static final String         TOMCAT_CATALINA_PROPERTIES_PATH = "\\conf\\catalina.properties";

    //jar files to be restored
    private static final String         ICM_WEBSETUP_SHARED_JAR_PATH = "\\lib\\icm-websetup-shared.jar";
    private static final String         ICM_REGISTRY_JAR_PATH = "\\lib\\registry.jar";
    private static final String         JNTSERVICES_JAR_PATH = "\\lib\\jntservices.jar";
    private static final String         CATALINA_JMX_REMOTE_JAR_PATH = "\\lib\\catalina-jmx-remote.jar";

    //clean up folder after upgrade
    private static final String         TOMCAT_WEBAPPS_PATH = "\\webapps";
    private static final String         TOMCAT_SERVER_ROOT_PATH = "\\ROOT";
    private static final String         TOMCAT_SERVER_MANAGER_PATH = "\\manager";
    private static final String         TOMCAT_SERVER_DOCS_PATH = "\\docs";

    /// post instaqllation files names and locations
    private static final String         WEBXML = "\\conf\\web.xml";
    private static final String         WEBCONFIG = "\\web.config";
    private static final String         ISAPI = "\\isapi_redirect.dll";
    private static final String         IISCUSTOM = "\\server.xml.IIS.custom";
    private static final String  		uriMapFile = "uriworkermap.properties";
    private static final String  		workersFile = "workers.properties";

    //service ctrl
    public static final String          TOMCAT_SERVICENAME ="Tomcat9";
    public static final String          W3SVC_SERVICENAME = "W3SVC";
    public static final String          W3SVC_DESCRIPTIVE_NAME = "World Wide Web Publishing Service (W3SVC)";
    public static final int             MAX_ATTEMPTS = 60;
    public static final int             INTERVAL_TO_CHECK_STATE = 5000; //in mil seconds. total 5 minutes to wait for Tomcat to stop or start

    public static final String          LOG_CHECKING_MSG = "For more detailed information, please check the log located in ..\\InstallTomcatResults directory";
    public static final String          LOG_TOMCAT_STARTED_MSG = "Tomcat has been started.";
    public static final String          LOG_TOMCAT_STOPPED_MSG = "Tomcat service has been stopped.";
    public static final String          TOMCAT_INSTALLER_PROMPT = "Enter new tomcat installer location (for an example, c:\\tomcatInstaller\\apache-tomcat-9.0.22.exe): ";
    public static final String          ISTALL_PROCEED_PROMPT = "Do you want to proceed with the installation? (Yes/No): ";
    private static final String         SERVICE_STARTUP_TYPE_AUTOMATIC = "auto";
    private static final String         TOMACAT_DISPLAY_NAME = "Apache Tomcat9";

    //Logger instance for this class
    private static final Logger         LOGGER = LogManager.getLogger(InstallTomcat.class);

    public InstallTomcat() {

    }

    /**
     * Main entry point of the Tomcat Installation
     *
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {

        SystemConsole  console = new SystemConsole();
        boolean bInstall = true; 
        if (args.length >= 1) {

            if ("-install".equals(args[0])) {
                bInstall = checkArgsForUpgrade(args);
                if (bInstall && args.length >= 3 && ("-noconfirm".equals(args[1]))) {
                    console = new SystemConsole(new ArrayList<String>(Arrays.asList(args[2], args[1])));
                }
            } else if("-noconfirm".equals(args[0])) {
                if (args.length < 2) {
                    System.out.println("usage: -noconfirm <new tomcat installer full path name>");
                    bInstall = false;
                } else {
                    console = new SystemConsole(new ArrayList<String>(Arrays.asList(args[1], args[0])));
                }
            } else {
                System.out.println("Supported option: [-install| -noconfirm <new tomcat installer full path name>] or without any options");
                bInstall = false;
            }
        }

        if (bInstall) {
            prepareInstallAndDoInstall(console);
        }
    }

    protected static boolean checkArgsForUpgrade(String[] args) {

        boolean bInstall = true;

        if (args.length >= 2) {
            if ("-noconfirm".equals(args[1])) {
                if (args.length < 3) {
                    System.out.println("usage: -install -noconfirm <new tomcat installer full path name>");
                    bInstall = false;
                }
            } else {
                System.out.println("usage: -install -noconfirm <new tomcat installer full path name>");
                bInstall = false;
            }
        }
        return bInstall;
    }


    /**
     * Launch the console to prompt user input, then prepare install, install and post install.
     *
     * @param console
     * @throws Exception
     */
    protected static void prepareInstallAndDoInstall(SystemConsole console) throws Exception {

        LOGGER.info("This tool will install or reinstall Tomcat 9 to the provided point release.");

        //Prompt for new installer location if installer not specified from command line argument.
        String newInstallerPath = console.readLine(TOMCAT_INSTALLER_PROMPT);

        LOGGER.debug(TOMCAT_INSTALLER_PROMPT + newInstallerPath);

        FileMgr fileMgr = new FileMgr();
        //validate input to make sure it is a valid path for installer
        if (!fileMgr.validateInstallerPath(newInstallerPath))
            return;

        //validate the install major number is 9 and extension is ".exe"
        String installerFileName = new File(newInstallerPath).getName();
        if (!fileMgr.validateInstallerFileName(installerFileName, TOMCAT_INSTALLER_PREFIX, TOMCAT_INSTALLER_EXTENSION)) {
            LOGGER.info("Tomcat Installer should have 9 as major version number, 0 as minor version number and extension is '.exe'.");
            return;
        }
     
        //need to confirm with user, if they want to proceed for installation, if yes, go and install it
        if (!installStepConfirmation(console))
            return;
        
        //start installation process
        String installerVersionString = getInstallerVersion(installerFileName);
        LOGGER.info("Starting Tomcat installation with version "+ installerVersionString);
       
        /// here is the idea that we do not need to specify the current version of tomcat and current drive as those could be also not specified(exist)
        /// we have to add a logic here to verify first if tomcat services is already installed or not.
        
        String currentTomcatVersion = RegistryManager.getCurrentInstallVersion();
        if (StringUtils.isBlank(currentTomcatVersion)) {
        	LOGGER.info(" Tomcat is not found on this system");
            install(newInstallerPath, installerVersionString, fileMgr);  
        }
        else {
        	LOGGER.info(" Tomcat is found on this system, will start rebuilding it by uninstalling and installing it.");
            uninstallAndInstall(newInstallerPath, installerVersionString, fileMgr);
        }
    }
    
    /**
     * Install work and post install
     * @param newInstallerPath
     */
    protected static void install(String newInstallerPath, String installerVersionString, FileMgr fileMgr) {
 
    	 SvcCtl tomcatSvcCtl = new SvcCtl(TOMCAT_SERVICENAME, MAX_ATTEMPTS, INTERVAL_TO_CHECK_STATE);
         SvcCtl w3SvcCtl = new SvcCtl(W3SVC_SERVICENAME, MAX_ATTEMPTS, INTERVAL_TO_CHECK_STATE);
          
    
         String currentInstallICMDrive= getCurrentICMInstallDrive(); 
         String currentInstallDir = currentInstallICMDrive+":\\icm\\tomcat"; 
     
         
         //Stop W3SVC service to be able to delete "bin folder" 
         LOGGER.info("Stopping " + W3SVC_DESCRIPTIVE_NAME + " ...");
         if (!w3SvcCtl.stopServiceAndCheckState()) {
             LOGGER.info("Failed to stop " + W3SVC_DESCRIPTIVE_NAME + " after " + (MAX_ATTEMPTS * INTERVAL_TO_CHECK_STATE / 1000) + " seconds");
         }
         LOGGER.info(W3SVC_DESCRIPTIVE_NAME + " has been stopped.");
         
         
         //5)Clean up all folders except logs
         if(!cleanUpTomcat(currentInstallDir)) {
       
              LOGGER.warn("Failed to cleanup tomcat. But still continuing to install the tomcat, " +
                      "as stopping here will make system doesn't has any Tomcat version");
          }

         LOGGER.info(" installing Tomcat on this path"+currentInstallDir);
         
         //6)silent run new installer
         if ( !installStepRunSilentInstaller(newInstallerPath, currentInstallDir, installerVersionString))
             return;
         
         LOGGER.info(" installation has been finished without postinstallation job");
         postInstallWork(fileMgr, currentInstallDir, tomcatSvcCtl, w3SvcCtl);    
         }
    
    
    
    /**
     * uninstall and Install work and post install
     * @param newInstallerPath
     */
    protected static void uninstallAndInstall(String newInstallerPath, String installerVersionString, FileMgr fileMgr) {

        SvcCtl tomcatSvcCtl = new SvcCtl(TOMCAT_SERVICENAME, MAX_ATTEMPTS, INTERVAL_TO_CHECK_STATE);
        SvcCtl w3SvcCtl = new SvcCtl(W3SVC_SERVICENAME, MAX_ATTEMPTS, INTERVAL_TO_CHECK_STATE);
        //1) Stop Tomcat service and W3SVC service
        if (!stopServiceStep(tomcatSvcCtl, w3SvcCtl))
            return;
        
        String currentInstallDir = RegistryManager.getCurrentInstallDirectory();
        if (!(StringUtils.isBlank(currentInstallDir))) {
            LOGGER.info("Tomcat seems it was already installed there so proceeding with uninstalling  ");
            if (!uninstallTomcat(currentInstallDir)) {
                LOGGER.warn("Tomcat uninstallation failed. But will still try to install the requested version of Tomcat 9.");
            }}
            
            
            //5)Clean up all folders except logs
           if(!cleanUpTomcat(currentInstallDir)) {
         
                LOGGER.warn("Failed to cleanup tomcat. But still continuing to install the tomcat, " +
                        "as stopping here will make system doesn't has any Tomcat version");
            }

        //6)silent run new installer
        if ( !installStepRunSilentInstaller(newInstallerPath, currentInstallDir, installerVersionString))
            return;
        
        LOGGER.info(" installation has been finished without postinstallation job");
        postInstallWork(fileMgr, currentInstallDir, tomcatSvcCtl, w3SvcCtl);
    }
    
    
    /**
     * uninstall and Install work and post install
     * @param newInstallerPath
     */

    protected static void postInstallWork(FileMgr fileMgr, String currentInstallDir, SvcCtl tomcatSvcCtl, SvcCtl w3SvcCtl) {

        //Post upgrade work ...
        LOGGER.info("Starting post installation work ...");
        boolean bPostWorkResults = true;
       
        //1) delete ROOT, docs and manager
        bPostWorkResults = bPostWorkResults && installStepPostInstallCleanupTomcatDir(fileMgr, currentInstallDir);
 
        //2) Unzip unifiedconfig-realm-assembly.zip and shindig-cache-assembly.zip into the lib folder.
        String unzipLocation = RegistryManager.getIcmInstallDrive() + ":\\icm\\tomcat\\lib";
        String realmZip = RegistryManager.getIcmInstallDrive() + ":\\icm\\install\\unifiedconfig-realm-assembly.zip";
        if (!fileMgr.unzipFile(realmZip, unzipLocation)) {
            LOGGER.warn(String.format("Unzip of '%s' to '%s' failed. Please try unzipping manually", realmZip, unzipLocation));
        }

        String shindigZip = RegistryManager.getIcmInstallDrive() + ":\\icm\\install\\shindig-cache-assembly.zip";
        if(!fileMgr.unzipFile(shindigZip, unzipLocation)) {
            LOGGER.warn(String.format("Unzip of '%s' to '%s' failed. Please try unzipping manually", shindigZip, unzipLocation));
        }

        /// getIcmInstallDrive where ICM has been installed
        
        String icmInstall = RegistryManager.getIcmInstallDrive() + ":\\icm\\install";
        String icmBin = RegistryManager.getIcmInstallDrive() + ":\\icm\\bin";
        ////
        
        //3) copy all the war files from  icm install and icm bin directories to the install Tomcat directory
        bPostWorkResults = bPostWorkResults && InstallStepPostInstallRestoreWarFiles(fileMgr,currentInstallDir,icmInstall,icmBin);

        //4) copy all the required jar files files from icm install and icm bin directories to the install Tomcat directory
        bPostWorkResults = bPostWorkResults && InstallStepPostInstallRestoreJarFiles(fileMgr, icmBin, currentInstallDir);
        
        //5) Create i386 folder in icm\tomcat\bin and copy web.config and isapi_redirect.dll there from icm install and icm bin directories  
        bPostWorkResults = bPostWorkResults && InstallStepPostInstallCopyAJPConnector(fileMgr, currentInstallDir,icmInstall,icmBin);

        //6) copy the catalina.properties, web.xml, and server.xml from icm bin and icm install directories 
        bPostWorkResults = bPostWorkResults && InstallStepPostInstallRestorePropertiesFile(fileMgr, icmBin, currentInstallDir,icmInstall);

        //7) works files updates
        bPostWorkResults = bPostWorkResults && InstallStepPostInstallRestoreTomcatWorkerFiles(fileMgr,currentInstallDir);

        //9) Restore the Tomcat options
        bPostWorkResults = bPostWorkResults && InstallStepPostInstall_IISInstallerWithRuntime(icmBin,currentInstallDir); 
        
        //10) execute this command Execute - C:\WINDOWS\SysWOW64\cscript.exe "C:\icm\bin\install4iis.js"  "C:\icm\tomcat\bin\i386" "C:\icm\tomcat"   
       bPostWorkResults = bPostWorkResults && InstallStepPostInstallRestoreTomcatParameters();

        //11) Apply Permissions on tomcat directory
        bPostWorkResults = bPostWorkResults && InstallStepPostInstallApplyPermissions(fileMgr, currentInstallDir);
       
        //12) remove jakarta ISAPI filter
        bPostWorkResults = bPostWorkResults && InstallStepPostInstallRemoveJakartaISAPIFilter();

        if (bPostWorkResults) {
            LOGGER.info("Tomcat post installation has been done successfully.");
            //Change Tomcat service LogOn to Local System account
            if(!changeTomcatServiceLogOnAccount()){
                LOGGER.info("Start the Tomcat from ICM Service Control once updated the Tomcat service LogOn account by running above command.");
                LOGGER.info(LOG_CHECKING_MSG);
                return;
            }
            //starting W3SVC and Tomcat services
            if (!startServicesStep(w3SvcCtl, tomcatSvcCtl))
                return;
            LOGGER.info(LOG_CHECKING_MSG);
        }
        else {
            LOGGER.info("Tomcat post installation work has been done with error. Please check the log located in ..\\InstallTomcatResults directory");
        }   
    }

    /**
     * Setting Tomcat service Log On Account as LocalSystem
     *
     * @return true if command executed successfully
     */
    private static boolean changeTomcatServiceLogOnAccount() {
        String updateTomcatLogOnAccount = "sc config " + TOMCAT_SERVICENAME + " obj=LocalSystem";
        LOGGER.info("Updating Tomcat service LogOn Account with command : " + updateTomcatLogOnAccount);
        try {
            Process proc = Runtime.getRuntime().exec(updateTomcatLogOnAccount);
            int retCode = proc.waitFor();
            if (retCode == 0) {
                Thread.sleep(1000);
                return true;
            }
            //In the error case
            LOGGER.error("Errors have occurred during the update of tomcat LogOn account with return code:" + retCode);
        } catch (InterruptedException e) {
            // That's okay, we can continue
            return true;
        } catch (Exception e) {
            LOGGER.error("Exception caught during tomcat service config change." , e);
        }
        LOGGER.info("Failed to update tomcat service LogOn config, Please run ' " + updateTomcatLogOnAccount + " ' command from Command Prompt.");
        return false;
    }

    /**
     * Uninstalls the version of tomcat that is currently found on the system.
     * Invokes icm\tomcat\Uninstall.exe for the silent uninstallation.
     * @param currentInstallDir
     * @return
     */
    private static boolean uninstallTomcat(String currentInstallDir) {
        String uninstallCommand = currentInstallDir + "\\Uninstall.exe /S -ServiceName=Tomcat9";
        LOGGER.info("Uninstalling existing Tomcat with command : " + uninstallCommand);
        try {
            Process proc = Runtime.getRuntime().exec(uninstallCommand);
            int retCode = proc.waitFor();
            if (retCode != 0) {
                LOGGER.error("Error occurred during uninstallation: " + retCode);
                return false;
            }
            LOGGER.info("Waiting 40 seconds for tomcat to be fully uninstalled");
            Thread.sleep(40000);
            return true;
        } catch (InterruptedException e) {
            // That's okay, we can continue
            return true;
        } catch (Exception e) {
            LOGGER.warn("An exception was emitted when uninstalling existing Tomcat - " + e.getMessage() , e);
            return false;
        }
    }

    /**
     * Will try to clean tomcat folder ("conf", "logs" will be preserved)
     * @param currentInstallDir
     * @return true if successfully cleaned up tomcat
     */
    private static boolean cleanUpTomcat(String currentInstallDir) {
        LOGGER.info("Trying to cleanup tomcat folder");
        List<String> preservedDirs = Arrays.asList("logs");
        File installDir = new File(currentInstallDir);
        File[] dirEntries = installDir.listFiles();
        if (dirEntries == null) return true;
        boolean success = true;
        for (File dirEntry : dirEntries){
            if (preservedDirs.contains(dirEntry.getName()) && dirEntry.isDirectory())
                LOGGER.info("Preserving " + dirEntry);
            else {
                LOGGER.info("Deleting " + dirEntry);
                try {
                    FileUtils.forceDelete(dirEntry);
                } catch (Exception e) {
                    LOGGER.error("Failed to delete " + dirEntry + " file.", e);
                    success = false;
                }
            }
        }

        return success;
    }

    /**
     * Get the Tomcat installer version from installer file name
     * @param installerFileName Tomcat installer file name
     * @return tomcat version string
     */
    protected static String getInstallerVersion(String installerFileName) {

        int index1 = StringUtils.lastIndexOf(installerFileName, "-");
        int index2 = StringUtils.lastIndexOf(installerFileName, ".");
        if (index1 != -1 && index2 != -1 && index2-index1-1 >0)
            return installerFileName.substring(index1+1, index2);
        return null;
    }

    /**
     * Get Tomcat version numbers from tomcat version string
     *  for an example 7.0.25, first one is major, second one is minor, third one is build number
     * @param tomcatVersion tomcat version string
     * @return array of int which consists major, minor, build
     */
    protected static int[] getTomcatVersionNums(String tomcatVersion) {

        String[] numberStrs = tomcatVersion.split("\\.");
        int[] numbers = new int[numberStrs.length];

        for (int i = 0; i < numberStrs.length; i++) {
            numbers[i] = Integer.parseInt(numberStrs[i]);
        }
        return numbers;
    }

    /**
     * compare two int value
     * @param value1
     * @param value2
     * @return
     */
    protected static int compare(int value1, int value2) {
        if (value1 == value2)
            return 0;
        if (value1 > value2)
            return 1;
        return -1;
    }
    /**
     * Compare two Tomcat version string
     * @param tomcatVersion1  version string of tomcat  (major.minor.build)
     * @param tomcatVersion2  version string of tomcat  (major.minor.build)
     * @return  1 if tomcatVersion1 is higher than tomcatVersion2, 0 if equals, otherwise -1
     */
    protected static int compareTomcatVersions(String tomcatVersion1, String tomcatVersion2) {

        int[] versionNums1 = getTomcatVersionNums(tomcatVersion1);
        int[] versionNums2 = getTomcatVersionNums(tomcatVersion2);
        int results = compare(versionNums1[0], versionNums2[0]);
        if (results != 0)
            return results;
        results = compare(versionNums1[1], versionNums2[1]);
        if (results != 0)
            return results;

        return compare(versionNums1[2], versionNums2[2]);
    }

    public static String handleEscapeCharacter(String line) {
        return line.replace("\\", "\\\\");
    }

    /**
     * Update tomcatConfig.ini with latest CCE_JAVA_HOME
     * @param tomcatConfigFile
     * @param cceJavaHome
     * @return
     */
    public static void updateTomcatConfigFile(File tomcatConfigFile, String cceJavaHome) {
        List<String> lines = new ArrayList<String>();
        String line = null;
        FileWriter fileWriter = null;
        try {
            FileReader fileReader = new FileReader(tomcatConfigFile);
            BufferedReader bufferedReader = new BufferedReader(fileReader);
            while ((line = bufferedReader.readLine()) != null) {
                if (line.contains("JavaHome=")) {
                    line = handleEscapeCharacter(line);
                    line = line.replaceAll("(?m)^\\s*JavaHome=\\S.*$", handleEscapeCharacter(cceJavaHome));
                }
                lines.add(line);
            }
            fileReader.close();
            bufferedReader.close();

            fileWriter = new FileWriter(tomcatConfigFile);
            BufferedWriter out = new BufferedWriter(fileWriter);
            for(String s : lines)
                out.write(s + '\n');
            out.flush();
            out.close();
        } catch (IOException e) {
            LOGGER.error("Failed to update tomcatConfig file. Please check Tomcat installer and try again.");
        } finally {
            IOUtils.closeQuietly(fileWriter);
        }
    }

    /**
     * perform the Tomcat upgrade from current version to the version in installer directory
     * @param installerPath  Tomcat installer full path name
     * @param currentInstallDir  current Tomcat Install directory
     * @return true if succeed, false if fail
     */
    protected static boolean performTomcatInstall(String installerPath, String currentInstallDir) {

        String icmInstallDrive = RegistryManager.getIcmInstallDrive();

        final String icmConfiglocation = icmInstallDrive + ":\\icm\\install\\tomcatConfig.ini";

        // Added as a fix for CSCvx27900, to ensure tomcat upgrade happens with OpenJDK.
        // tomcatConfig.ini file having OpenJDK jre will be created in \icm\install directory if not present
        // and will be used while tomcat upgrade
        // if present already JavaHome will be updated with latest CCE_JAVA_HOME env variable
        try {
            // Get the file
            File fileName = new File(icmConfiglocation);
            String cceJavaHome = "JavaHome=" + System.getenv("CCE_JAVA_HOME");

            // Create new file if it does not exist
            if (!fileName.exists()) {
                FileWriter fileWriter = null;
                try {
                    if (fileName.createNewFile()) {
                        LOGGER.info("Creating new tomcatConfig.ini file.");
                        fileWriter = new FileWriter(icmConfiglocation);
                        fileWriter.write(cceJavaHome);
                    }
                }
                catch (IOException e) {
                    LOGGER.error("IOException caught during upgrade. " + e);
                    LOGGER.info("Failed to create tomcatConfig file. Please check Tomcat installer and try again.");
                }
                catch (Throwable e) {
                    LOGGER.error("Exception caught during tomcatConfig creation. " + e);
                }
                finally {
                    IOUtils.closeQuietly(fileWriter);
                }
            } else {
                // update JavaHome with latest CCE_JAVA_HOME env variable
                LOGGER.info("tomcatConfig.ini file exists, updating");
                updateTomcatConfigFile(fileName, cceJavaHome);
            }
        }
        catch (Exception e) {
            LOGGER.error("Exception caught during upgrade. " + e);
        }

        String installTomcatCmd = installerPath + " /C="+ icmConfiglocation + " /S /D=" + currentInstallDir;
        try {
            Process proc = Runtime.getRuntime().exec(installTomcatCmd);
            int retCode = proc.waitFor();
            //In the error case
            if (retCode != 0) {
                LOGGER.error("Errors have occurred during the installation with return code:" + retCode);
                return false;
            }
            LOGGER.info("Waiting 30 seconds for tomcat to be fully installed");
            Thread.sleep(30000);
            updateServiceNameAndStartupType(currentInstallDir);
            return true;
        }
        catch (IOException e) {
            //no need to revert in this case
            LOGGER.error("IOException caught during upgrade. " , e);
            LOGGER.info("Failed to run the Tomcat installer. Please check Tomcat installer and try again.");
        }
        catch (Exception e) {
            LOGGER.error("Exception caught during upgrade. " , e);
        }
        return false;
    }

    private static void updateServiceNameAndStartupType(String currentInstallDir) throws IOException, InterruptedException {
        String command = currentInstallDir +"\\bin\\tomcat9 //US//"+TOMCAT_SERVICENAME+ " --Startup="+SERVICE_STARTUP_TYPE_AUTOMATIC + " --DisplayName="+"\""+TOMACAT_DISPLAY_NAME+"\"";
        Process proc = Runtime.getRuntime().exec(command);
        int retCode = proc.waitFor();
        if (retCode != 0) {
            LOGGER.error("Errors have occurred during the updating with return code:" + retCode);
        }
    }

    /**
     * Get the Tomcat Install drive from the Tomcat install path
     * @param currentTomcatInstallLocation the Tomcat install path
     * @return drive letter with colon, for an example C:
     */
    protected static String getTomcatInstallDrive(String currentTomcatInstallLocation) {
        return StringUtils.substring(currentTomcatInstallLocation, 0, 2);
    }
    
    protected static String getCurrentICMInstallDrive() {

        return Registry.getStringValue(RegistryRoot.IcmInstallDrive.getRoot(),RegistryRoot.IcmInstallDrive.getKey(), "InstallDrive");
    }


    /**
     * Confirm with the user for upgrade operation before proceed
     * @param console
     * @return
     */
    private static boolean installStepConfirmation(SystemConsole console) {
        String proceedOption = console.readLine(ISTALL_PROCEED_PROMPT).toLowerCase(Locale.getDefault());
        LOGGER.debug(ISTALL_PROCEED_PROMPT + proceedOption);
        boolean bProceed = "-noconfirm".equals(proceedOption) || "yes".equals(proceedOption) || "y".equals(proceedOption);
        return bProceed;
    }

    /**
     * Step to start W3Svc and Tomcat services
     * @param w3SvcCtl
     * @param tomcatSvcCtl
     */
    private static boolean startServicesStep(SvcCtl w3SvcCtl, SvcCtl tomcatSvcCtl) {
        boolean success = true;
        //Start W3SVC service
        if (!w3SvcCtl.startServiceAndCheckState()) {
            LOGGER.info("Failed to start W3SVC service after " + (MAX_ATTEMPTS * INTERVAL_TO_CHECK_STATE / 1000) + " seconds");
            LOGGER.info("Please try to start the W3SVC service from ICM Service Control.");
            success =  false;
        }
        else
            LOGGER.info(W3SVC_DESCRIPTIVE_NAME + " has been started.");

        //Start Tomcat service
        if (!tomcatSvcCtl.startServiceAndCheckState()) {
            LOGGER.info("Failed to start Tomcat after " + (MAX_ATTEMPTS * INTERVAL_TO_CHECK_STATE / 1000) + " seconds");
            LOGGER.info("Please try to start the Tomcat from ICM Service Control.");
            success = false;
        }
        else
            LOGGER.info(LOG_TOMCAT_STARTED_MSG);
        return success;
    }

    /**
     * Step to stop Tomcat and W3Svc services
     * @param tomcatSvcCtl tomcat service control object
     * @param w3SvcCtl  w3svc service control object
     * @return true if successful, otherwise false
     */
    private static boolean stopServiceStep(SvcCtl tomcatSvcCtl, SvcCtl w3SvcCtl) {
        //Stop Tomcat service  and W3SVC service,
        LOGGER.info("Stopping Tomcat service ...");
        if (!tomcatSvcCtl.stopServiceAndCheckState()) {
            LOGGER.info("Failed to stop Tomcat after " + (MAX_ATTEMPTS * INTERVAL_TO_CHECK_STATE / 1000) + " seconds");
            return false;
        }
        LOGGER.info(LOG_TOMCAT_STOPPED_MSG);

        //Stop W3SVC service
        LOGGER.info("Stopping " + W3SVC_DESCRIPTIVE_NAME + " ...");
        if (!w3SvcCtl.stopServiceAndCheckState()) {
            LOGGER.info("Failed to stop " + W3SVC_DESCRIPTIVE_NAME + " after " + (MAX_ATTEMPTS * INTERVAL_TO_CHECK_STATE / 1000) + " seconds");
            return false;
        }
        LOGGER.info(W3SVC_DESCRIPTIVE_NAME + " has been stopped.");
        return true;
    }


    /**
     * Step in upgrade operation to save the current Tomcat options
     * @return
     */
    private static String[] installStepSaveTomcatOptions() {
        LOGGER.info("Saving the options for the current Tomcat.");
        String[] tomcatOptions = RegistryManager.retrieveTomcatOptions();
        if (tomcatOptions != null) {
            LOGGER.debug("The current Tomcat options:\n");
            for (String option : tomcatOptions) {
                LOGGER.debug(option + "\n");
            }
            LOGGER.info("The current Tomcat options have been saved.");
        }
        else {
            LOGGER.info("Failed to save the current Tomcat options.");
            LOGGER.info(LOG_CHECKING_MSG);
        }
        return tomcatOptions;
    }

    /**
     * Step in upgrade operation to run the Tomcat silent installer
     * @param newInstallerPath
     * @param currentInstallDir
     * @param installerVersionString
     * @return true if success, otherwise false
     */
    private static boolean installStepRunSilentInstaller(String newInstallerPath, String currentInstallDir, String installerVersionString ) {
        //silent run new installer
        LOGGER.info("Running Tomcat installer ...");
        LOGGER.info ("Installer path: " + newInstallerPath);
        if (!performTomcatInstall(newInstallerPath, currentInstallDir)) {
            LOGGER.info(LOG_CHECKING_MSG);
            return false;
        }
        LOGGER.info("Tomcat installer has been run successfully.");

        String tomcatVersionAfterUpgrade = RegistryManager.getCurrentInstallVersion();
        if (tomcatVersionAfterUpgrade.compareTo(installerVersionString) == 0) {
            LOGGER.debug("The value of Tomcat registry version has been updated correctly. ");
        }
        return true;
    }

    /**
     * Post Install step to clean up Tomcat directory
     * @param fileMgr
     * @param currentInstallDir
     * @return true if success, otherwise false
     */
    private static boolean installStepPostInstallCleanupTomcatDir(FileMgr fileMgr, String currentInstallDir ) {
        boolean bPostWorkResults = true;
        //delete ROOT, docs and manager
        LOGGER.info("Doing cleanup on the Tomcat directory " + currentInstallDir);
        String cleanupPath= currentInstallDir + TOMCAT_WEBAPPS_PATH;

        String rootPath = cleanupPath + TOMCAT_SERVER_ROOT_PATH;
        if (fileMgr.cleanupDirecotry(rootPath))
            LOGGER.debug(rootPath + " directory has been deleted.");
        else {
            LOGGER.info("Failed to delete " + rootPath + " directory.");
            bPostWorkResults = false;
        }
        String managerPath = cleanupPath + TOMCAT_SERVER_MANAGER_PATH;
        if (fileMgr.cleanupDirecotry(managerPath))
            LOGGER.debug(managerPath + " directory has been deleted.");
        else {
            LOGGER.info("Failed to delete " + managerPath + " directory.");
            bPostWorkResults = false;
        }
        String docsPath = cleanupPath + TOMCAT_SERVER_DOCS_PATH;
        if (fileMgr.cleanupDirecotry(docsPath))
            LOGGER.debug(docsPath + " directory has been deleted.");
        else {
            LOGGER.info("Failed to delete " + docsPath + " directory.");
            bPostWorkResults = false;
        }
        LOGGER.info("The cleanup of the Tomcat directory is done.");
        return bPostWorkResults;
    }


    /**
     * Copy the war files from the webapps folder of the tomcat install folders to webapps folder of the tomcat installation directory.
     * @param tomcatBackupDir
     * @param currentInstallDir
     * @return
     */
    private static boolean InstallStepPostInstallRestoreWarFiles(FileMgr fileMgr,String currentInstallDir, String icmInstall, String icmBin){
        
    	boolean bPostWorkResults = true;
    	
    	  String currentWebAppsDir = currentInstallDir + TOMCAT_WEBAPPS_PATH;
    	
    	 
           File RootFile = new File(currentWebAppsDir+"ROOT.war");

           
   	    try {
   	    	   File shindigFile = new File(currentWebAppsDir+"\\ccbu-common-shindig-server.war");
               FileUtils.copyDirectory(new File(icmBin), new File(icmInstall), new WildcardFileFilter("*.war"), true);
               FileUtils.copyDirectory(new File(icmInstall), new File(currentWebAppsDir), new WildcardFileFilter("*.war"), true);
              
             	LOGGER.info("Restoring ROOT.war from ccbu-common-shindig-server.war" );
                String sourceFile = icmInstall +"\\ccbu-common-shindig-server.war", destFile = currentWebAppsDir+"\\ROOT.war";

                if (fileMgr.copyTomcatFile(sourceFile, destFile)){
                    LOGGER.debug("The file " + sourceFile + " has been copied over to " + destFile);
                    LOGGER.info(" ROOT.war has been restored");
                }
                else {
                    LOGGER.info("Failed to copy " + sourceFile + " to " + destFile);
                    bPostWorkResults = false;
                }
         
               
               if (shindigFile.exists())
               {
               	LOGGER.info("the shindigFile is there , deleting it..");
               	
                FileUtils.forceDelete(shindigFile);
               }
               
           }
           catch (IOException e){
               LOGGER.warn(String.format("Unable to copy the war files from '%s' to '%s'. Please perform the copy manually", icmInstall, currentWebAppsDir));
               LOGGER.warn(e);
               bPostWorkResults = false;
           
           }
   	    
   	    return bPostWorkResults;

    	
    }


    /**
     * Post upgrade step to restore Tomcat options, JvmMx, JvmMs and failure actions
     * @param tomcatOptions
     * @return true if success, otherwise false
     */
    private static boolean InstallStepPostInstallRestoreTomcatParameters() {
    	
    	boolean bPostWorkResults = true;
    	String [] tomcatOptions = installStepSaveTomcatOptions();
    	
    	String[] options = {
    	            "-DICM_ROOT=C:\\icm",
    	            "-Dcom.sun.management.jmxremote.ssl.need.client.auth=false",
    	            "-Dcom.sun.management.jmxremote.authenticate=false",
    	            "-Dcom.sun.management.jmxremote.ssl=false",
    	            "-XX:MaxPermSize=128m",
    	            "-Dfile.encoding=UTF8",
    	            "-Dclient.encoding.override=UTF-8"
    	     };
    	
    	  String[] combined = new String[options.length + (tomcatOptions.length-1)]; /// this to add the options properly
    	
    	
    	  String keyPath = "HKEY_CURRENT_USER\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Run";
          String valueName = "ApacheTomcatMonitor9.0_Tomcat9";

          // Construct the delete command
          String command = "reg delete \"" + keyPath + "\" /v " + valueName + " /f";
    	
    	

    	System.arraycopy(tomcatOptions, 0, combined, 0, tomcatOptions.length-1);
    	System.arraycopy(options, 0, combined, tomcatOptions.length-1, options.length);
    

    	// Print the combined array
    	for (String opt : combined) {
    	    System.out.println(opt);
    	}
    	
    	LOGGER.info("Restoring the setting for Tomcat options.");
        if (RegistryManager.setTomcatOptions(combined))
            LOGGER.info("The setting for Tomcat options has been restored.");
        else {
            LOGGER.info("Failed to restore Tomcat options.");
            bPostWorkResults= false;
        }

        LOGGER.info("Restoring the Tomcat JvmMx and JvmMs.");
        if (RegistryManager.setJVMMemory(0x00000200))
            LOGGER.info("The JvmMx and JvmMs for Tomcat has been restored.");
        else {
            LOGGER.info("Failed to restore Tomcat JvmMx and JvmMs.");
            bPostWorkResults= false;
        }

        LOGGER.info("Restoring the Tomcat failure actions.");
        
        
        /// delete the options
        
        try {
            Process process = Runtime.getRuntime().exec(command);

            // Capture output
            BufferedReader stdInput = new BufferedReader(new InputStreamReader(process.getInputStream()));
            BufferedReader stdError = new BufferedReader(new InputStreamReader(process.getErrorStream()));

            String line;
            System.out.println("Output:");
            while ((line = stdInput.readLine()) != null) {
                System.out.println(line);
            }

            System.out.println("Errors (if any):");
            while ((line = stdError.readLine()) != null) {
                System.err.println(line);
                bPostWorkResults= false;
            }

            int exitCode = process.waitFor();
            System.out.println("Registry deletion completed with exit code: " + exitCode);

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            bPostWorkResults= false;
        }
           
        return bPostWorkResults;
    
    }

    /**
     * create icm\tomcat\bin\i386 and copy web.config and isapi_redirect.dll there from icm install and icm bin directories  
     * @return
     */
    private static boolean  InstallStepPostInstallCopyAJPConnector(FileMgr fileMgr, String currentInstallDir, String icmInstall, String icmBin) {
		
		/// first we have to create the directory...
		 
		  LOGGER.info("Creating i386 directory...");
		  
		  String i386Dir =currentInstallDir + "\\bin\\i386";
		  
		try {
			
			Path i386Path = Paths.get(currentInstallDir);
			if (!Files.exists(i386Path)) {
			    Files.createDirectories(i386Path);
			}	
		}
		
		catch(Exception e) {
            LOGGER.error("Exception caught during creating i386 Directory " , e);
        }
		
		String sourceFile = icmInstall + WEBCONFIG, destFile = i386Dir +WEBCONFIG;

        

        if (fileMgr.copyTomcatFile(sourceFile, destFile)){
            LOGGER.debug("The file " + sourceFile + " has been copied over to " + destFile);
            LOGGER.info("icm-websetup-shared.jar has been restored");
        }
        else {
            LOGGER.info("Failed to copy " + sourceFile + " to " + destFile);
             return false;
        }
        
        
         sourceFile = icmBin + ISAPI;
         destFile = i386Dir +ISAPI;

        

        if (fileMgr.copyTomcatFile(sourceFile, destFile)){
            LOGGER.debug("The file " + sourceFile + " has been copied over to " + destFile);
            LOGGER.info("icm-websetup-shared.jar has been restored");
        }
        else {
            LOGGER.info("Failed to copy " + sourceFile + " to " + destFile);
             return false;}
             
        return true;  
        
    }
    

    /**
     * Post install step apply permissions on tomcat directory and files recursively
     * @param fileMgr
     * @param currentInstallDir
     * @return true if success, otherwise false
     */
    private static boolean InstallStepPostInstallApplyPermissions(FileMgr fileMgr, String currentInstallDir) {
        boolean bPostWorkResults = false;
        String parentDir = currentInstallDir + "\\..";
        LOGGER.info("Applying Permissions on " + currentInstallDir);

        // Get parent directory permissions and apply those on currentInstallDir recursively
        List<AclEntry> aclEntriesDir = fileMgr.getACLEntriesList(parentDir);
        bPostWorkResults = fileMgr.applyACLEntries(currentInstallDir, Files::isDirectory, aclEntriesDir);
        if (bPostWorkResults) {
            // Apply file permissions
            List<AclEntry> aclEntriesFile = fileMgr.getACLEntriesListofFirstFile(parentDir);
            bPostWorkResults = fileMgr.applyACLEntries(currentInstallDir, Files::isRegularFile, aclEntriesFile);
        }

        if (bPostWorkResults) {
            LOGGER.info("Permissions successfully applied on "+ currentInstallDir);
        } else {
            LOGGER.info("Failed to apply Permissions");
        }
        return bPostWorkResults;
    }
    
    private static boolean InstallStepPostInstallRestoreJarFiles(FileMgr fileMgr, String icmBin, String currentInstallDir){
        boolean bPostWorkResults = true;
        LOGGER.info("Restoring the icm-websetup-shared.jar from the backup directory " + icmBin );
        String sourceFile = icmBin + "\\icm-websetup-shared.jar", destFile = currentInstallDir + ICM_WEBSETUP_SHARED_JAR_PATH;

        if (fileMgr.copyTomcatFile(sourceFile, destFile)){
            LOGGER.debug("The file " + sourceFile + " has been copied over to " + destFile);
            LOGGER.info("icm-websetup-shared.jar has been restored");
        }
        else {
            LOGGER.info("Failed to copy " + sourceFile + " to " + destFile);
            bPostWorkResults = false;
        }
        LOGGER.info("Restoring the jntservices.jar from the backup directory " + icmBin );
        sourceFile = icmBin + "\\jntservices.jar";
        destFile = currentInstallDir + JNTSERVICES_JAR_PATH;

        if (fileMgr.copyTomcatFile(sourceFile, destFile)){
            LOGGER.debug("The file " + sourceFile + " has been copied over to " + destFile);
            LOGGER.info("jntservices.jar has been restored");
        }
        else {
            LOGGER.info("Failed to copy " + sourceFile + " to " + destFile);
            bPostWorkResults = false;
        }
        LOGGER.info("Restoring the catalina-jmx-remote.jar from the backup directory " + icmBin );
        
        sourceFile = icmBin + "\\catalina-jmx-remote.jar";
        destFile = currentInstallDir + CATALINA_JMX_REMOTE_JAR_PATH;

        if (fileMgr.copyTomcatFile(sourceFile, destFile)){
            LOGGER.debug("The file " + sourceFile + " has been copied over to " + destFile);
            LOGGER.info("catalina-jmx-remote.jar has been restored");
        }
        else {
            LOGGER.info("Failed to copy " + sourceFile + " to " + destFile);
            bPostWorkResults = false;
        }
        LOGGER.info("Restoring the registry.jar from the backup directory " + icmBin );
        
        sourceFile = icmBin + "\\registry.jar";
        destFile = currentInstallDir + ICM_REGISTRY_JAR_PATH;

        if (fileMgr.copyTomcatFile(sourceFile, destFile)){
            LOGGER.debug("The file " + sourceFile + " has been copied over to " + destFile);
            LOGGER.info("registry.jar has been restored");
        }
        else {
            LOGGER.info("Failed to copy " + sourceFile + " to " + destFile);
            bPostWorkResults = false;
        }
        return bPostWorkResults;
        
    }

    private static boolean InstallStepPostInstallRestorePropertiesFile(FileMgr fileMgr, String icmBin, String currentInstallDir, String icmInstall){
        boolean bPostWorkResults = true;
        LOGGER.info("Restoring the web.xml from the directory " + icmBin );
        String sourceFile = icmBin + "\\web.xml", destFile = currentInstallDir + WEBXML;

        if (fileMgr.copyTomcatFile(sourceFile, destFile)){
            LOGGER.debug("The file " + sourceFile + " has been copied over to " + destFile);
            LOGGER.info("web.xml has been restored");
        }
        else {
            LOGGER.info("Failed to copy " + sourceFile + " to " + destFile);
            bPostWorkResults = false;
        }
        
        LOGGER.info("Restoring the server.xml from the directory " + icmBin );  
        sourceFile = icmBin + IISCUSTOM;
        destFile = currentInstallDir+TOMCAT_SERVER_XML_PATH;


       if (fileMgr.copyTomcatFile(sourceFile, destFile)){
           LOGGER.debug("The file " + sourceFile + " has been copied over to " + destFile);
           LOGGER.info("server.xml has been restored");}
         
       else {
           LOGGER.info("Failed to copy " + sourceFile + " to " + destFile);
           bPostWorkResults = false;}
       
       LOGGER.info("Restoring the catalina.properties from the directory " + icmBin );  
       sourceFile = icmBin + "\\catalina.properties";
       destFile = currentInstallDir+TOMCAT_CATALINA_PROPERTIES_PATH;


      if (fileMgr.copyTomcatFile(sourceFile, destFile)){
          LOGGER.debug("The file " + sourceFile + " has been copied over to " + destFile);
          LOGGER.info("server.xml has been restored");}
        
      else {
          LOGGER.info("Failed to copy " + sourceFile + " to " + destFile);
          bPostWorkResults = false;} 
        
        return bPostWorkResults;
        
    }
    
    private static boolean InstallStepPostInstallRestoreTomcatWorkerFiles(FileMgr fileMgr,String currentInstallDir){
       
    	boolean bPostWorkResults = true;
        
    	// Define file paths
        Path uriMapPath = Paths.get(currentInstallDir+"\\conf\\", uriMapFile);
        Path workersPath = Paths.get(currentInstallDir+"\\conf\\", workersFile);
        
        String ls = System.lineSeparator(); 

        // Content for uriworkermap.properties
        String workersContent =
        	    "worker.list=worker.tomcat" + ls +ls+
        	    "# Define a worker to redirect requests to Tomcat"+ls+
        	    "worker.tomcat.type=ajp13" + ls +
        	    "worker.tomcat.host=127.0.0.1" + ls +
        	    "worker.tomcat.port=8009" + ls;

        	String uriMapContent =
        	    "# Add URI mappings for Tomcat" + ls +
        	    "/setup=worker.tomcat" + ls +
        	    "/setup/*=worker.tomcat" + ls +ls+
        	    "/unifiedconfig=worker.tomcat" + ls +
        	    "/unifiedconfig/*=worker.tomcat" + ls +ls+
        	    "/cceadmin=worker.tomcat" + ls +
        	    "/cceadmin/*=worker.tomcat" + ls +ls+
        	    "/cceadminnew=worker.tomcat" + ls +
        	    "/cceadminnew/*=worker.tomcat" + ls +ls+
        	    "/gadgets=worker.tomcat" + ls +
        	    "/gadgets/*=worker.tomcat" + ls;

        try {
            Files.createDirectories(Paths.get(currentInstallDir+"\\conf\\"));

            if (!Files.exists(uriMapPath)) {
                Files.write(uriMapPath, uriMapContent.getBytes(StandardCharsets.UTF_8));
                System.out.println("Created: " + uriMapPath);
            } else {
                System.out.println("Skipped (already exists): " + uriMapPath);
            }

            if (!Files.exists(workersPath)) {
                Files.write(workersPath, workersContent.getBytes(StandardCharsets.UTF_8));
                System.out.println("Created: " + workersPath);
            } else {
                System.out.println("Skipped (already exists): " + workersPath);
            }
        } catch (IOException e) {
            e.printStackTrace();
            bPostWorkResults = false;}   
        
        
        return bPostWorkResults;
    }
    
    
    private static boolean InstallStepPostInstall_IISInstallerWithRuntime(String icmBin, String currentInstallDir) {
    	boolean bPostWorkResults = true;

    	String command = "C:\\WINDOWS\\SysWOW64\\cscript.exe \"" 
    		    + icmBin + "\\install4iis.js\" \"" 
    		    + currentInstallDir + "\\bin\\i386\" \"" 
    		    + currentInstallDir + "\"";

        try {
            // Execute the command
            Process process = Runtime.getRuntime().exec(command);

            // Read standard output
            BufferedReader stdInput = new BufferedReader(new InputStreamReader(process.getInputStream()));
            // Read error output
            BufferedReader stdError = new BufferedReader(new InputStreamReader(process.getErrorStream()));

            String s;
            System.out.println("Output:");
            while ((s = stdInput.readLine()) != null) {
                System.out.println(s);
            }

            System.out.println("Errors (if any):");
            while ((s = stdError.readLine()) != null) {
                System.err.println(s);
            }

            // Wait for the command to finish
            int exitCode = process.waitFor();
            System.out.println("Process exited with code: " + exitCode);

        } catch (Exception e) {
            e.printStackTrace();
            bPostWorkResults=false;
        }
     	
     return bPostWorkResults;
    	
    }
    
    
    private static boolean  InstallStepPostInstallRemoveJakartaISAPIFilter (){
    	boolean bPostWorkResults = true;
    	 try {
             //String configPath = "C:\\Windows\\System32\\inetsrv\\config\\applicationHost.config";  /// for 64 bit java
             
             // Use Sysnative if using 32-bit Java on 64-bit Windows 

    		 String configPath ="";
    		 String jvm = System.getProperty("sun.arch.data.model");
    		 
    		   if ("32".equals(jvm)) {
    			     LOGGER.info("Java is 32-bit");
    	              configPath = System.getenv("windir") + "\\Sysnative\\inetsrv\\config\\applicationHost.config";

    	        } else if ("64".equals(jvm)) {
    	        	LOGGER.info("Java is 64-bit");
    	        	 configPath = System.getenv("windir") + "\\System32\\inetsrv\\config\\applicationHost.config";
    	        } else {
    	        	LOGGER.info("Unable to determine JVM bitness");
    	        }

            
             File xmlFile = new File(configPath);
             if (!xmlFile.exists()) {
                 System.err.println("Config file not found: " + configPath);
                 bPostWorkResults=false;
             }

             DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
             factory.setNamespaceAware(false);
             DocumentBuilder builder = factory.newDocumentBuilder();
             Document doc = builder.parse(xmlFile);
             doc.getDocumentElement().normalize();

             boolean found = false;

             // 1. Remove from global isapiFilters
             NodeList globalFilters = doc.getElementsByTagName("isapiFilters");
             for (int i = 0; i < globalFilters.getLength(); i++) {
                 Element filters = (Element) globalFilters.item(i);
                 NodeList filterList = filters.getElementsByTagName("filter");
                 for (int j = 0; j < filterList.getLength(); j++) {
                     Element filter = (Element) filterList.item(j);
                     if ("Jakarta".equalsIgnoreCase(filter.getAttribute("name"))) {
                         filters.removeChild(filter);
                         found = true;
                         System.out.println("Removed global Jakarta filter.");
                         break;
                     }
                 }
             }

             // 2. Remove from site-level <location> nodes
             NodeList locationNodes = doc.getElementsByTagName("location");
             for (int i = 0; i < locationNodes.getLength(); i++) {
                 Element location = (Element) locationNodes.item(i);
                 NodeList systemWebServer = location.getElementsByTagName("system.webServer");

                 for (int j = 0; j < systemWebServer.getLength(); j++) {
                     Element sysWebServer = (Element) systemWebServer.item(j);
                     NodeList isapiFilters = sysWebServer.getElementsByTagName("isapiFilters");

                     for (int k = 0; k < isapiFilters.getLength(); k++) {
                         Element filters = (Element) isapiFilters.item(k);
                         NodeList filterList = filters.getElementsByTagName("filter");

                         for (int m = 0; m < filterList.getLength(); m++) {
                             Element filter = (Element) filterList.item(m);
                             if ("Jakarta".equalsIgnoreCase(filter.getAttribute("name"))) {
                                 filters.removeChild(filter);
                                 found = true;
                                 System.out.println("Removed Jakarta filter from site: " +
                                     location.getAttribute("path"));
                                 break;
                             }
                         }
                     }
                 }
             }

             if (found) {
                 // Save updated config
                 Transformer transformer = TransformerFactory.newInstance().newTransformer();
                 transformer.setOutputProperty(OutputKeys.INDENT, "yes");
                 transformer.transform(new DOMSource(doc), new StreamResult(xmlFile));
                 System.out.println("Updated applicationHost.config successfully.");
             } else {
                 System.out.println("Jakarta ISAPI filter not found at any level.");
                 bPostWorkResults=false;;
             }

         } catch (Exception e) {
             e.printStackTrace();
             bPostWorkResults=false;
         }
    	
    	
    return bPostWorkResults;
    	
    }
    
   
}