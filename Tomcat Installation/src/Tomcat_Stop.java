import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class Tomcat_Stop {

	public static void main(String[] args) {
		 
		try {
	            // Construct the command
	            String command = "sc stop \"Tomcat9\"";

	            // Create a ProcessBuilder
	            ProcessBuilder processBuilder = new ProcessBuilder("cmd.exe", "/c", command);

	            // Redirect error stream to standard output to capture any errors
	            processBuilder.redirectErrorStream(true);

	            // Start the process
	            Process process = processBuilder.start();

	            // Read the output
	            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
	            String line;
	            while ((line = reader.readLine()) != null) {
	                System.out.println(line);
	            }

	            // Wait for the process to complete
	            int exitCode = process.waitFor();
	            System.out.println("\nExited with error code : " + exitCode);

	            if(exitCode != 0){
	                System.out.println("Tomcat9 stop command may have failed. Check the output above for errors.");
	            } else {
	                System.out.println("Tomcat9 successfully stopped.");
	            }

	        } catch (IOException | InterruptedException e) {
	            e.printStackTrace();
	        	}

}
		
		
		
	}

