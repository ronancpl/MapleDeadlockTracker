/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package mapledeadlocktracker;

import java.util.Map;
import mapledeadlocktracker.containers.MapleDeadlockFunction;
import mapledeadlocktracker.graph.MapleDeadlockGraphMethod;

/**
 *
 * @author RonanLana
 */
public class MapleDeadlockGraph {
    
    private Map<MapleDeadlockFunction, Integer> mapleGraphFunctionIds;
    private Map<MapleDeadlockFunction, MapleDeadlockGraphMethod> mapleGraphFunctions;
    
    public MapleDeadlockGraph(Map<MapleDeadlockFunction, Integer> mapleGraphFunctionIds, Map<MapleDeadlockFunction, MapleDeadlockGraphMethod> mapleGraphFunctions) {
        this.mapleGraphFunctionIds = mapleGraphFunctionIds;
        this.mapleGraphFunctions = mapleGraphFunctions;
    }
    
    public Map<MapleDeadlockFunction, Integer> getFunctionIds() {
        return this.mapleGraphFunctionIds;
    }
    
    public Map<MapleDeadlockFunction, MapleDeadlockGraphMethod> getFunctionGraph() {
        return this.mapleGraphFunctions;
    }
    
}
