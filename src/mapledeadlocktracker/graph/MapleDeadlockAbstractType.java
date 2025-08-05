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
