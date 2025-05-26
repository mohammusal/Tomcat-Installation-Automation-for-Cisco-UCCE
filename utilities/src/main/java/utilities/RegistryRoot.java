package utilities;

import com.sun.jna.platform.win32.WinReg;

/**
 * Tomcat registry key path
 */
public enum RegistryRoot {

    /**
     * Registry key for <i>HKLM</i>.
     */
    LocalMachine(WinReg.HKEY_LOCAL_MACHINE),

    /**
     * Registry key for <i>HKLM\SOFTWARE\Apache Software Foundation</i>.
     */
    TomcatROOT(WinReg.HKEY_LOCAL_MACHINE) {
        @Override
        public String getKey() {
            return RegistryManager.TOMCAT_KEY;
        }
    },

    /**
     * Registry key for <i>HKLM\SOFTWARE\Apache Software Foundation\Tomcat\9.0\Tomcat9</i>.
     */
    TomcatVersion(WinReg.HKEY_LOCAL_MACHINE) {
        @Override
        public String getKey() {
            return RegistryManager.TOMCAT_KEY + "\\" + RegistryManager.TOMCAT_VERSION;
        }
    },

    /**
     * Registry key for <i>HKLM\SOFTWARE\Cisco Systems, Inc.\ICM\SystemSettings</i>.
     */
    IcmInstallDrive(WinReg.HKEY_LOCAL_MACHINE) {
        @Override
        public String getKey() {
            return RegistryManager.ICM_INSTALL_DRIVE;
        }
    },

    /**
     * Registry key for <i>HKLM\SOFTWARE\Apache Software Foundation\Procrun2.0\Tomcat9\Parameters\Java\Options</i>.
     */
    TomcatOptions(WinReg.HKEY_LOCAL_MACHINE) {
        @Override
        public String getKey() {
            return RegistryManager.TOMCAT_OPTIONS;
        }
    };

    private RegistryRoot(WinReg.HKEY root) {
        this.root = root;
    }

    private WinReg.HKEY root;

    /**
     * @return The registry root.
     */
    public WinReg.HKEY getRoot() {
        return root;
    }

    /**
     * @return The base registry key.
     */
    public String getKey() {
        return null;
    }
}