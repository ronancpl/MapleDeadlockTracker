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
public class DeadlockGraphNode {
    int position;
    int lockid;
    DeadlockGraphNodeType type;
    
    protected DeadlockGraphNode(int pos, DeadlockGraphNodeType t, int lockid) {
        this.position = pos;
        this.type = t;
        this.lockid = lockid;
    }
    
    public DeadlockGraphNode() {
        position = -1;
        type = DeadlockGraphNodeType.END;
    }
    
    public DeadlockGraphNodeType getType() {
        return type;
    }
    
    public int getValue() {
        return position;
    }
    
    public int getLockId() {
        return lockid;
    }
    
}
