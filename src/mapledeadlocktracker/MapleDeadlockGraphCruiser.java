/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package mapledeadlocktracker;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Stack;
import mapledeadlocktracker.containers.MapleDeadlockFunction;
import mapledeadlocktracker.containers.MapleDeadlockStorage;
import mapledeadlocktracker.graph.MapleDeadlockGraphEntry;
import mapledeadlocktracker.graph.MapleDeadlockGraphMethod;
import mapledeadlocktracker.graph.MapleDeadlockGraphNode;

/**
 *
 * @author RonanLana
 */
public class MapleDeadlockGraphCruiser {
    
    private final static MapleDeadlockGraphCruiser instance = new MapleDeadlockGraphCruiser();

    private class MapleDeadlockObject {
        
        Integer lockId;
        MapleDeadlockFunction f1;
        MapleDeadlockFunction f2;
        
        private MapleDeadlockObject(Integer lockId, MapleDeadlockFunction f1, MapleDeadlockFunction f2) {
            this.lockId = lockId;
            this.f1 = f1;
            this.f2 = f2;
        }
        
    }
    
    private class FunctionPathNode {
        
        Set<Integer> acquiredLocks = new HashSet<>();
        List<Integer> seqLocks = new LinkedList<>();
        
    }
    
    static Set<MapleDeadlockObject> deadlocks = new HashSet<>();
    
    static Stack<MapleDeadlockFunction> functionStack = new Stack<>();
    static Map<MapleDeadlockFunction, FunctionPathNode> functionLocks = new HashMap<>();
    
    static Map<Integer, MapleDeadlockFunction> functions = new HashMap<>();
    
    private static boolean isStartingFunction(MapleDeadlockFunction f) {
        return f.getName().contentEquals("handlePacket") || f.getName().contentEquals("run") || f.getName().contentEquals("main") && MapleDeadlockStorage.getCanonClassName(f.getSourceClass()).endsWith("net.server.Server");
    }
    
    private static void commitFunctionAcquiredLocks(MapleDeadlockFunction f, FunctionPathNode trace, FunctionPathNode uptrace) {
        Set<Integer> ongoingLocks = trace.acquiredLocks;
        Set<Integer> locks = functionLocks.get(f).acquiredLocks;
        
        if (locks.size() < ongoingLocks.size()) {
            locks.clear();
            locks.addAll(ongoingLocks);
        }
        
        while (uptrace.seqLocks.size() > trace.seqLocks.size()) uptrace.seqLocks.remove(trace.seqLocks.size());
    }
    
    private static void sourceGraphFunctionLock(MapleDeadlockFunction f, int lockId, Map<MapleDeadlockFunction, MapleDeadlockGraphMethod> g, FunctionPathNode ongoingLocks) {
        List<Integer> list = ongoingLocks.seqLocks;
        
        list.add(lockId);
        ongoingLocks.acquiredLocks.add(lockId);
    }
    
    private static void sourceGraphFunctionUnlock(MapleDeadlockFunction f, int lockId, Map<MapleDeadlockFunction, MapleDeadlockGraphMethod> g, FunctionPathNode ongoingLocks) {
        List<Integer> list = ongoingLocks.seqLocks;
        list.remove(list.lastIndexOf(lockId));
    }
    
    private void runSourceGraphFunction(MapleDeadlockFunction f, Map<MapleDeadlockFunction, MapleDeadlockGraphMethod> g, FunctionPathNode uptrace) {
        int curBaseIdx = functionStack.size();
        functionStack.add(f);
        
        while (functionStack.size() > curBaseIdx) {
            MapleDeadlockFunction mdf = functionStack.pop();
            
            FunctionPathNode ftrace = new FunctionPathNode();
            ftrace.seqLocks.addAll(uptrace.seqLocks);
            
            for (MapleDeadlockGraphEntry e : g.get(mdf).getEntryList()) {
                for (MapleDeadlockGraphNode n : e.getGraphEntryPoints()) {
                    switch(n.getType()) {
                        case CALL:
                            runSourceGraphFunction(mdf, g, ftrace);
                            break;

                        case LOCK:
                            sourceGraphFunctionLock(f, n.getValue(), g, ftrace);
                            break;

                        case UNLOCK:
                            sourceGraphFunctionUnlock(f, n.getValue(), g, ftrace);
                            break;
                    }
                }
            }
            
            commitFunctionAcquiredLocks(f, ftrace, uptrace);
        }
    }
    
    public static void makeRemissiveIndexFunctions(MapleDeadlockGraph graph) {
        for (Entry<MapleDeadlockFunction, Integer> e : graph.getFunctionIds().entrySet()) {
            functions.put(e.getValue(), e.getKey());
        }
    }
    
    public void runSourceGraph(MapleDeadlockGraph graph) {
        makeRemissiveIndexFunctions(graph);
        
        Map<MapleDeadlockFunction, MapleDeadlockGraphMethod> functionGraph = graph.getFunctionGraph();
        for (Entry<MapleDeadlockFunction, MapleDeadlockGraphMethod> e : functionGraph.entrySet()) {
            MapleDeadlockFunction f = e.getKey();
            if (isStartingFunction(f)) {
                FunctionPathNode trace = new FunctionPathNode();
                runSourceGraphFunction(f, functionGraph, trace);
            }
        }
    }
}
