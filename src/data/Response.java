/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package data;

import java.net.Socket;

/**
 * Interface for creating responses to events
 * @author jaron
 */
public interface Response {
    public abstract void run(Data data, Socket socket);
}
