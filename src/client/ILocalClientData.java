/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package client;

import java.util.*;
import server.IClientData;

/**
 *
 * @author jaron
 */
public interface ILocalClientData {
    public ArrayList<IClientData> getConnectedClientInfo();
    public void setConnectedClientInfo(ArrayList<IClientData> connectedClientInfo);
    
    public long getPing();
    public void updatePing(long newPing);
}
