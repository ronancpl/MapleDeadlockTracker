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
public enum DeadlockGraphNodeType {
    UNDEFINED((byte) -1),
    CALL((byte) 0),
    LOCK((byte) 1),
    UNLOCK((byte) 2),
    SCRIPT((byte) 3),
    END((byte) 4);

    private final byte i;

    private DeadlockGraphNodeType(byte val) {
        this.i = val;
    }

    public int getValue() {
        return i;
    }
}
