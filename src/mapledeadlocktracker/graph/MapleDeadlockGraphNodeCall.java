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
public class MapleDeadlockGraphNodeCall extends MapleDeadlockGraphNode {
    Integer callFid;
    
    public MapleDeadlockGraphNodeCall(Integer fid) {
        super(-1, MapleDeadlockGraphNodeType.CALL);
        callFid = fid;
    }
    
    @Override
    public String toString() {
        return "C" + callFid;
    }
}
