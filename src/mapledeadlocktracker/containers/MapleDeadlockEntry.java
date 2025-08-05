/*
    This file is part of the MapleQuestAdvisor planning tool
    Copyleft (L) 2025 RonanLana

    GNU General Public License v3.0

    Permissions of this strong copyleft license are conditioned on making available complete
    source code of licensed works and modifications, which include larger works using a licensed
    work, under the same license. Copyright and license notices must be preserved. Contributors
    provide an express grant of patent rights.
*/
package mapledeadlocktracker.containers;

/**
 *
 * @author Ronan Lana
 */
public class MapleDeadlockEntry {
        
    Integer lockId1;
    Integer lockId2;
    MapleDeadlockFunction f1;
    MapleDeadlockFunction f2;

    public MapleDeadlockEntry(Integer lockId1, Integer lockId2, MapleDeadlockFunction f1, MapleDeadlockFunction f2) {
        this.lockId1 = lockId1;
        this.lockId2 = lockId2;
        this.f1 = f1;
        this.f2 = f2;
    }
    
    public Integer getLockId1() {
        return this.lockId1;
    }
    
    public Integer getLockId2() {
        return this.lockId2;
    }
    
    public MapleDeadlockFunction getFunction1() {
        return this.f1;
    }
    
    public MapleDeadlockFunction getFunction2() {
        return this.f2;
    }

}
