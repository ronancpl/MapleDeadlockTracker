/*
    This file is part of the MapleQuestAdvisor planning tool
    Copyleft (L) 2025 RonanLana

    GNU General Public License v3.0

    Permissions of this strong copyleft license are conditioned on making available complete
    source code of licensed works and modifications, which include larger works using a licensed
    work, under the same license. Copyright and license notices must be preserved. Contributors
    provide an express grant of patent rights.
*/
package mapledeadlocktracker;

import java.util.ArrayList;
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

    private class MapleDeadlockItem {
        
        Integer lockId1;
        Integer lockId2;
        MapleDeadlockFunction f1;
        MapleDeadlockFunction f2;
        
        private MapleDeadlockItem(Integer lockId1, Integer lockId2, MapleDeadlockFunction f1, MapleDeadlockFunction f2) {
            this.lockId1 = lockId1;
            this.lockId2 = lockId2;
            this.f1 = f1;
            this.f2 = f2;
        }
        
    }
    
    private class FunctionPathNode {
        
        Set<Integer> acquiredLocks = new HashSet<>();
        List<Integer> seqLocks = new LinkedList<>();
        List<Integer> seqAcqLocks = new ArrayList<>();
        
    }
    
    private class FunctionLockElement {
        
        Integer lockId;
        MapleDeadlockFunction function;
        
        private FunctionLockElement(Integer lockId, MapleDeadlockFunction function) {
            this.lockId = lockId;
            this.function = function;
        }
        
    }
    
    static Set<MapleDeadlockItem> deadlocks = new HashSet<>();
    
    static Stack<MapleDeadlockFunction> functionStack = new Stack<>();
    static Map<MapleDeadlockFunction, FunctionPathNode> functionLocks = new HashMap<>();
    static Map<Integer, Set<MapleDeadlockFunction>> lockFunctions = new HashMap<>();
    
    static Map<Integer, MapleDeadlockFunction> functions = new HashMap<>();
    static Map<Integer, Set<FunctionLockElement>> lockDependencies = new HashMap<>();
    
    private static boolean isStartingFunction(MapleDeadlockFunction f) {
        return f.getName().contentEquals("handlePacket") || f.getName().contentEquals("run") || f.getName().contentEquals("main") && MapleDeadlockStorage.getCanonClassName(f.getSourceClass()).endsWith("net.server.Server");
    }
    
    private static void commitFunctionAcquiredLocks(MapleDeadlockFunction f, FunctionPathNode trace, FunctionPathNode uptrace) {
        if (functionLocks.get(f) != null) {
            Set<Integer> ongoingLocks = trace.acquiredLocks;
            Set<Integer> locks = functionLocks.get(f).acquiredLocks;
            List<Integer> acqLocks = functionLocks.get(f).seqAcqLocks;

            if (acqLocks.size() < trace.seqAcqLocks.size()) {
                locks.clear();
                locks.addAll(ongoingLocks);

                acqLocks.clear();
                acqLocks.addAll(trace.seqLocks);
            }    
        }
        
        uptrace.seqLocks = uptrace.seqLocks.subList(0, Math.min(uptrace.seqLocks.size(), trace.seqLocks.size()));
    }
    
    private static void sourceGraphFunctionLock(MapleDeadlockFunction f, int lockId, Map<MapleDeadlockFunction, MapleDeadlockGraphMethod> g, FunctionPathNode ongoingLocks) {
        List<Integer> list = ongoingLocks.seqLocks;
        
        list.add(lockId);
        ongoingLocks.acquiredLocks.add(lockId);
    }
    
    private static void sourceGraphFunctionUnlock(MapleDeadlockFunction f, int lockId, Map<MapleDeadlockFunction, MapleDeadlockGraphMethod> g, FunctionPathNode ongoingLocks) {
        List<Integer> list = ongoingLocks.seqLocks;
        list.remove(list.lastIndexOf(lockId));
        
        ongoingLocks.seqAcqLocks.add(-lockId); // represents unlock in a lane graph
    }
    
    private void runSourceGraphFunction(MapleDeadlockFunction f, Map<MapleDeadlockFunction, MapleDeadlockGraphMethod> g, FunctionPathNode uptrace) {
        int curBaseIdx = functionStack.size();
        functionStack.add(f);
        
        while (functionStack.size() > curBaseIdx) {
            MapleDeadlockFunction mdf = functionStack.pop();
            if (!functionStack.contains(mdf)) {
                functionStack.add(f);
                
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
                functionStack.pop();
            }
        }
    }
    
    public static void makeRemissiveIndexFunctions(MapleDeadlockGraph graph) {
        for (Entry<MapleDeadlockFunction, Integer> e : graph.getFunctionIds().entrySet()) {
            functions.put(e.getValue(), e.getKey());
        }
    }
    
    private void findFunctionLocks(MapleDeadlockGraph graph) {
        Map<MapleDeadlockFunction, MapleDeadlockGraphMethod> functionGraph = graph.getFunctionGraph();
        for (Entry<MapleDeadlockFunction, MapleDeadlockGraphMethod> e : functionGraph.entrySet()) {
            MapleDeadlockFunction f = e.getKey();
            if (isStartingFunction(f)) {
                FunctionPathNode trace = new FunctionPathNode();
                runSourceGraphFunction(f, functionGraph, trace);
            }
        }
    }
    
    private static int fetchUnlockIndex(List<Integer> fl, int lockId, int idx, int n) {
        int c = 0;
        for (int i = idx + 1; i < fl.size(); i++) {
            if (fl.get(i) == lockId) {
                c++;
            } else if (fl.get(i) == -lockId) {
                if (c == 0) {
                    return i;
                }
                
                c--;
            }
        }
        
        return n;   // in the end of who knows when
    }
    
    private void fetchLockDependenciesInFunction(MapleDeadlockFunction f, MapleDeadlockFunction nf, FunctionPathNode n) {
        List<Integer> fl = functionLocks.get(f).seqAcqLocks;
        for (int a = 0; a < fl.size(); a++) {
            Integer i = fl.get(a);  // lockId
            
            if (i > 0) {
                int j = fetchUnlockIndex(fl, i, a, n.seqAcqLocks.size()), h = 0;
                for (h = 0; h < Math.min(n.seqAcqLocks.size(), j); h++) {
                    Integer k = n.seqAcqLocks.get(h);
                    if (k > 0) {
                        Set<FunctionLockElement> locks = lockDependencies.get(i);
                        if (locks == null) {
                            locks = new HashSet<>();
                            lockDependencies.put(k, locks);
                        }

                        locks.add(new FunctionLockElement(k, nf));
                    }
                }
            }
        }
    }
    
    private void fetchLockDependencies() {
        Set<MapleDeadlockFunction> r = new HashSet<>();
        
        for (Entry<MapleDeadlockFunction, FunctionPathNode> e : functionLocks.entrySet()) {
            MapleDeadlockFunction ek = e.getKey();
            r.add(ek);
            
            for (Entry<MapleDeadlockFunction, FunctionPathNode> f : functionLocks.entrySet()) {
                if (!r.contains(ek)) {
                    fetchLockDependenciesInFunction(ek, f.getKey(), f.getValue());
                }
            }
        }
    }
    
    private static boolean containsLock(Integer i, Set<FunctionLockElement> es) {
        for (FunctionLockElement e : es) {
            if (i.equals(e.lockId)) return true;
        }
        
        return false;
    }
    
    private static boolean detectDeadlocksInLockSequence(Integer i1, Set<FunctionLockElement> e1, Integer i2, Set<FunctionLockElement> e2) {
        boolean v1 = containsLock(i2, e1), v2 = containsLock(i1, e2);
        return v1 && v2;
    }
    
    private static void makeRemissiveIndexLockFunctions() {
        for (Integer k : lockDependencies.keySet()) {
            lockFunctions.put(k, new HashSet<>(5));
        }
        
        for (Entry<MapleDeadlockFunction, FunctionPathNode> e : functionLocks.entrySet()) {
            for (Integer i : e.getValue().seqAcqLocks) {
                if (i > 0) lockFunctions.get(i).add(e.getKey());
            }
        }
    }
    
    private void detectDeadlocksInLockDependencies() {
        Set<Integer> r = new HashSet<>();
        
        for (Entry<Integer, Set<FunctionLockElement>> e : lockDependencies.entrySet()) {
            Integer ek = e.getKey();
            r.add(ek);
            
            for (Entry<Integer, Set<FunctionLockElement>> f : lockDependencies.entrySet()) {
                if (!r.contains(ek)) {
                    if (detectDeadlocksInLockSequence(ek, e.getValue(), f.getKey(), f.getValue())) {
                        for (MapleDeadlockFunction m : lockFunctions.get(ek)) {
                            for (MapleDeadlockFunction n : lockFunctions.get(f.getKey())) {
                                deadlocks.add(new MapleDeadlockItem(ek, f.getKey(), m, n));
                            }
                        }
                    }
                }
            }
        }
    }
    
    private void detectDeadlocks() {
        fetchLockDependencies();
        makeRemissiveIndexLockFunctions();
        detectDeadlocksInLockDependencies();
    }
    
    public void runSourceGraph(MapleDeadlockGraph graph) {
        makeRemissiveIndexFunctions(graph);
        findFunctionLocks(graph);
        detectDeadlocks();
    }
}
