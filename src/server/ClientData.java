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
public interface ClientData extends Serializable {
    
    public String getClientID();
    public void setClientID(String clientID);
    
    public long getPing();
    public void updatePing(long newPing);
}
