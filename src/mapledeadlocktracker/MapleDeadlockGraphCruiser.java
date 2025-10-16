/*
    This file is part of the DeadlockTracker detection tool
    Copyleft (L) 2025 RonanLana

    GNU General Public License v3.0

    Permissions of this strong copyleft license are conditioned on making available complete
    source code of licensed works and modifications, which include larger works using a licensed
    work, under the same license. Copyright and license notices must be preserved. Contributors
    provide an express grant of patent rights.
 */
package mapledeadlocktracker;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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
import mapledeadlocktracker.containers.MapleDeadlockEntry;
import mapledeadlocktracker.containers.MapleDeadlockFunction;
import mapledeadlocktracker.containers.MapleDeadlockStorage;
import mapledeadlocktracker.containers.Pair;
import mapledeadlocktracker.graph.MapleDeadlockGraphEntry;
import mapledeadlocktracker.graph.MapleDeadlockGraphMethod;
import mapledeadlocktracker.graph.MapleDeadlockGraphNode;

/**
 *
 * @author RonanLana
 */
public class MapleDeadlockGraphCruiser {

	private final static MapleDeadlockGraphCruiser instance = new MapleDeadlockGraphCruiser();

	private class FunctionPathNode {

		Set<Integer> acquiredLocks = new HashSet<>();
		List<Integer> seqLocks = new LinkedList<>();
		List<Integer> seqAcqLocks = new ArrayList<>();
                List<Integer> seqIntAcqLocks = Collections.emptyList();
                Integer startAt;

	}

	private class FunctionLockElement {

		Integer lockId;
		MapleDeadlockFunction function;
		Integer count;

