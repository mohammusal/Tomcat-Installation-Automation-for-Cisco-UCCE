import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper around the system console to support prompting the user and testing
 *
 *
 */
public class SystemConsole {

    protected List<String> readLineBuffer = new ArrayList<String>();

    public SystemConsole(){

    }

    public SystemConsole(List<String> readLineBuffer){
        this.readLineBuffer = readLineBuffer;
    }

    public String readLine(String prompt) {

        if(readLineBuffer.size() > 0){
            return readLineBuffer.remove(0);
        }

        // If this is being run as a jar from command line, System.console() will exist;
        // however, if you want to run in intellij for debugging, System.console() will be
        // null, so we need to read from the screen a different way
        if (System.console() != null) {
            return System.console().readLine(prompt);
        } else {
            try {
                System.out.print(prompt);
                BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
                return reader.readLine();
            } catch (IOException e) {
                return "";
            }
        }
    }
}