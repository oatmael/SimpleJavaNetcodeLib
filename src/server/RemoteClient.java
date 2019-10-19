
package server;

import java.net.Socket;

/**
 *
 * @author jaron
 */
public class RemoteClient {
    private String id;
    private Socket socket;
    private IClientData clientData;
    
    /**
     * Constructor for the RemoteClient object
     * @param id The client id
     * @param socket The client's socket
     * @param clientData The IClientData implementation
     */
    public RemoteClient(String id, Socket socket, IClientData clientData){
        this.id = id;
        this.socket = socket;
        this.clientData = clientData;
    }

    /**
     * Getter for the ID
     * @return The ID
     */
    public String getId() {
        return id;
    }

    /**
     * Getter for the client socket
     * @return The client socket
     */
    public Socket getSocket() {
        return socket;
    }
    
    /**
     * Getter for the ClientData implementation
     * @return
     */
    public IClientData getClientData(){
        return clientData;
    }
    
    @Override
    public String toString(){
        return "[RemoteClient: + " + id + " @ " + socket.getRemoteSocketAddress() + "]";
    }
}
