
package data;

import java.net.Socket;

/**
 * Interface for creating responses to events
 * Insert your logic for responses inside the run function
 * @author jaron
 */
public interface Response {

    /**
     * Run user-defined code for defined responses
     * @param data The data sent by the client/server
     * @param socket The socket for the client/server
     */
    public abstract void run(Data data, Socket socket);
}
