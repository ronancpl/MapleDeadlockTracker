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

import java.util.LinkedList;
import java.util.List;

/**
 *
 * @author RonanLana
 */
public class DeadlockGraphMethod {
    String srcName;
    
    int id;
    List<DeadlockGraphEntry> entryNodes = new LinkedList<>();
    
    public DeadlockGraphMethod(int id, String srcName) {
        this.id = id;
        this.srcName = srcName;
    }
    
    public void addGraphEntry(DeadlockGraphEntry e) {
        entryNodes.add(e);
    }
    
    public int getId() {
        return id;
    }
    
    public String getSourceName() {
        return srcName;
    }
    
    public List<DeadlockGraphEntry> getEntryList() {
        return entryNodes;
    }
    
    @Override
    public String toString() {
        return srcName + ":" + entryNodes.size();
    }
}
