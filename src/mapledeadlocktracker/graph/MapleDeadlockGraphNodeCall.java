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

/**
 *
 * @author RonanLana
 */
public class MapleDeadlockGraphNodeCall extends MapleDeadlockGraphNode {
	Integer callFid;

	public MapleDeadlockGraphNodeCall(Integer fid) {
		super(fid, MapleDeadlockGraphNodeType.CALL, -1);
		callFid = fid;
	}

	@Override
	public String toString() {
		return "C" + callFid;
	}
}
