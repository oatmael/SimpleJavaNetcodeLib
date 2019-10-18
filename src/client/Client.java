/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package client;

import data.*;
import server.*;

import java.io.*;
import java.net.*;
import java.nio.channels.AlreadyConnectedException;
import java.util.*;

/**
 *
 * @author jaron
 */
public class Client {
    
    protected HashMap<String, Response> responses = new HashMap<>();
    
    protected String id;
    protected Socket socket;
    protected InetSocketAddress address;
    protected int timeout;
    
    protected Thread listener;
    protected ILocalClientData localClientData;
    
    protected int errors;
    protected boolean stopped;
    
    public static final String DEFAULT_USER_ID = UUID.randomUUID().toString();
    public static final int DEFAULT_TIMEOUT = 30000;
    
    private static class DefaultLocalClientDataImpl implements ILocalClientData {
        private ArrayList<IClientData> connectedClientInfo;
        private long ping;
        
        @Override
        public ArrayList<IClientData> getConnectedClientInfo() {
            return connectedClientInfo;
        }
        @Override
        public void setConnectedClientInfo(ArrayList<IClientData> connectedClientInfo) {
            this.connectedClientInfo = connectedClientInfo;
        }

        @Override
        public long getPing() {
            return ping;
        }
        @Override
        public void updatePing(long newPing) {
            this.ping = newPing;
        }    
    }
    
    // Constructors
    
    public Client(String hostname, int port, int timeout, String id, ILocalClientData localClientData){
        this.address = new InetSocketAddress(hostname, port);
        this.timeout = timeout;
        this.id = id;
        this.errors = 0;
        this.localClientData = localClientData;
        
        registerDefaultResponses();
    }
    
    public Client(String hostname, int port, int timeout, String id) {
        this(hostname, port, timeout, id, new DefaultLocalClientDataImpl());
    }
    
    public Client(String hostname, int port, int timeout) {
        this(hostname, port, timeout, DEFAULT_USER_ID, new DefaultLocalClientDataImpl());
    }
    
    public Client(String hostname, int port, int timeout, ILocalClientData localClientData) {
        this(hostname, port, timeout, DEFAULT_USER_ID, localClientData);
    }
    
    public Client(String hostname, int port, String id, ILocalClientData localClientData) {
        this(hostname, port, DEFAULT_TIMEOUT, id, localClientData);
    }
    
    public Client(String hostname, int port, ILocalClientData localClientData) {
        this(hostname, port, DEFAULT_TIMEOUT, DEFAULT_USER_ID, localClientData);
    }
    
    public Client(String hostname, int port) {
        this(hostname, port, DEFAULT_TIMEOUT, DEFAULT_USER_ID, new DefaultLocalClientDataImpl());
    }
    
    
    
    protected void registerDefaultResponses(){
        responses.put("PING", new Response(){
            @Override
            public void run(Data data, Socket socket) {
                if (data.size() > 2) { 
                    localClientData.setConnectedClientInfo((ArrayList<IClientData>) data.get(2));
                    for (IClientData cd : localClientData.getConnectedClientInfo()){
                        if (cd.getClientID().equalsIgnoreCase(id)) {
                            localClientData.updatePing(cd.getPing());
                        }
                    }
                }
                //localClientData.updatePing((long) data.get(1));
                onClientDataUpdate();
                
                Data response = new Data("PONG", System.nanoTime());
                response.sign(id);
                sendMessage(response, timeout, false);
            }
        });
    }
    
    public void start(){
        stopped = false;
        login();
        startListener();
    }
    
    public void stop(){
        stopped = true;
        log("[Client] Stopping...");
    }
    
