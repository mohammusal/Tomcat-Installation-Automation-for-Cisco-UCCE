
public class test {

	public static void main(String[] args) {
		
		/*To automate these steps:
		
		1. Copy the Apache tomcat installer to c:\temp
		2. Open command prompt with administrative rights (Run as Administrator)
		3. Say if the tomcat installer is, apache-tomcat-9.0.39.exe
		a. Execute the command, "C:\temp\apache-tomcat-9.0.39.exe /S
		/D=C:\icm\tomcat"  */
		
		String TOMCAT_INSTALLER_PROMPT = "Enter new tomcat installer location (for an example, c:\\tomcatInstaller\\apache-tomcat-9.0.22.exe): ";

        SystemConsole  console = new SystemConsole();

		String newInstallerPath = console.readLine(TOMCAT_INSTALLER_PROMPT);
		String icmInstallDrive = RegistryManager.getIcmInstallDrive();
		String icmConfiglocation = icmInstallDrive + ":\\icm\\install\\tomcatConfig.ini";
		
		 String installTomcatCmd = installerPath + " /C="+ icmConfiglocation + " /S /D=" + currentInstallDir;
	}

}
