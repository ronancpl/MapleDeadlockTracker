/*
    This file is part of the MapleDeadlockTracker detection tool
    Copyleft (L) 2025 RonanLana

    GNU General Public License v3.0

    Permissions of this strong copyleft license are conditioned on making available complete
    source code of licensed works and modifications, which include larger works using a licensed
    work, under the same license. Copyright and license notices must be preserved. Contributors
    provide an express grant of patent rights.
*/
package mapledeadlocktracker.graph;

import mapledeadlocktracker.strings.MapleIgnoredTypes;

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
    
    public static MapleDeadlockAbstractType getValue(String typeName) {
        /*
        System.out.print("testing ABST " + typeName + " ");
        String t = typeName.split("<", 1)[0];

        int idx = t.lastIndexOf('.');
        if(idx > -1) t = t.substring(idx + 1);  // removing the package part of the type declaration
        */

        //System.out.print("goingfor " + t + " ");
        
        switch(typeName) {
            case "Collection":
            case "LinkedHashSet":
            case "HashSet":
            case "Set":
                //System.out.println(MapleDeadlockAbstractType.SET);
                return MapleDeadlockAbstractType.SET;

            case "LinkedList":
            case "ArrayList":
            case "List":
                //System.out.println(MapleDeadlockAbstractType.LIST);
                return MapleDeadlockAbstractType.LIST;

            case "LinkedHashMap":
            case "HashMap":
            case "EnumMap":
            case "Map":
                //System.out.println(MapleDeadlockAbstractType.MAP);
                return MapleDeadlockAbstractType.MAP;
            
            case "SyncLock":
            case "ReentrantReadWriteLock":
            case "ReentrantLock":
            case "ReadWriteLock":
            case "ReadLock":
            case "WriteLock":
            case "Lock":
                //System.out.println(MapleDeadlockAbstractType.LOCK);
                return MapleDeadlockAbstractType.LOCK;
            
            case "PriorityQueue":
                //System.out.println(MapleDeadlockAbstractType.PRIORITYQUEUE);
                return MapleDeadlockAbstractType.PRIORITYQUEUE;
                
            case "WeakReference":
            case "Reference":
            case "Iterator":
            case "Iterable":
            case "Comparable":
                //System.out.println(MapleDeadlockAbstractType.REFERENCE);
                return MapleDeadlockAbstractType.REFERENCE;
                
            case "StringBuffer":
            case "StringBuilder":
            case "String":
                return MapleDeadlockAbstractType.STRING;
                
            default:
                if(MapleIgnoredTypes.isDataTypeIgnored(typeName)) {
                    return MapleDeadlockAbstractType.OTHER;
                }
                
                //System.out.println(MapleDeadlockAbstractType.NON_ABSTRACT);
                return MapleDeadlockAbstractType.NON_ABSTRACT;
        }
    }
}
