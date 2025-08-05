/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package mapledeadlocktracker.graph;

/**
 *
 * @author RonanLana
 */
public enum MapleDeadlockGraphNodeType {
    UNDEFINED((byte) -1),
    CALL((byte) 0),
    LOCK((byte) 1),
    UNLOCK((byte) 2),
    END((byte) 3);

    private final byte i;

    private MapleDeadlockGraphNodeType(byte val) {
        this.i = val;
    }

    public int getValue() {
        return i;
    }
}
