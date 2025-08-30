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

import java.util.Map;
import deadlocktracker.containers.DeadlockFunction;
import deadlocktracker.graph.DeadlockGraphMethod;

/**
 *
 * @author RonanLana
 */
public class DeadlockGraph {
    
    private Map<DeadlockFunction, Integer> GraphFunctionIds;
    private Map<DeadlockFunction, DeadlockGraphMethod> GraphFunctions;
    
    public DeadlockGraph(Map<DeadlockFunction, Integer> GraphFunctionIds, Map<DeadlockFunction, DeadlockGraphMethod> GraphFunctions) {
        this.GraphFunctionIds = GraphFunctionIds;
        this.GraphFunctions = GraphFunctions;
    }
    
    public Map<DeadlockFunction, Integer> getFunctionIds() {
        return this.GraphFunctionIds;
    }
    
    public Map<DeadlockFunction, DeadlockGraphMethod> getFunctionGraph() {
        return this.GraphFunctions;
    }
    
}
