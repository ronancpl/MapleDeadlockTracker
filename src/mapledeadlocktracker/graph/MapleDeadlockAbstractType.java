/*
    This file is part of the MapleQuestAdvisor planning tool
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
public enum MapleDeadlockAbstractType {
    NON_ABSTRACT(-1),
    SET(0),
    MAP(1),
    LIST(2),
    STACK(3),
    STRING(4),
    LOCK(5),
    PRIORITYQUEUE(6),
    REFERENCE(7),
    OTHER(20);

    private final int i;

    private MapleDeadlockAbstractType(int val) {
        this.i = val;
    }

    public int getValue() {
        return i;
    }
}
