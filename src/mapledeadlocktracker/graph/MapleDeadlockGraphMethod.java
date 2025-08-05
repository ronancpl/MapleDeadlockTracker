/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package mapledeadlocktracker.graph;

import java.util.LinkedList;
import java.util.List;

/**
 *
 * @author RonanLana
 */
    
public class MapleDeadlockGraphMethod {
    String srcName;
    
    int id;
    List<MapleDeadlockGraphEntry> entryNodes = new LinkedList<>();
    
    public MapleDeadlockGraphMethod(int id, String srcName) {
        this.id = id;
        this.srcName = srcName;
    }
    
    public void addGraphEntry(MapleDeadlockGraphEntry e) {
        entryNodes.add(e);
    }
    
    public int getId() {
        return id;
    }
    
    public String getSourceName() {
        return srcName;
    }
    
    public List<MapleDeadlockGraphEntry> getEntryList() {
        return entryNodes;
    }
}
