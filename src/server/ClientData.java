/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package server;

import java.io.Serializable;

/**
 *
 * @author jaron
 */
public class ClientData implements Serializable {
    private String clientID;
    private long ping;
    
    public String getClientID() {
        return clientID;
    }
    public void setClientID(String clientID) {
        this.clientID = clientID;
    }
    
    public long getPing(){
        return ping;
    }
    public void updatePing(long newPing){
        ping = newPing;
    }
}
