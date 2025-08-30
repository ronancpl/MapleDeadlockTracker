/*
    This file is part of the DeadlockTracker detection tool
    Copyleft (L) 2025 RonanLana

    GNU General Public License v3.0

    Permissions of this strong copyleft license are conditioned on making available complete
    source code of licensed works and modifications, which include larger works using a licensed
    work, under the same license. Copyright and license notices must be preserved. Contributors
    provide an express grant of patent rights.
*/
package deadlocktracker;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import deadlocktracker.containers.DeadlockEntry;
import deadlocktracker.containers.DeadlockFunction;
import deadlocktracker.containers.DeadlockStorage;
import deadlocktracker.containers.Pair;
import deadlocktracker.graph.DeadlockGraphEntry;
import deadlocktracker.graph.DeadlockGraphMethod;
import deadlocktracker.graph.DeadlockGraphNode;

/**
 *
 * @author RonanLana
 */
public class DeadlockGraphCruiser {
    
    private final static DeadlockGraphCruiser instance = new DeadlockGraphCruiser();
    
    private class FunctionPathNode {
        
        Set<Integer> acquiredLocks = new HashSet<>();
        List<Integer> seqLocks = new LinkedList<>();
        List<Integer> seqAcqLocks = new ArrayList<>();
        
    }
    
    private class FunctionLockElement {
        
        Integer lockId;
        DeadlockFunction function;
        Integer count;
        
        private FunctionLockElement(Integer lockId, DeadlockFunction function) {
            this.lockId = lockId;
            this.function = function;
            this.count = 1;
        }
        
        protected void increment() {
            this.count += 1;
        }
        
        protected boolean decrementAndZero() {
            this.count -= 1;
            return this.count <= 0;
        }
        
    }
    
    static Set<DeadlockEntry> deadlocks = new HashSet<>();
    
    static Stack<DeadlockFunction> functionStack = new Stack<>();
    static Map<DeadlockFunction, Integer> functionMap = new HashMap<>();
    static Map<DeadlockFunction, Set<Integer>> functionMilestones = new HashMap<>();
    
    static Map<DeadlockFunction, FunctionPathNode> functionLocks = new HashMap<>();
    static Map<Integer, Set<DeadlockFunction>> lockFunctions = new HashMap<>();
    
    static Map<Integer, DeadlockFunction> functions = new HashMap<>();
    static Map<Integer, Map<Integer, FunctionLockElement>> lockDependencies = new HashMap<>();
    
    private static Pattern p = Pattern.compile("([\\w\\d_\\.]*)(\\[([\\w\\d_\\.]*)\\])?");
    private static List<Pair<String,String>> startingMethods = startingMethods(DeadlockConfig.getProperty("entry_points"));
    
    private static List<Pair<String,String>> startingMethods(String methodSeq) {
        List<Pair<String,String>> list = new LinkedList<>();
        Matcher m = p.matcher(methodSeq);
        while (m.find()) {
            if (m.groupCount() >= 3 && m.group(2) != null) {
                list.add(new Pair<>(m.group(1), m.group(3)));
            } else if (!m.group(1).isEmpty()) {
                list.add(new Pair<>(m.group(1), null));
            }
        }
        
        return list;
    }
    
    private static boolean isStartingFunction(DeadlockFunction f) {
        String fName = f.getName();
        String cName = DeadlockStorage.getCanonClassName(f.getSourceClass());
        
        for (Pair<String,String> p : startingMethods) {
            if ((fName.contentEquals(p.left) || p.left.isEmpty()) && (p.right == null || cName.contentEquals(p.right))) {
                return true;
            }
        }
        
        return false;
    }
    