    protected void repairConnection(){
        errors++;
        timeout += 1000;
        log("[Client] Attempting to repair connection...");
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException e) {  }
            socket = null;
        }
        
        login();
        startListener();
    }
    
    protected void login(){
        if (stopped){
            return;
        }
        
        try {
            log("[Client] Connecting...");
            if (socket != null && socket.isConnected()){
                throw new AlreadyConnectedException();
            }
            
            socket = new Socket();
            socket.connect(address, timeout);
            
            log("[Client] Connected to " + socket.getRemoteSocketAddress());
            
            try {
                log("[Client] Logging in...");
                
                ObjectOutputStream out = new ObjectOutputStream(
                    new BufferedOutputStream(socket.getOutputStream()));
                Data loginRequest = new Data("REGISTER_CLIENT", id);
                loginRequest.sign(id);
                out.writeObject(loginRequest);
                out.flush();
                
                log("[Client] Logged in.");
                onReconnect();
            } catch (IOException e){
                logError("Login Failed.");
            }
        } catch (ConnectException e){
            logError("Connection Failed: " + e.getMessage());
            onConnectionProblem();
        } catch (IOException e) {
            logError("Connection Failed: " + e.getMessage());
            onConnectionProblem();
        }
    }
    
    protected void startListener(){
        
        if (listener != null && listener.isAlive()){
            return;
        }
        
        listener = new Thread(new Runnable(){
            @Override
            public void run() {
                while (!stopped) {
                    try {
                        if (socket != null && !socket.isConnected()) {
                            while (!socket.isConnected()){
                                repairConnection();
                                if (socket.isConnected()){
                                    break;
                                }
                                
                                Thread.sleep(5000);
                            }
                        }
                        
                        onConnectionGood();
                        
                        ObjectInputStream in = new ObjectInputStream(
                            new BufferedInputStream(socket.getInputStream()));
                        Object data = in.readObject();
                        
                        if (stopped){
                            return;
                        }
                        
                        if (data instanceof Data){
                            Data message = (Data) data;
                            for (String s : responses.keySet()){
                                if (s.equalsIgnoreCase(message.id())){
                                    new Thread(new Runnable(){
                                        @Override
                                        public void run() {
                                            responses.get(s).run(message, socket);
                                        }
                                    }).start();
                                    break;
                                }
                            }
                        }
                    } catch (SocketException e) {
                        onConnectionProblem();
                        logError("[Client] Connection lost.");
                        repairConnection();
                    } catch (ClassNotFoundException | IOException | InterruptedException e) {
                        onConnectionProblem();
                        logError("[Client] Connection was interrupted: " + e.getMessage());
                        repairConnection();
                    }
                    
                    errors = 0;
                }
            }
        });
        listener.start();
    }
    
    public Data sendMessage(Data data, int timeout, boolean expectResponse){
        
        try {
            Socket readSocket = new Socket();
            readSocket.connect(address);
            
            ObjectOutputStream out = new ObjectOutputStream(
                new BufferedOutputStream(readSocket.getOutputStream()));
            data.sign(id);
            out.writeObject(data);
            out.flush();
            
            if (expectResponse){
                ObjectInputStream in = new ObjectInputStream(
                    new BufferedInputStream(readSocket.getInputStream()));
                Object response = in.readObject();
                in.close();
                
                out.close();
            
                readSocket.close();
                
                if (response instanceof Data){
                    return (Data) response;
                }
            }
            
            out.close();
            
            readSocket.close();
            
            
        } catch (EOFException e) {
            logError("[Client] EOFException: did not receive response from server?");
        } catch (IOException | ClassNotFoundException e) {
            logError("[Client] Error while sending message: " + e.getMessage());
        }
        
        return null;
    }
    
    public Data sendMessage(Data data, int timeout){
        return sendMessage(data, timeout, true);
    }
    
    public void registerResponse(String identifier, Response response){
        if (identifier.equalsIgnoreCase("PING"))
            throw new IllegalArgumentException("Identifier can not be 'PING'.");
        
        responses.put(identifier, response);
    }
    
    public boolean isListening(){
        return isConnected() && listener != null && listener.isAlive() && errors == 0;
    }
    
    public boolean isConnected(){
        return socket != null && socket.isConnected();
    }
    
    public boolean isServerReachable(){
        try {
            Socket testSocket = new Socket();
            testSocket.connect(address);
            testSocket.isConnected();
            testSocket.close();
            return true;
        } catch (IOException e){
            return false;
        }
    }
    
    public void log(String message){
        System.out.println(message);
    }
    public void logError(String message){
        System.err.println(message);
    }
    
    //Overrides
    
    public void onReconnect(){
        
    }
    public void onConnectionProblem(){
        
    }
    public void onConnectionGood(){
        
    }
    public void onClientDataUpdate() {
        
    }
}
