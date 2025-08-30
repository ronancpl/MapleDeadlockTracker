/*
    This file is part of the DeadlockTracker detection tool
    Copyleft (L) 2025 RonanLana

    GNU General Public License v3.0

    Permissions of this strong copyleft license are conditioned on making available complete
    source code of licensed works and modifications, which include larger works using a licensed
    work, under the same license. Copyright and license notices must be preserved. Contributors
    provide an express grant of patent rights.
*/
package deadlocktracker.graph;

/**
 *
 * @author RonanLana
 */
public class DeadlockGraphNodeLock extends DeadlockGraphNode {
    boolean lock;
    Integer id;
    
    public DeadlockGraphNodeLock(Integer lockid, boolean isLocking) {
        super(-1, isLocking ? DeadlockGraphNodeType.LOCK : DeadlockGraphNodeType.UNLOCK, (lockid != null) ? lockid : -1);
        lock = isLocking;
        id = lockid;
    }
    
    public Integer getLock() {
        return id;
    }
    
    @Override
    public String toString() {
        return (lock ? "L" : "U") + id;
    }
}
