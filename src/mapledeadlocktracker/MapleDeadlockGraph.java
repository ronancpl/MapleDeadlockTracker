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
