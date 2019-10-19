
package server;

import java.io.Serializable;

/**
 *
 * @author jaron
 */
public interface IClientData extends Serializable {
    
    /**
     * Getter for the clientID
     * @return the clientID
     */
    public String getClientID();

    /**
     * Setter for the clientID
     * @param clientID The new clientID
     */
    public void setClientID(String clientID);
    
    /**
     * Getter for the ping
     * @return The ping
     */
    public long getPing();

    /**
     * Setter for the ping
     * @param ping The new ping
     */
    public void setPing(long ping);
}
