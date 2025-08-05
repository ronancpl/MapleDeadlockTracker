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
public class MapleDeadlockGraphEntry {
    List<MapleDeadlockGraphNode> points = new LinkedList<>();
    
    public MapleDeadlockGraphEntry() {}
    
    public MapleDeadlockGraphEntry(MapleDeadlockGraphNode entry) {
        points.add(entry);
    }
    
    public void addGraphEntryPoint(MapleDeadlockGraphNode entry) {
        points.add(entry);
    }
    
    public List<MapleDeadlockGraphNode> getGraphEntryPoints() {
        return points;
    }
     
}
