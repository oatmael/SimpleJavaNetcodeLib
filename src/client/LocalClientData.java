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
public interface LocalClientData {
    public ArrayList<ClientData> getConnectedClientInfo();
    public void setConnectedClientInfo(ArrayList<ClientData> connectedClientInfo);
    
    public long getPing();
    public void updatePing(long newPing);
}
