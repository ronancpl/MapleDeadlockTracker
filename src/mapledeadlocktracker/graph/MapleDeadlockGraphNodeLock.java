/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package mapledeadlocktracker.graph;

/**
 *
 * @author RonanLana
 */
public class MapleDeadlockGraphNodeLock extends MapleDeadlockGraphNode {
    boolean lock;
    Integer id;
    
    public MapleDeadlockGraphNodeLock(Integer lockid, boolean isLocking) {
        super(-1, MapleDeadlockGraphNodeType.LOCK);
        lock = isLocking;
        id = lockid;
    }
    
    @Override
    public String toString() {
        return (lock ? "L" : "U") + id;
    }
}
