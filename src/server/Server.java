/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package server;

import data.*;
import java.io.*;
import java.net.*;
import java.util.*;

/**
 *
 * @author jaron
 */
public abstract class Server {
    
    protected HashMap<String, Response> responses = new HashMap<>();
    
    protected ServerSocket server;
    protected int port;
    protected ArrayList<RemoteClient> connectedClients;
    protected ArrayList<RemoteClient> clientCleanupQueue;
    
    protected Thread listener;
    
    protected boolean stopped;
    protected int pingInterval = 15 * 1000;
    
    public void setPingInterval(int seconds) {
        this.pingInterval = seconds * 1000;
    }
    
    public Server(int port, boolean keepConnectionAlive, ClientData ch){
        this.connectedClients = new ArrayList<>();
        this.port = port;
        
        responses.put("REGISTER_CLIENT", new Response(){
            @Override
            public void run(Data data, Socket socket) {
                connectedClients.add(new RemoteClient((String) data.get(1), socket, ch));
                // This code is kinda awful but I can't think of a better
                // solution rn so I'll leave it as is
                for (RemoteClient c : connectedClients){
                    if (c.getId().equalsIgnoreCase((String) data.get(1))){
                        c.getHandler().setClientID((String) data.get(1));
                    }
                }
                onClientRegistered(data, socket);
            }
        });
        
        responses.put("PONG", new Response(){
           @Override
           public void run(Data data, Socket socket) {
               long ping = Math.abs(lastPingTime - (long) data.get(1)) / 1000000;
               for (RemoteClient c : connectedClients){
                   if (c.getId().equalsIgnoreCase(data.getSenderID())){
                       c.getHandler().updatePing(ping);
                   }
               }
           }
        });
        
        registerResponses();
        start();
        
        if (keepConnectionAlive)
            startPingThread();
    }
    
    public Server(int port, boolean keepConnectionAlive){
        this(port, keepConnectionAlive, new ClientData());
    }
    
    public abstract void registerResponses();
    
    public void registerResponse(String identifier, Response response){
        if (identifier.equalsIgnoreCase("REGISTER_CLIENT"))
            throw new IllegalArgumentException("Identifier can not be 'REGISTER_CLIENT'.");
        
        responses.put(identifier, response);
    }
    
    protected void startListener(){
        if (listener == null && server != null){
            listener = new Thread(new Runnable() {
               @Override
               public void run(){
                   while (!Thread.interrupted() && !stopped && server != null){
                       try {

                           Socket clientSocket = server.accept();
                           
                           ObjectInputStream in = new ObjectInputStream(
                                new BufferedInputStream(clientSocket.getInputStream()));
                           Object data = in.readObject();
                           
                           if (data instanceof Data){
                               Data message = (Data) data;
                               
                               for (String s : responses.keySet()){
                                   if (message.id().equalsIgnoreCase(s)) {
                                       log("[Server] Responding to client request " + message.id());
                                       startRequestHandler(s, message, clientSocket);
                                       break;
                                   }
                               }
                           }
                           
                       } catch (SocketException e) {
                           logError("Server stopped: " + e.getMessage());
                           onServerStopped();
                       } catch (IOException | ClassNotFoundException e) {
                           
                       } 
                   }
               }
            });
            listener.start();
        }
    }
    
    protected void startRequestHandler(String requestID, Data data, Socket socket){
        new Thread(new Runnable(){
            @Override
            public void run(){
                responses.get(requestID).run(data, socket);
                
                if (!data.id().equals("REGISTER_CLIENT")) {
                    try {
                        socket.close();
                    } catch (IOException e) {
                        logError("Error closing socket: " + e.getMessage());
                    }
                }
            }
        }).start();
    }
    
    protected void start(){
        stopped = false;
        server = null;
        
        try {
            server = new ServerSocket(port);
        } catch (IOException e) {
            logError("Error opening ServerSocket: " + e.getMessage());
        }
        
        startListener();
    }
    
    public void stop() {
        stopped = true;
        
        if (listener.isAlive()){
            listener.interrupt();
        }
        
        if (server != null){
            try {
                server.close();
            } catch (IOException e) {
                logError("Error closing ServerSocket: " + e.getMessage());
            }
        }
    }
    
    protected long lastPingTime;
    
    private void startPingThread() {
        new Thread(new Runnable(){
            @Override
            public void run(){
                while (server != null){
                    try {
                        Thread.sleep(pingInterval);
                    } catch (InterruptedException ex) {  }
                    lastPingTime = System.nanoTime();
                    if (connectedClients != null){
                        ArrayList<ClientData> currentClients = new ArrayList<>();
                        for (RemoteClient c : connectedClients){
                            currentClients.add(c.getHandler());
                        }
                        broadcastMessage(new Data("PING", lastPingTime, currentClients));
                        onPing();
                    } else {
                        broadcastMessage(new Data("PING", lastPingTime));
                        onPing();
                    }
                }
            }
        }).start();
    }
    
    public synchronized void sendMessage(RemoteClient client, Data data) {
        try {
            if (!client.getSocket().isConnected())
                throw new ConnectException("Remote Client is not connected");
            
            ObjectOutputStream out = new ObjectOutputStream(
                new BufferedOutputStream(client.getSocket().getOutputStream()));
            out.writeObject(data);
            out.flush();
            
        } catch (IOException e) {
            logError("Error sending message: " + e.getMessage());
            
            if (clientCleanupQueue != null) {
                clientCleanupQueue.add(client);
            } else {
                connectedClients.remove(client);
                onClientRemoved(client);
            }
        }
    }
    
    public synchronized void sendReply(Socket toSocket, String replyID, Object... datapackageContent) {
        sendMessage(new RemoteClient(null, toSocket, null), new Data(replyID, datapackageContent));
    }
    
    public synchronized int broadcastMessage(Data data){
        int received = 0;
        for (RemoteClient client : connectedClients){
            sendMessage(client, data);
            received++;
        }
        
        if(clientCleanupQueue != null) {
            received -= clientCleanupQueue.size();
        
            for (RemoteClient client : clientCleanupQueue){
                connectedClients.remove(client);
                onClientRemoved(client);
            }
        }
        
        return received;
    }
    
    public synchronized int numConnectedClients() {
        int num = 0;
        if (connectedClients != null) {
            num = connectedClients.size();
        }
        
        return num;
    }
    
    public boolean isClientConnected(String clientID) {
        if (connectedClients != null && connectedClients.size() > 0){
            for (RemoteClient client : connectedClients){
                if (client.getId().equals(clientID) && client.getSocket() != null 
                        && client.getSocket().isConnected()){
                    return true;
                }
            }
        }
        return false;
    }
    
    public void log(String message){
        System.out.println(message);
    }
    public void logError(String message){
        System.err.println(message);
    }
    
    
    //Override methods
    public void onClientRegistered(Data data, Socket socket){
        
    }
    
    public void onClientRemoved(RemoteClient client){
        
    }
    
    public void onServerStopped(){
        
    }
    
    public void onPing(){
        
    }
    
    
}