		private FunctionLockElement(Integer lockId, MapleDeadlockFunction function) {
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

	static Set<MapleDeadlockEntry> deadlocks = new HashSet<>();

	static Stack<MapleDeadlockFunction> functionStack = new Stack<>();
	static Map<MapleDeadlockFunction, Integer> functionMap = new HashMap<>();
	static Map<MapleDeadlockFunction, Set<Integer>> functionMilestones = new HashMap<>();

	static Map<MapleDeadlockFunction, FunctionPathNode> functionLocks = new HashMap<>();
	static Map<Integer, Set<MapleDeadlockFunction>> lockFunctions = new HashMap<>();

	static Map<Integer, MapleDeadlockFunction> functions = new HashMap<>();
	static Map<Integer, Map<Integer, FunctionLockElement>> lockDependencies = new HashMap<>();

	private static Pattern p = Pattern.compile("([\\w\\d_\\.]*)(\\[([\\w\\d_\\.]*)\\])?");
	private static List<Pair<String,String>> startingMethods = startingMethods(MapleDeadlockConfig.getProperty("entry_points"));

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

	private static boolean isStartingFunction(MapleDeadlockFunction f) {
		String fName = f.getName();
		String cName = MapleDeadlockStorage.getCanonClassName(f.getSourceClass());

		for (Pair<String,String> p : startingMethods) {
			if ((fName.contentEquals(p.left) || p.left.isEmpty()) && (p.right == null || cName.contentEquals(p.right))) {
				return true;
			}
		}

		return false;
	}

	private static void commitFunctionAcquiredLocks(MapleDeadlockFunction f, FunctionPathNode trace, FunctionPathNode uptrace) {
		Set<Integer> ongoingLocks = trace.acquiredLocks;
		Set<Integer> locks = functionLocks.get(f).acquiredLocks;
		List<Integer> acqLocks = functionLocks.get(f).seqAcqLocks;

		if (acqLocks.size() < trace.seqAcqLocks.size()) {
			locks.clear();
			locks.addAll(ongoingLocks);

			acqLocks.clear();
			acqLocks.addAll(trace.seqAcqLocks);
                        
                        if (uptrace.startAt < acqLocks.size()) {
                                functionLocks.get(f).seqIntAcqLocks = acqLocks.subList(uptrace.startAt, acqLocks.size());
                        } else {
                                functionLocks.get(f).seqIntAcqLocks = Collections.emptyList();
                        }
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

			ListIterator<MapleDeadlockFunction> i = functionStack.listIterator();
			while (i.hasNext()) {
				MapleDeadlockFunction f = i.next();
				s += f.getName() + ",";
			}

			System.out.println("[WARNING] " + s.substring(0, s.length() - 1) + " has acquired lock count: " + ongoingLocks.acquiredLocks.size());
		}
	}

	private static void insertGraphFunction(MapleDeadlockFunction f) {
		Integer i = functionMap.get(f);
		if (i == null) functionMap.put(f, 1);
		else functionMap.put(f, i + 1);
	}

	private static void removeGraphFunction(MapleDeadlockFunction f) {
		Integer i = functionMap.get(f);
		if (i == 1) functionMap.remove(f);
		else functionMap.put(f, i - 1);
	}

	private void runSourceGraphFunction(MapleDeadlockFunction f, Map<MapleDeadlockFunction, MapleDeadlockGraphMethod> g, FunctionPathNode uptrace) {
		Set<Integer> s = functionMilestones.get(f);
		int size = s.size();
		s.addAll(uptrace.acquiredLocks);

		if (s.size() > size || s.size() == 0) {
			int curBaseIdx = functionStack.size();
			functionStack.add(f);

			while (functionStack.size() > curBaseIdx) {
				MapleDeadlockFunction mdf = functionStack.pop();
				if (!functionMap.containsKey(mdf)) {
					functionStack.add(mdf);
					insertGraphFunction(mdf);

                                        
                                        FunctionPathNode ftrace = new FunctionPathNode();
					ftrace.seqLocks.addAll(uptrace.seqLocks);
                                        ftrace.startAt = ftrace.seqLocks.size();

					for (MapleDeadlockGraphEntry e : g.get(mdf).getEntryList()) {
						for (MapleDeadlockGraphNode n : e.getGraphEntryPoints()) {
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
		for (Entry<Integer, MapleDeadlockFunction> f : functions.entrySet()) {
			functionMilestones.put(f.getValue(), new HashSet<>(5));
		}
	}

	public static void makeRemissiveIndexFunctions(MapleDeadlockGraph graph) {
		for (Entry<MapleDeadlockFunction, Integer> e : graph.getFunctionIds().entrySet()) {
			functions.put(e.getValue(), e.getKey());
		}
	}

	private void findFunctionLocks(MapleDeadlockGraph graph, MapleDeadlockStorage storage) {
		prepareFunctionMilestones();

		Map<MapleDeadlockFunction, MapleDeadlockGraphMethod> functionGraph = graph.getFunctionGraph();
                Set<MapleDeadlockFunction> runMethods = storage.getRunnableMethods();
                
		for (Entry<MapleDeadlockFunction, MapleDeadlockGraphMethod> e : functionGraph.entrySet()) {
			MapleDeadlockFunction f = e.getKey();
                        if (isStartingFunction(f) || runMethods.contains(f)) {
				//System.out.println("Reading " + MapleDeadlockStorage.getCanonClassName(f.getSourceClass()) + " >> " + f.getName());
				FunctionPathNode trace = new FunctionPathNode();
                                trace.startAt = 0;
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

	private void incrementLockInFunction(Integer i, Integer k, MapleDeadlockFunction nf) {
		if(functionMilestones.get(nf).contains(k)) return;
            
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

	private void fetchLockDependenciesInFunction(MapleDeadlockFunction f, MapleDeadlockFunction nf, FunctionPathNode n) {
		List<Integer> fl = functionLocks.get(f).seqIntAcqLocks;
                Set<Integer> locks = new HashSet<>();
		for (int a = functionLocks.get(f).startAt; a < fl.size(); a++) {
			Integer i = fl.get(a);  // lockId
			if (i > 0) {
                                incrementLockInFunction(i, i, nf);
                                
                                int j = fetchUnlockIndex(fl, i, a + 1, fl.size());
                                for (int h = a + 1; h < j; h++) {
                                        Integer k = fl.get(h);
                                        if (k > 0 && !locks.contains(k)) {
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
                                
                                locks.add(i);
			} else if (i < 0) {
				decrementLockInFunction(-i, -i);
			}
		}
	}
        
	private void fetchLockDependencies() {
		Set<MapleDeadlockFunction> r = new HashSet<>();

		for (Entry<MapleDeadlockFunction, FunctionPathNode> e : functionLocks.entrySet()) {
			MapleDeadlockFunction ek = e.getKey();
			r.add(ek);

			for (Entry<MapleDeadlockFunction, FunctionPathNode> f : functionLocks.entrySet()) {
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
			for (Integer l : n.seqIntAcqLocks) {
				if (l > 0) {
					acqLocks.add(l);
				}
			}
		}

		for (Integer k : acqLocks) {
			lockFunctions.put(k, new HashSet<>(5));
		}

		for (Entry<MapleDeadlockFunction, FunctionPathNode> e : functionLocks.entrySet()) {
			for (Integer i : e.getValue().seqIntAcqLocks) {
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
						for (MapleDeadlockFunction m : lockFunctions.get(ek)) {
							for (MapleDeadlockFunction n : lockFunctions.get(f.getKey())) {
								deadlocks.add(new MapleDeadlockEntry(ek, f.getKey(), m, n));
							}
						}
					}
				}
			}
		}
	}

	private void detectDeadlocks(Map<Integer, String> mapleLockNames) {
		fetchLockDependencies();
		dumpLockDependency(mapleLockNames);
		makeRemissiveIndexLockFunctions();
		detectDeadlocksInLockDependencies();
                dumpDeadlockFunctions(mapleLockNames);
	}

	private void createFunctionAcquiredLocks(MapleDeadlockGraph graph) {
		for(MapleDeadlockFunction f : graph.getFunctionIds().keySet()) {
			FunctionPathNode ftrace = new FunctionPathNode();
                        ftrace.startAt = 0;
			functionLocks.put(f, ftrace);
		}
	}

	private static void dumpLockDependency(Map<Integer, String> mapleLockNames) {
		System.out.println("Lock dependency:");
		for (Entry<Integer, Map<Integer, FunctionLockElement>> e : lockDependencies.entrySet()) {
			String s = mapleLockNames.get(e.getKey()) + " [";

			for (FunctionLockElement lockElem : e.getValue().values()) {
				s += mapleLockNames.get(lockElem.lockId) + ", ";
			}

			s += "]";

			System.out.println(s);
		}
		System.out.println();
	}
        
        private static void dumpDeadlockFunctions(Map<Integer, String> mapleLockNames) {
                System.out.println();
                
                Set<Integer> locks = new HashSet<>();
                for (MapleDeadlockEntry e : deadlocks) {
                        locks.add(e.getLockId1());
                        locks.add(e.getLockId2());
                }
                
                System.out.println("Dead Lock ids:");
                for (Integer i : locks) {
                        System.out.println(mapleLockNames.get(i) + ":" + i);
                }
                System.out.println();
                
                System.out.println("Function acquired Locks:");
                Set<MapleDeadlockFunction> functions = new HashSet<>();
		for (Entry<MapleDeadlockFunction, FunctionPathNode> e : functionLocks.entrySet()) {
                        for (Integer i : locks) {
                                if (e.getValue().acquiredLocks.contains(i)) {
                                        functions.add(e.getKey());
                                }
                        }
		}
                
                for (MapleDeadlockFunction f : functions) {
                        System.out.println(f + " : " + functionLocks.get(f).seqIntAcqLocks + "," + functionMilestones.get(f));
                }
	}

	public Set<MapleDeadlockEntry> runSourceGraph(MapleDeadlockGraph graph, MapleDeadlockStorage storage, Map<Integer, String> mapleLockNames) {
		createFunctionAcquiredLocks(graph);
		makeRemissiveIndexFunctions(graph);
		findFunctionLocks(graph, storage);

		detectDeadlocks(mapleLockNames);
		return deadlocks;
	}
}
