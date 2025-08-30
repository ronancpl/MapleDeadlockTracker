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

import deadlocktracker.strings.IgnoredTypes;
import deadlocktracker.strings.LinkedTypes;

/**
 *
 * @author RonanLana
 */
public enum DeadlockAbstractType {
    NON_ABSTRACT(-1),
    SET(0),
    MAP(1),
    LIST(2),
    STACK(3),
    STRING(4),
    LOCK(5),
    PRIORITYQUEUE(6),
    REFERENCE(7),
    SCRIPT(19),
    OTHER(20);

    private final int i;

    private DeadlockAbstractType(int val) {
        this.i = val;
    }

    public int getValue() {
        return i;
    }
    
    public static DeadlockAbstractType getValue(String typeName) {
        /*
        System.out.print("testing ABST " + typeName + " ");
        String t = typeName.split("<", 1)[0];
        */
        
        int idx = typeName.lastIndexOf('.');
        if(idx > -1) typeName = typeName.substring(idx + 1);  // removing the package part of the type declaration
        typeName = LinkedTypes.getLinkedType(typeName);
        
        //System.out.print("goingfor " + t + " ");
        
        switch(typeName) {
            case "Collection":
            case "LinkedHashSet":
            case "HashSet":
            case "Set":
                //System.out.println(DeadlockAbstractType.SET);
                return DeadlockAbstractType.SET;

            case "LinkedList":
            case "ArrayList":
            case "List":
                //System.out.println(DeadlockAbstractType.LIST);
                return DeadlockAbstractType.LIST;

            case "LinkedHashMap":
            case "HashMap":
            case "EnumMap":
            case "Map":
                //System.out.println(DeadlockAbstractType.MAP);
                return DeadlockAbstractType.MAP;
            
            case "SyncLock":
            case "ReentrantReadWriteLock":
            case "ReentrantLock":
            case "ReadWriteLock":
            case "ReadLock":
            case "WriteLock":
            case "Lock":
                //System.out.println(DeadlockAbstractType.LOCK);
                return DeadlockAbstractType.LOCK;
            
            case "PriorityQueue":
                //System.out.println(DeadlockAbstractType.PRIORITYQUEUE);
                return DeadlockAbstractType.PRIORITYQUEUE;
                
            case "WeakReference":
            case "Reference":
            case "Iterator":
            case "Iterable":
            case "Comparable":
                //System.out.println(DeadlockAbstractType.REFERENCE);
                return DeadlockAbstractType.REFERENCE;
                
            case "StringBuffer":
            case "StringBuilder":
            case "String":
                return DeadlockAbstractType.STRING;
                
            case "Invocable":
                return DeadlockAbstractType.SCRIPT;
                
            default:
                if(IgnoredTypes.isDataTypeIgnored(typeName)) {
                    return DeadlockAbstractType.OTHER;
                }
                
                //System.out.println(DeadlockAbstractType.NON_ABSTRACT);
                return DeadlockAbstractType.NON_ABSTRACT;
        }
    }
}
