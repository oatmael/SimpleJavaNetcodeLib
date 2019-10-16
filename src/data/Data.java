/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package data;

import java.util.ArrayList;
import java.util.Arrays;

/**
 *
 * @author jaron
 */
public class Data extends ArrayList<Object>{
    private String senderID = null;
    
    public Data(String id, Object... o){
        this.add(0, id);
        this.addAll(Arrays.asList(o));
    }
    
    public String id(){
        return (String) this.get(0);
    }
    
    public String getSenderID() {
        return this.senderID;
    }
    
    public void sign(String senderID) {
        this.senderID = senderID;
    }
}
