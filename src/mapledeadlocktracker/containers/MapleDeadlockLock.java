/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package mapledeadlocktracker.containers;

/**
 *
 * @author RonanLana
 */
public class MapleDeadlockLock {
    Integer id;
    String name;
    
    public MapleDeadlockLock(Integer lockId, String lockName) {
        id = lockId;
        name = lockName;
    }
    
    public Integer getId() {
        return id;
    }
    
    @Override
    public String toString() {
        return name;
    }
}
