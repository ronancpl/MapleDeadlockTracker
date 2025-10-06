/*
    This file is part of the DeadlockTracker detection tool
    Copyleft (L) 2025 RonanLana

    GNU General Public License v3.0

    Permissions of this strong copyleft license are conditioned on making available complete
    source code of licensed works and modifications, which include larger works using a licensed
    work, under the same license. Copyright and license notices must be preserved. Contributors
    provide an express grant of patent rights.
 */
package mapledeadlocktracker.graph;

import java.util.LinkedList;
import java.util.List;

/**
 *
 * @author RonanLana
 */
public class MapleDeadlockGraphEntry {
	List<MapleDeadlockGraphNode> points = new LinkedList<>();

	public MapleDeadlockGraphEntry() {}

	public MapleDeadlockGraphEntry(MapleDeadlockGraphNode entry) {
		points.add(entry);
	}

	public void addGraphEntryPoint(MapleDeadlockGraphNode entry) {
		points.add(entry);
	}

	public List<MapleDeadlockGraphNode> getGraphEntryPoints() {
		return points;
	}

}
