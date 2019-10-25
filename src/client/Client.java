
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
public abstract class Client {
    

    protected HashMap<String, Response> responses = new HashMap<>();
    
    protected String id;

    public String getId() {
        return id;
    }
    
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
        private ArrayList<String> clientTags;
        
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
    
    // Constructors

    /**
     * All-args constructor for Client object.
     * @param hostname The server hostname
     * @param port The server port
     * @param timeout Time in milliseconds for timeout functions (server connect, send message)
     * @param id The client ID (should be unique! if you can't implement UID, use another constructor)
     * @param localClientData The implementation for data kept on the Client
     */
    
    public Client(String hostname, int port, int timeout, String id, ILocalClientData localClientData){
        this.address = new InetSocketAddress(hostname, port);
        this.timeout = timeout;
        this.id = id;
        this.errors = 0;
        this.localClientData = localClientData;
        
        registerDefaultResponses();
        registerResponses();
    }
    
    /**
     * Constructor for Client object omitting the ILocalClientData implementation (uses default)
     * @param hostname The server hostname
     * @param port The server port 
     * @param timeout Time in milliseconds for timeout functions (server connect, send message)
     * @param id The client ID (should be unique! if you can't implement UID, use another constructor)
     */
    public Client(String hostname, int port, int timeout, String id) {
        this(hostname, port, timeout, id, new DefaultLocalClientDataImpl());
    }
    
    /**
     * Constructor for Client object omitting the ILocalClientData and ID
     * (ID is randomly generated in this case)
     * @param hostname The server hostname
     * @param port The server port 
     * @param timeout Time in milliseconds for timeout functions (server connect, send message)
     */
    public Client(String hostname, int port, int timeout) {
        this(hostname, port, timeout, DEFAULT_USER_ID, new DefaultLocalClientDataImpl());
    }
    
    /**
     * Constructor for Client object omitting the ID (ID is randomly generated in this case)
     * @param hostname The server hostname
     * @param port The server port 
     * @param timeout Time in milliseconds for timeout functions (server connect, send message)
     * @param localClientData The implementation for data kept on the Client
     */
    public Client(String hostname, int port, int timeout, ILocalClientData localClientData) {
        this(hostname, port, timeout, DEFAULT_USER_ID, localClientData);
    }
    
    /**
     * Constructor for Client object omitting timeout
     * @param hostname The server hostname
     * @param port The server port
     * @param id The client ID (should be unique! if you can't implement UID, use another constructor)
     * @param localClientData The implementation for data kept on the Client
     */ 
    public Client(String hostname, int port, String id, ILocalClientData localClientData) {
        this(hostname, port, DEFAULT_TIMEOUT, id, localClientData);
    }
    
    /**
     * Constructor for Client object omitting timeout and ID (ID is randomly generated in this case)
     * @param hostname The server hostname
     * @param port The server port
     * @param localClientData The implementation for data kept on the Client
     */
    public Client(String hostname, int port, ILocalClientData localClientData) {
        this(hostname, port, DEFAULT_TIMEOUT, DEFAULT_USER_ID, localClientData);
    }
    
    /**
     * Constructor for Client omitting everything but hostname and port
     * @param hostname The server hostname
     * @param port The server port
     */
    public Client(String hostname, int port) {
        this(hostname, port, DEFAULT_TIMEOUT, DEFAULT_USER_ID, new DefaultLocalClientDataImpl());
    }
    
    /**
     * Method for implementing user-defined responses to messages sent from
     * clients. This method is called before the server is started, and can be
     * used for most initialization methods.
     */
    public abstract void registerResponses();
    
