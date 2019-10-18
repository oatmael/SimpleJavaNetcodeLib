/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package server;

import java.net.Socket;

/**
 *
 * @author jaron
 */
public class RemoteClient {
    private String id;
    private Socket socket;
    private IClientData handler;
    
    public RemoteClient(String id, Socket socket, IClientData handler){
        this.id = id;
        this.socket = socket;
        this.handler = handler;
    }

    public String getId() {
        return id;
    }

    public Socket getSocket() {
        return socket;
    }
    
    public IClientData getHandler(){
        return handler;
    }
    
    @Override
    public String toString(){
        return "[RemoteClient: + " + id + " @ " + socket.getRemoteSocketAddress() + "]";
    }
}
