
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
    protected boolean keepConnectionAlive;
    protected boolean logResponses;
    protected boolean stopped;
    protected int pingInterval = 15 * 1000;
    
    /**
     * Setter for the time between pings when the
     * <code>keepConnectionAlive</code> flag is set
     * @param seconds The time in seconds between pings
     */
    public void setPingInterval(int seconds) {
        this.pingInterval = seconds * 1000;
    }
    
    public static final boolean DEFAULT_KEEP_ALIVE = true;
    public static final boolean DEFAULT_LOG_RESPONSES = true;
    
    

    // Constructors

    /**
     * All-args constructor for a Server object.
     * @param port The port to run the server on
     * @param keepConnectionAlive Flag for sending periodic pings to connected clients
     * @param logResponses Flag for logging client repsonses
     * @param cd The implementation for data kept on the Server
     */  
    public Server(int port, boolean keepConnectionAlive, boolean logResponses, Class cd){
        this.connectedClients = new ArrayList<>();
        this.clientCleanupQueue = new ArrayList<>();
        this.port = port;
        this.keepConnectionAlive = keepConnectionAlive;
        this.logResponses = logResponses;
        
        boolean correctImpl = false;
        for (Class c : cd.getInterfaces()){
            if (c.equals(IClientData.class))
                correctImpl = true;
        }
                    
        if (!correctImpl){
            logError("Class " + cd.getSimpleName() + " is not an implementation of IClientData!");
            logError("Exiting...");
            return;
        }
        
        log("[Server] Registering default responses...");
        registerDefaultResponses(cd);
        log("[Server] Registering additional responses...");
        registerResponses();
        start();
        
        if (keepConnectionAlive){
            log("[Server] Starting ping thread...");
            startPingThread();
        }
    }
    
    /**
     * Constructor for a Server object, omitting the 
     * <code>keepConnectionAlive</code> flag
     * @param port The port to run the server on
     * @param cd The implementation for data kept on the Server
     */
    public Server(int port, Class cd){
        this(port, DEFAULT_KEEP_ALIVE, DEFAULT_LOG_RESPONSES, cd);
    }
    
    /**
     * Constructor for a Server object, omitting the IClientData implementation
     * @param port The port to run the server on
     * @param keepConnectionAlive Flag for sending periodic pings to connected clients
     */
    public Server(int port, boolean keepConnectionAlive){
        this(port, keepConnectionAlive, DEFAULT_LOG_RESPONSES, DefaultClientDataImpl.class);
    }
    
    /**
     *
     * @param port
     * @param keepConnectionAlive
     * @param cd
     */
    public Server(int port, boolean keepConnectionAlive, Class cd){
        this(port, DEFAULT_KEEP_ALIVE, DEFAULT_LOG_RESPONSES, cd);
    }
    
    /**
     * Most basic constructor for a Server object
     * @param port The port to run the server on
     */
    public Server(int port){
        this(port, DEFAULT_KEEP_ALIVE, DEFAULT_LOG_RESPONSES, DefaultClientDataImpl.class);
    }
    
    /**
     * Method for implementing user-defined responses to messages sent from
     * clients. This method is called before the server is started, and can be
     * used for most initialization methods.
     */
    public abstract void registerResponses();
    
    /**
     * Registering reserved responses used by the server. These responses can not
     * be defined by the user, except in the case where the <code>keepConnectionAlive</code>
     * flag is not set and 'PONG' is defined.
     * @param cd The ClientData implementation
     */
    private void registerDefaultResponses(Class cd){
        responses.put("REGISTER_CLIENT", new Response(){
            @Override
            public void run(Data data, Socket socket) {
                try {
                    connectedClients.add(new RemoteClient((String) data.getSenderID(), socket, (IClientData) cd.newInstance()));
                } catch (InstantiationException | IllegalAccessException ex) {
                    logError(ex.getMessage());
                    return;
                } 
                // This code is kinda awful but I can't think of a better
                // solution rn so I'll leave it as is
                for (RemoteClient c : connectedClients){
                    if (c.getId().equalsIgnoreCase((String) data.getSenderID())){
                        c.getClientData().setClientID((String) data.getSenderID());
                    }
                }
                onClientRegistered(data, socket);
            }
        });
        
        responses.put("SET_CLIENT_TAGS", new Response(){
            @Override
            public void run(Data data, Socket socket) {
                data.remove(0);
                for (RemoteClient c : connectedClients){
                    if (c.getId().equalsIgnoreCase(data.getSenderID())){
                        c.getClientData().getClientTags().clear();
                        ArrayList<String> temp = new ArrayList<>();
                        for (Object s : data){
                            temp.add((String) s);
                        }
                        log("[Server] Setting client tags " + temp.toString() + " for client: " + c.getId());
                        c.getClientData().setClientTags(temp);
                        onTagsSet(c);
                    }
                }
            }
        });
        
        responses.put("ADD_CLIENT_TAGS", new Response(){
            @Override
            public void run(Data data, Socket socket) {
                data.remove(0);
                for (RemoteClient c : connectedClients){
                    if (c.getId().equalsIgnoreCase(data.getSenderID())){
                        ArrayList<String> temp = new ArrayList<>();
                        for (Object s : data){
                            temp.add((String) s);
                        }
                        log("[Server] Adding client tags " + temp.toString() + " for client: " + c.getId());
                        c.getClientData().setClientTags(temp);
                        onTagsAdded(c);
                    }
                }
            }
        });
        
        responses.put("REMOVE_CLIENT_TAGS", new Response(){
            @Override
            public void run(Data data, Socket socket) {
                data.remove(0);
                for (RemoteClient c : connectedClients){
                    if (c.getId().equalsIgnoreCase(data.getSenderID())){
                        ArrayList<String> temp = new ArrayList<>();
                        for (Object s : data){
                            temp.remove((String) s);
                        }
                        log("[Server] Removing client tags " + temp.toString() + " for client: " + c.getId());
                        c.getClientData().setClientTags(temp);
                        onTagsRemoved(c);
                    }
                }
            }
        });
        
        responses.put("LOGOUT", new Response(){
            @Override
            public void run(Data data, Socket socket) {
                for (RemoteClient c : connectedClients){
                    if (c.getId().equalsIgnoreCase((String) data.getSenderID())){
                        log("[Server] Logging out client " + c.getId());
                        clientCleanupQueue.add(c);
                        onClientLogout();
                    }
                }
                cleanupClients();
            }
        });
        
        if (keepConnectionAlive)
            responses.put("PONG", new Response(){
                @Override
                public void run(Data data, Socket socket) {
                    long ping = Math.abs(lastPingTime - (long) data.get(1)) / 1000000;
                    for (RemoteClient c : connectedClients){
                        if (c.getId().equalsIgnoreCase(data.getSenderID())){
                            c.getClientData().setPing(ping);
                        }
                    }
                }
            });
    }
    
    /**
     * The method used to define server responses. Use within the
     * <code>registerResponses()</code> method.
     * @param identifier The string used to identify the response, i.e. "REQUEST_INFO"
     * @param response The action that occurs upon receiving the response
     */
    public void registerResponse(String identifier, Response response){
        for (String s : responses.keySet()){
            if (identifier.equalsIgnoreCase(s)){
                if (identifier.equalsIgnoreCase("PONG") && !keepConnectionAlive)
                    break;
                throw new IllegalArgumentException("Identifier can not be " 
                        + s + ". Already regsitered.");
            }
        } 
        
        responses.put(identifier, response);
    }
    
    /**
     * Main server loop to receive requests.
     */
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
                                        // avoiding the log being spammed with ping requests/responses
                                        if (!message.id().equalsIgnoreCase("PONG") && logResponses)
                                            log("[Server] Responding to client " 
                                                    + message.getSenderID() + " request " + message.id());
                                        startRequestHandler(s, message, clientSocket);
                                        break;
                                    }
                                }
                            }
                        } catch (SocketException e) {
                            logError("Server stopped: " + e.getMessage());
                            onServerStopped();
                        } catch (IOException | ClassNotFoundException e) {
                            logError("Server stopped: " + e.getMessage());
                            onServerStopped();
                        } 
                    }    
                }
            });
            listener.start();
        }
    }
    
    /**
     * Starts the thread to handle the client request
     * @param requestID The response identifier
     * @param data The data sent with the request
     * @param socket The client socket that sent the request
     */
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
    
    /**
     * Sets flags, opens the server socket and starts the main listener loop
     */
    protected void start(){
        stopped = false;
        server = null;
        
        log("[Server] Attempting to open socket...");
        try {
            server = new ServerSocket(port);
        } catch (IOException e) {
            logError("Error opening ServerSocket: " + e.getMessage());
        }
        
        log("[Server] Starting Server...");
        startListener();
    }
    
    /**
     * Stops the server and closes sockets.
     */
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
    
    /**
     * Starts the thread that periodically pings connected clients.
     */
    private void startPingThread() {
        new Thread(new Runnable(){
            @Override
            public void run(){
                while (server != null){
                    try {
                        Thread.sleep(pingInterval);
                    } catch (InterruptedException ex) {  }
                    lastPingTime = System.nanoTime();
                    if (connectedClients.size() > 1){
                        ArrayList<IClientData> currentClients = new ArrayList<>();
                        for (RemoteClient c : connectedClients){
                            currentClients.add(c.getClientData());
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
    
    /**
     * Sends a message to a specified client
     * @param client The client to send the message to
     * @param data The data that is sent to the client
     */
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
            clientCleanupQueue.add(client);
        }
    }
    
    /**
     * Helper function for sending replies when receiving requests
     * @param toSocket The client socket to reply to
     * @param replyID The response identifier to send
     * @param datapackageContent The content to send in the response
     */
    public synchronized void sendReply(Socket toSocket, String replyID, Object... datapackageContent) {
        sendMessage(new RemoteClient(null, toSocket, null), new Data(replyID, datapackageContent));
    }
    
    /**
     * Helper function for sending replies when receiving requests
     * @param toSocket The client socket to reply to
     * @param dataToBeSent The data to send back
     */
    public synchronized void sendReply(Socket toSocket, Data dataToBeSent) {
        sendMessage(new RemoteClient(null, toSocket, null), dataToBeSent);
    }
    
    /**
     * Send a message to all connected clients
     * @param data The data to send
     * @return The amount of clients who received the message
     */
    public synchronized int broadcastMessage(Data data){
        int received = 0;
        for (RemoteClient client : connectedClients){
            sendMessage(client, data);
            received++;
        }
        
        if(clientCleanupQueue.size() > 0) {
            received -= clientCleanupQueue.size();
            cleanupClients();
        }
        
        return received;
    }
    
    /**
     * Sends a message to one/multiple clients based on how many have the given
     * tags.
     * @param data The message to be sent
     * @param tag The tag to send the message to
     * @param tags Varargs for multiple tags
     * @return
     */
    public synchronized int sendMessageToTaggedClients(Data data, String tag, String... tags){
        int received = 0;
        ArrayList<RemoteClient> messageQueue = new ArrayList<>();
        ArrayList<String> tagsToAdd = new ArrayList<>();
        
        tagsToAdd.add(tag);
        if (tags != null)
            for (String s : tags) {
                tagsToAdd.add(s);
            }
        
        // likely a far more efficient way to do this
        // luckily I'm not looking for efficiency so this will do
        for (RemoteClient client : connectedClients){
            if (!messageQueue.contains(client)) {
                for (String s : tagsToAdd){
                    if (client.getClientData().getClientTags().contains(s)){
                        messageQueue.add(client);
                    }
                }
            }
        }
        
        for (RemoteClient client : messageQueue){
            sendMessage(client, data);
            received++;
        }
        
        if(clientCleanupQueue.size() > 0) {
            received -= clientCleanupQueue.size();
            cleanupClients();
        }
        
        return received;
    }
    
    /**
     * Cleanup clients that are marked for deletion from the connected clients
     * list. Don't call this while connectedClients list is being iterated on.
     */
    public synchronized void cleanupClients(){
        log("[Server] Cleaning up clients...");
        if (connectedClients.size() > 0 && clientCleanupQueue != null)
            for (RemoteClient client : clientCleanupQueue){
                connectedClients.remove(client);
                onClientRemoved(client);
            }
        clientCleanupQueue.clear();
    }
    
    /**
     * Returns the number of currently connected clients
     * @return The number of currently connected clients
     */
    public synchronized int numConnectedClients() {
        int num = 0;
        if (connectedClients != null) {
            num = connectedClients.size();
        }
        
        return num;
    }
    
    /**
     * Helper function to easily set a client's tags
     * @param clientID The client to set tags for
     * @param tag The tag to set
     * @param tags Varargs for multiple tags
     */
    public synchronized void setClientTags(String clientID, String tag, String... tags){
        for (RemoteClient client : connectedClients){
            if (client.getId().equalsIgnoreCase(clientID)) {
                ArrayList<String> temp = new ArrayList<>();
                temp.add(tag);
                if (tags != null)
                    for (String t : tags){
                        temp.add(t);
                    }
                client.getClientData().setClientTags(temp);
                onTagsSet(client);
            }
        }
    }
    
    /**
     * Returns whether a given client is connected
     * @param clientID The client to check
     * @return Whether the client is connected
     */
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
    
    /**
     * It's just System.out.println();
     * @param message
     */
    public static void log(String message){
        System.out.println(message);
    }

    /**
     * It's just System.err.println();
     * @param message
     */
    public static void logError(String message){
        System.err.println(message);
    }
    
    
    //Override methods

    /**
     * Called when a client registers, override this method to add functionality.
     * @param data The data the client registered with
     * @param socket The client socket
     */
    public void onClientRegistered(Data data, Socket socket){
        
    }
    
    /**
     * Called when a client is removed from the active client list, 
     * override this method to add functionality.
     * @param client
     */
    public void onClientRemoved(RemoteClient client){
        
    }
    
    /**
     * Called when the server stops, override this method to add functionality.
     */
    public void onServerStopped(){
        
    }
    
    /**
     * Called when the server broadcasts a ping, 
     * override this method to add functionality.
     */
    public void onPing(){
        
    }
    
    /**
     * Called when a client logs out, override this method to add functionality.
     */
    public void onClientLogout(){
        
    }
    
    /**
     * Called when a client's tag is set, override this method to add functionality.
     * @param client The client who's tag was set
     */
    public void onTagsSet(RemoteClient client){
        
    }
    
    /**
     * Called when a client has tags added, override this method to add functionality.
     * @param client The client who had tags added
     */
    public void onTagsAdded(RemoteClient client){
        
    }
    
    /**
     * Called when a client's tags are removed, override this method to add functionality.
     * @param client The client who had tags removed
     */
    public void onTagsRemoved(RemoteClient client){
        
    }
    
    
}