    /**
     * Registering reserved responses used by the client. These responses can not
     * be defined by the user.
     */
    protected void registerDefaultResponses(){
        responses.put("PING", new Response(){
            @Override
            public void run(Data data, Socket socket) {
                if (data.size() > 2) { 
                    localClientData.setConnectedClientInfo((ArrayList<IClientData>) data.get(2));
                    for (IClientData cd : localClientData.getConnectedClientInfo()){
                        if (cd.getClientID().equalsIgnoreCase(id)) {
                            localClientData.setPing(cd.getPing());
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
    
    /**
     * Starts the client, connecting to the server and starting the listener.
     */
    public void start(){
        stopped = false;
        login();
        startListener();
    }
    
    /**
     * Stops the client.
     */
    public void stop(){
        stopped = true;
        try {
            logout();
        } catch (IOException ex) {  }
        log("[Client] Stopping...");
    }
    
    /**
     * Attempts to repair the connection, closing the socket and reopening it.
     */
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
    
    /**
     * Attempt to login to the server
     */
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
    
    /**
     * Attempts to logout.
     * @throws IOException 
     */
    protected void logout() throws IOException {
        log("[Client] Disconnecting from " + address + "...");
        Data message = new Data("LOGOUT", id);
        message.sign(id);
        sendMessage(message, 100, false);
        socket.close();
    }
    
    /**
     * Starts the server listener and runs responses.
     */
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
                            while (!socket.isConnected() && !stopped){
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
                        if (!stopped) {
                            logError("[Client] Connection lost.");
                            onConnectionProblem();
                            repairConnection();
                        }
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
    
    /**
     * Sends a message to the server.
     * @param data The message to be sent to the server
     * @param timeout The timeout length in milliseconds
     * @param expectResponse Whether or not to expect an immediate response from the server
     * @return The data from the server (if a response was expected)
     */
    public Data sendMessage(Data data, int timeout, boolean expectResponse){
        
        try {
            Socket writeSocket = new Socket();
            writeSocket.connect(address, timeout);
            
            ObjectOutputStream out = new ObjectOutputStream(
                new BufferedOutputStream(writeSocket.getOutputStream()));
            data.sign(id);
            out.writeObject(data);
            out.flush();
            
            if (expectResponse){
                ObjectInputStream in = new ObjectInputStream(
                    new BufferedInputStream(writeSocket.getInputStream()));
                Object response = in.readObject();
                in.close();
                
                out.close();
            
                writeSocket.close();
                
                if (response instanceof Data){
                    return (Data) response;
                }
            }
            
            out.close();
            
            writeSocket.close();
            
            
        } catch (EOFException e) {
            logError("[Client] EOFException: did not receive response from server?");
        } catch (IOException | ClassNotFoundException e) {
            logError("[Client] Error while sending message: " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Sends a message to the server, expecting an immediate response
     * @param data The message to send to the server
     * @param timeout The timeout length in milliseconds
     * @return The response from the server
     */
    public Data sendMessage(Data data, int timeout){
        return sendMessage(data, timeout, true);
    }
    
    /**
     * The method used to define client responses. Use within the
     * <code>registerResponses()</code> method.
     * @param identifier The string used to identify the response, i.e. "REQUEST_INFO"
     * @param response The action that occurs upon receiving the response
     */
    public void registerResponse(String identifier, Response response){
        if (identifier.equalsIgnoreCase("PING"))
            throw new IllegalArgumentException("Identifier can not be 'PING'.");
        
        responses.put(identifier, response);
    }
    
    /**
     * Return whether or not the client is currently connected and listening.
     * @return Whether or not the client is currently connected and listening.
     */
    public boolean isListening(){
        return isConnected() && listener != null && listener.isAlive() && errors == 0;
    }
    
    /**
     * Return whether the client is currently connected.
     * @return Whether or not the client is currently connected and listening.
     */
    public boolean isConnected(){
        return socket != null && socket.isConnected();
    }
    
    /**
     * Check whether the specified server is currently reachable.
     * @return Whether the specified server is currently reachable.
     */
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
    
    /**
     * It's just System.out.println();
     * @param message
     */
    public void log(String message){
        System.out.println(message);
    }

    /**
     * It's just System.err.println();
     * @param message
     */
    public void logError(String message){
        System.err.println(message);
    }
    
    //Overrides

    /**
     * Called when the client reconnects, override this method to add functionality.
     */ 
    public void onReconnect(){
        
    }

    /**
     * Called when a connection problem occurs, override this method to add functionality.
     */
    public void onConnectionProblem(){
        
    }

    /**
     * Called when the client successfully connects, override this method to add functionality.
     */
    public void onConnectionGood(){
        
    }

    /**
     * , override this method to add functionality.
     */
    public void onClientDataUpdate() {
        
    }
}
