/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package client;

import java.util.*;
import server.ClientData;

/**
 *
 * @author jaron
 */
public class LocalClientData {
    private long ping;
    private ArrayList<ClientData> connectedClientInfo;

    public ArrayList<ClientData> getConnectedClientInfo() {
        return connectedClientInfo;
    }
    public void setConnectedClientInfo(ArrayList<ClientData> connectedClientInfo) {
        this.connectedClientInfo = connectedClientInfo;
    }
    
    public long getPing(){
        return ping;
    }
    public void updatePing(long newPing){
        ping = newPing;
    }
}
