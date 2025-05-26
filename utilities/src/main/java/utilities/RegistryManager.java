package utilities;

import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.WinReg;

import java.util.Arrays;
import java.util.List;

public class RegistryManager {

    public static final String TOMCAT_KEY = "SOFTWARE\\WOW6432Node\\Apache Software Foundation";
    public static final String TOMCAT_VERSION = "Tomcat\\9.0\\Tomcat9";

    public static final String TOMCAT_OPTIONS = "SOFTWARE\\Wow6432Node\\Apache Software Foundation\\Procrun 2.0\\Tomcat9\\Parameters\\Java";
    public static final String ICM_INSTALL_DRIVE = "SOFTWARE\\Cisco Systems, Inc.\\ICM\\SystemSettings";
    public static final String TOMCAT_AUTO_RESTART_KEY = "SYSTEM\\CurrentControlSet\\Services\\Tomcat9";

    //Tomcat9 Options for PCCE, currently used only in unit test
    protected static final List<String> DEFAULT_TOMCAT_OPTIONS_VALUES = Arrays.asList(
                                                               "-DICM_ROOT=C:\\icm",
                                                               "-Dcom.sun.management.jmxremote.ssl.need.client.auth=false",
                                                               "-Dcom.sun.management.jmxremote.ssl=false",
                                                               "-Dcom.sun.management.jmxremote.authenticate=false",
                                                               "-Dfile.encoding=UTF8",
                                                               "-Dclient.encoding.override=UTF-8",
                                                               "-XX:MaxPermSize=128m");

    //used in unit testing only
    protected  static String[] getPCCEDefaultTomcatOptionsValues() {

        String[] defaultTomcatOptions  = new String[DEFAULT_TOMCAT_OPTIONS_VALUES.size()];
        return DEFAULT_TOMCAT_OPTIONS_VALUES.toArray(defaultTomcatOptions);
    }

    /**
     * Get the current install Tomcat version  by querying the registry key
     * @return empty string when error occurs
     */
    public static String getCurrentInstallVersion() {

        return Registry.getStringValue(RegistryRoot.TomcatROOT.getRoot(), RegistryRoot.TomcatVersion.getKey(), "Version");
    }

    /**
     * Used for revert process
     * @param tomcatVersion
     * @return
     */
    public static boolean updateTomcatInstallVersion(String tomcatVersion ) {
        return Registry.setStringValue(RegistryRoot.TomcatROOT.getRoot(), RegistryRoot.TomcatVersion.getKey(), "Version", tomcatVersion);
    }

    /**
     * Get the current install Tomcat directory
     * @return empty string when error occurs
     */
    public static String getCurrentInstallDirectory() {

        return Registry.getStringValue(RegistryRoot.TomcatROOT.getRoot(), RegistryRoot.TomcatVersion.getKey(), "InstallPath");
    }

    /**
     * Get the current install Tomcat options
     * @return null when error occurs
     */
    public static String[] retrieveTomcatOptions() {

        return Registry.getMultiStringValue(RegistryRoot.TomcatROOT.getRoot(), RegistryRoot.TomcatOptions.getKey(), "Options");
    }

    /**
     * Set the Tomcat options
     * @param tomcatOptions tomcat options
     * @return false when error occurs
     */
    public static boolean setTomcatOptions(String[] tomcatOptions) {

        return Registry.setMultiStringValue(RegistryRoot.TomcatROOT.getRoot(), RegistryRoot.TomcatOptions.getKey(), "Options", tomcatOptions);
    }

    /**
     * Retrieve ICM install drive
     * @return empty when error occurs
     */
    public static String getIcmInstallDrive() {

        return Registry.getStringValue(RegistryRoot.TomcatROOT.getRoot(), RegistryRoot.IcmInstallDrive.getKey(), "InstallDrive");
    }

    public static boolean setTomcatFailureActions(byte[] tomcatAutoRestartConfigUpdated) {
        return Registry.registrySetBinaryValue(WinReg.HKEY_LOCAL_MACHINE, TOMCAT_AUTO_RESTART_KEY, "FailureActions", tomcatAutoRestartConfigUpdated);
    }

    public static boolean setJVMMemory(int value) {
        if(Registry.setIntValue(WinReg.HKEY_LOCAL_MACHINE, TOMCAT_OPTIONS, "JvmMx", value)){
            return Registry.setIntValue(WinReg.HKEY_LOCAL_MACHINE, TOMCAT_OPTIONS, "JvmMs", value);
        }
        return false;
    }
}