import java.io.File;
import java.io.IOException;

public class UninstallTomcat {

    public static void main(String[] args) {
        // Define the path to Uninstall.exe
        String uninstallPath = "C:\\Program Files (x86)\\Apache Software Foundation\\Tomcat 9.0";
        String executable = "Uninstall.exe";
        String serviceName = "Tomcat9";

        // Full command with arguments
        String command = executable + " /S -ServiceName=" + serviceName;

        ProcessBuilder processBuilder = new ProcessBuilder("cmd.exe", "/c", command);
        processBuilder.directory(new File(uninstallPath));
        processBuilder.redirectErrorStream(true);

        try {
            Process process = processBuilder.start();
            int exitCode = process.waitFor();
            System.out.println("Uninstall process exited with code: " + exitCode);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}