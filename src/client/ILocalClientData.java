
package client;

import java.util.*;
import server.IClientData;

/**
 *
 * @author jaron
 */
public interface ILocalClientData {

    /**
     * Getter for the ArrayList of the ClientData implementation
     * @return The ArrayList of the ClientData implementation
     */
    public ArrayList<IClientData> getConnectedClientInfo();

    /**
     * Setter for the ArrayList of the ClientData implementation
     * @param connectedClientInfo The new connectedClientInfo
     */
    public void setConnectedClientInfo(ArrayList<IClientData> connectedClientInfo);
    
    /**
     * Getter for the ping
     * @return the ping
     */
    public long getPing();

    /**
     * Setter for the ping
     * @param ping The new ping
     */
    public void setPing(long ping);
}
