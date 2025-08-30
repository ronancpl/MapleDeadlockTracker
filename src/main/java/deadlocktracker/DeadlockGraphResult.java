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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import deadlocktracker.containers.DeadlockEntry;
import deadlocktracker.containers.DeadlockFunction;
import deadlocktracker.containers.DeadlockStorage;

/**
 *
 * @author RonanLana
 */
public class DeadlockGraphResult {
    
    private static List<DeadlockEntry> sortDeadlockEntries(Set<DeadlockEntry> deadlocksSet) {
        List<DeadlockEntry> deadlocks = new ArrayList<>(deadlocksSet);
        
        Collections.sort(deadlocks, new Comparator<DeadlockEntry>() {
            @Override
            public int compare(DeadlockEntry e1, DeadlockEntry e2) {
                return ((e1.getFunction1().getId() < e2.getFunction1().getId()) ? -1 : ((e1.getFunction1().getId() == e2.getFunction1().getId()) ? ((e1.getFunction2().getId() < e2.getFunction2().getId()) ? -1 : ((e1.getFunction2().getId() == e2.getFunction2().getId()) ? 0 : 1)) : 1));
            }
        });
        
        return deadlocks;
    }
    
    public static void reportDeadlocks(Set<DeadlockEntry> deadlocksSet, Map<Integer, String> LockNames) {
        List<DeadlockEntry> deadlocks = sortDeadlockEntries(deadlocksSet);
        Set<Integer> locks = new HashSet<>();
        for (DeadlockEntry e : deadlocks) {
            locks.add(e.getLockId1());
            locks.add(e.getLockId2());
        }
        
        System.out.println("List of deadlocks:");
        for (Integer i : locks) {
            System.out.println(LockNames.get(i));
        }
    }
    
}
