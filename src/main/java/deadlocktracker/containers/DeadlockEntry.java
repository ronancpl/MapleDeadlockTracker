/*
    This file is part of the DeadlockTracker detection tool
    Copyleft (L) 2025 RonanLana

    GNU General Public License v3.0

    Permissions of this strong copyleft license are conditioned on making available complete
    source code of licensed works and modifications, which include larger works using a licensed
    work, under the same license. Copyright and license notices must be preserved. Contributors
    provide an express grant of patent rights.
*/
package deadlocktracker.containers;

/**
 *
 * @author RonanLana
 */
public class DeadlockEntry {
        
    Integer lockId1;
    Integer lockId2;
    DeadlockFunction f1;
    DeadlockFunction f2;

    public DeadlockEntry(Integer lockId1, Integer lockId2, DeadlockFunction f1, DeadlockFunction f2) {
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
    
    public DeadlockFunction getFunction1() {
        return this.f1;
    }
    
    public DeadlockFunction getFunction2() {
        return this.f2;
    }

}
