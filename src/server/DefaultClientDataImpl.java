
package server;

import java.util.ArrayList;

/**
 *
 * @author jaron
 */
public class DefaultClientDataImpl implements IClientData {

    private String clientID;
    private long ping;
    private ArrayList<String> clientTags = new ArrayList<>();
    
    @Override
    public String getClientID() {
        return clientID;
    }

    @Override
    public void setClientID(String clientID) {
        this.clientID = clientID;
    }

    @Override
    public long getPing() {
        return ping;
    }

    @Override
    public void setPing(long ping) {
        this.ping = ping;
    }

    @Override
    public ArrayList<String> getClientTags() {
        return clientTags;
    }

    @Override
    public void setClientTags(ArrayList<String> clientTags) {
        this.clientTags = clientTags;
    }
    
}