    private static void commitFunctionAcquiredLocks(DeadlockFunction f, FunctionPathNode trace, FunctionPathNode uptrace) {
        Set<Integer> ongoingLocks = trace.acquiredLocks;
        Set<Integer> locks = functionLocks.get(f).acquiredLocks;
        List<Integer> acqLocks = functionLocks.get(f).seqAcqLocks;

        if (acqLocks.size() < trace.seqAcqLocks.size()) {
            locks.clear();
            locks.addAll(ongoingLocks);

            acqLocks.clear();
            acqLocks.addAll(trace.seqAcqLocks);
        }
        
        List<Integer> list = new ArrayList<>(trace.seqLocks);
        List<Integer> toRemove = new LinkedList<>();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) <= 0) {
                toRemove.add(i);
            }
        }
        for (int i = toRemove.size() - 1; i >= 0; i--) {
            list.remove(toRemove.get(i));
        }
        
        uptrace.seqAcqLocks = list;
        uptrace.seqLocks = trace.seqLocks.subList(0, Math.min(uptrace.seqLocks.size(), trace.seqLocks.size()));
    }
    
    private static void sourceGraphFunctionLock(int lockId, FunctionPathNode ongoingLocks) {
        ongoingLocks.seqLocks.add(lockId);
        ongoingLocks.seqAcqLocks.add(lockId);
        ongoingLocks.acquiredLocks.add(lockId);
    }
    
    private static void sourceGraphFunctionUnlock(int lockId, FunctionPathNode ongoingLocks) {
        List<Integer> list = ongoingLocks.seqLocks;
        int idx = list.lastIndexOf(lockId);
        if (idx > -1) {
            list.remove(idx);
            ongoingLocks.seqAcqLocks.add(-lockId); // represents unlock in a lane graph
        }
    }
    
    private static void sourceGraphFunctionScript(FunctionPathNode ongoingLocks) {
        if (!ongoingLocks.acquiredLocks.isEmpty()) {
            String s = "";
            
            ListIterator<DeadlockFunction> i = functionStack.listIterator();
            while (i.hasNext()) {
                DeadlockFunction f = i.next();
                s += f.getName() + ",";
            }
            
            System.out.println("[WARNING] " + s.substring(0, s.length() - 1) + " has acquired lock count: " + ongoingLocks.acquiredLocks.size());
        }
    }
    
    private static void insertGraphFunction(DeadlockFunction f) {
        Integer i = functionMap.get(f);
        if (i == null) functionMap.put(f, 1);
        else functionMap.put(f, i + 1);
    }
    
    private static void removeGraphFunction(DeadlockFunction f) {
        Integer i = functionMap.get(f);
        if (i == 1) functionMap.remove(f);
        else functionMap.put(f, i - 1);
    }
    
    private void runSourceGraphFunction(DeadlockFunction f, Map<DeadlockFunction, DeadlockGraphMethod> g, FunctionPathNode uptrace) {
        Set<Integer> s = functionMilestones.get(f);
        int size = s.size();
        s.addAll(uptrace.acquiredLocks);
        
        if (s.size() > size || s.size() == 0) {
            int curBaseIdx = functionStack.size();
            functionStack.add(f);
            
            while (functionStack.size() > curBaseIdx) {
                DeadlockFunction mdf = functionStack.pop();
                if (!functionMap.containsKey(mdf)) {
                    functionStack.add(mdf);
                    insertGraphFunction(mdf);

                    FunctionPathNode ftrace = new FunctionPathNode();
                    ftrace.seqLocks.addAll(uptrace.seqLocks);
                    
                    for (DeadlockGraphEntry e : g.get(mdf).getEntryList()) {
                        for (DeadlockGraphNode n : e.getGraphEntryPoints()) {
                            switch (n.getType()) {
                                case CALL:
                                    runSourceGraphFunction(functions.get(n.getValue()), g, ftrace);
                                    break;

                                case LOCK:
                                    sourceGraphFunctionLock(n.getLockId(), ftrace);
                                    break;

                                case UNLOCK:
                                    sourceGraphFunctionUnlock(n.getLockId(), ftrace);
                                    break;
                                    
                                case SCRIPT:
                                    sourceGraphFunctionScript(ftrace);
                                    break;
                            }
                        }
                    }

                    commitFunctionAcquiredLocks(mdf, ftrace, uptrace);
                    removeGraphFunction(mdf);
                    functionStack.pop();
                }
            }
        }
    }
    
    private static void prepareFunctionMilestones() {
        for (Entry<Integer, DeadlockFunction> f : functions.entrySet()) {
            functionMilestones.put(f.getValue(), new HashSet<>(5));
        }
    }
    
    public static void makeRemissiveIndexFunctions(DeadlockGraph graph) {
        for (Entry<DeadlockFunction, Integer> e : graph.getFunctionIds().entrySet()) {
            functions.put(e.getValue(), e.getKey());
        }
    }
    
    private void findFunctionLocks(DeadlockGraph graph) {
        prepareFunctionMilestones();
        
        Map<DeadlockFunction, DeadlockGraphMethod> functionGraph = graph.getFunctionGraph();
        for (Entry<DeadlockFunction, DeadlockGraphMethod> e : functionGraph.entrySet()) {
            DeadlockFunction f = e.getKey();
            if (isStartingFunction(f)) {
                //System.out.println("Reading " + DeadlockStorage.getCanonClassName(f.getSourceClass()) + " >> " + f.getName());
                FunctionPathNode trace = new FunctionPathNode();
                runSourceGraphFunction(f, functionGraph, trace);
            }
        }
    }
    
    private static int fetchUnlockIndex(List<Integer> fl, int lockId, int idx, int n) {
        for (int i = idx; i < fl.size(); i++) {
            if (fl.get(i) == -lockId) {
                return i;
            }
        }
        
        return n;   // in the end of who knows when
    }
    
    private void incrementLockInFunction(Integer i, Integer k, DeadlockFunction nf) {
        Map<Integer, FunctionLockElement> locks = lockDependencies.get(i);
        if (locks == null) {
            locks = new HashMap<>();
            lockDependencies.put(i, locks);
        }
        
        if (locks.containsKey(k)) {
            locks.get(k).increment();
        } else {
            locks.put(k, new FunctionLockElement(k, nf));
        }
    }
    
    private void decrementLockInFunction(Integer i, Integer k) {
        Map<Integer, FunctionLockElement> locks = lockDependencies.get(i);
        if (locks != null && locks.get(-k) != null) {
            if (locks.get(-k).decrementAndZero()) {
                locks.remove(-k);
                if (locks.isEmpty()) {
                    lockDependencies.remove(i);
                }
            }
        }
    }
    
    private void fetchLockDependenciesInFunction(DeadlockFunction f, DeadlockFunction nf, FunctionPathNode n) {
        List<Integer> fl = functionLocks.get(f).seqAcqLocks;
        for (int a = 0; a < fl.size(); a++) {
            Integer i = fl.get(a);  // lockId
            if (i > 0) {
                incrementLockInFunction(i, i, nf);
                
                int j = fetchUnlockIndex(fl, i, a + 1, fl.size());
                for (int h = a + 1; h < j; h++) {
                    Integer k = fl.get(h);
                    if (k > 0) {
                        int c = fetchUnlockIndex(fl, k, h, fl.size());
                        if (c > j) {
                            for (int m = h; m < j; m++) {
                                Integer g = fl.get(m);
                                if (g > 0) {
                                    incrementLockInFunction(i, g, nf);
                                } else if (g < 0) {
                                    decrementLockInFunction(i, g);
                                }
                            }
                        }
                    }
                }
            } else if (i < 0) {
                decrementLockInFunction(-i, -i);
            }
        }
    }
    
    private void fetchLockDependencies() {
        Set<DeadlockFunction> r = new HashSet<>();
        
        for (Entry<DeadlockFunction, FunctionPathNode> e : functionLocks.entrySet()) {
            DeadlockFunction ek = e.getKey();
            r.add(ek);
            
            for (Entry<DeadlockFunction, FunctionPathNode> f : functionLocks.entrySet()) {
                if (!r.contains(f.getKey())) {
                    fetchLockDependenciesInFunction(ek, f.getKey(), f.getValue());
                }
            }
        }
    }
    
    private static boolean containsLock(Integer i, Collection<FunctionLockElement> es) {
        for (FunctionLockElement e : es) {
            if (i.equals(e.lockId)) return true;
        }
        
        return false;
    }
    
    private static boolean detectDeadlocksInLockSequence(Integer i1, Collection<FunctionLockElement> e1, Integer i2, Collection<FunctionLockElement> e2) {
        boolean v1 = containsLock(i2, e1), v2 = containsLock(i1, e2);
        return v1 && v2;
    }
    
    private static void makeRemissiveIndexLockFunctions() {
        Set<Integer> acqLocks = new HashSet<>();
        for (FunctionPathNode n : functionLocks.values()) {
            for (Integer l : n.seqAcqLocks) {
                if (l > 0) {
                    acqLocks.add(l);
                }
            }
        }
        
        for (Integer k : acqLocks) {
            lockFunctions.put(k, new HashSet<>(5));
        }
        
        for (Entry<DeadlockFunction, FunctionPathNode> e : functionLocks.entrySet()) {
            for (Integer i : e.getValue().seqAcqLocks) {
                if (i > 0) lockFunctions.get(i).add(e.getKey());
            }
        }
    }
    
    private void detectDeadlocksInLockDependencies() {
        Set<Integer> r = new HashSet<>();
        
        for (Entry<Integer, Map<Integer, FunctionLockElement>> e : lockDependencies.entrySet()) {
            Integer ek = e.getKey();
            r.add(ek);
            
            for (Entry<Integer, Map<Integer, FunctionLockElement>> f : lockDependencies.entrySet()) {
                if (!r.contains(f.getKey())) {
                    if (detectDeadlocksInLockSequence(ek, e.getValue().values(), f.getKey(), f.getValue().values())) {
                        for (DeadlockFunction m : lockFunctions.get(ek)) {
                            for (DeadlockFunction n : lockFunctions.get(f.getKey())) {
                                deadlocks.add(new DeadlockEntry(ek, f.getKey(), m, n));
                            }
                        }
                    }
                }
            }
        }
    }
    
    private void detectDeadlocks(Map<Integer, String> LockNames) {
        fetchLockDependencies();
        dumpLockDependency(LockNames);
        makeRemissiveIndexLockFunctions();
        detectDeadlocksInLockDependencies();
    }
    
    private void createFunctionAcquiredLocks(DeadlockGraph graph) {
        for(DeadlockFunction f : graph.getFunctionIds().keySet()) {
            FunctionPathNode ftrace = new FunctionPathNode();
            functionLocks.put(f, ftrace);
        }
    }
    
    private static void dumpLockDependency(Map<Integer, String> LockNames) {
        System.out.println("Lock dependency:");
        for (Entry<Integer, Map<Integer, FunctionLockElement>> e : lockDependencies.entrySet()) {
            String s = LockNames.get(e.getKey()) + " [";
            
            for (FunctionLockElement lockElem : e.getValue().values()) {
                s += LockNames.get(lockElem.lockId) + ", ";
            }
            
            s += "]";
            
            System.out.println(s);
        }
        System.out.println();
    }
    
    public Set<DeadlockEntry> runSourceGraph(DeadlockGraph graph, Map<Integer, String> LockNames) {
        createFunctionAcquiredLocks(graph);
        makeRemissiveIndexFunctions(graph);
        findFunctionLocks(graph);
        
        detectDeadlocks(LockNames);
        return deadlocks;
    }
}
