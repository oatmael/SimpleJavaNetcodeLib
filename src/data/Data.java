
package data;

import java.util.ArrayList;
import java.util.Arrays;

/**
 *
 * @author jaron
 */
public class Data extends ArrayList<Object>{
    private String senderID = null;
    
    /**
     * Constructor for the data object
     * @param id The response identifier for the message
     * @param o The objects to be sent in the message
     */
    public Data(String id, Object... o){
        this.add(0, id);
        this.addAll(Arrays.asList(o));
    }
    
    /**
     * Return the identifier for the data
     * @return The identifier for the data
     */
    public String id(){
        return (String) this.get(0);
    }
    
    /**
     * Return the sender's ID
     * @return The sender's ID
     */
    public String getSenderID() {
        return this.senderID;
    }
    
    /**
     * Sign the message with the client's ID to be used by the server
     * @param senderID The client's ID
     */
    public void sign(String senderID) {
        this.senderID = senderID;
    }
}
