	import java.io.BufferedReader;
	import java.io.IOException;
	import java.io.InputStreamReader;

public class IIS_Stop {

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		 try {
	            // Construct the command
	            String command = "iisreset /stop";

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
	                System.out.println("IIS stop command may have failed. Check the output above for errors.");
	            } else {
	                System.out.println("IIS successfully stopped.");
	            }

	        } catch (IOException | InterruptedException e) {
	            e.printStackTrace();
	        	}

}}
