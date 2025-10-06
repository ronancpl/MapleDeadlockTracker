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

import mapledeadlocktracker.strings.MapleIgnoredTypes;
import mapledeadlocktracker.strings.MapleLinkedTypes;

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
	SCRIPT(19),
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
		 */

		int idx = typeName.lastIndexOf('.');
		if(idx > -1) typeName = typeName.substring(idx + 1);  // removing the package part of the type declaration
		typeName = MapleLinkedTypes.getLinkedType(typeName);

		//System.out.print("goingfor " + t + " ");

		switch(typeName) {
		case "Collection":
		case "LinkedHashSet":
		case "HashSet":
		case "Set":
			//System.out.println(DeadlockAbstractType.SET);
			return MapleDeadlockAbstractType.SET;

		case "LinkedList":
		case "ArrayList":
		case "List":
			//System.out.println(DeadlockAbstractType.LIST);
			return MapleDeadlockAbstractType.LIST;

		case "LinkedHashMap":
		case "HashMap":
		case "EnumMap":
		case "Map":
			//System.out.println(DeadlockAbstractType.MAP);
			return MapleDeadlockAbstractType.MAP;

		case "SynchLock":
		case "ReentrantReadWriteLock":
		case "ReentrantLock":
		case "ReadWriteLock":
		case "ReadLock":
		case "WriteLock":
		case "Lock":
			//System.out.println(DeadlockAbstractType.LOCK);
			return MapleDeadlockAbstractType.LOCK;

		case "PriorityQueue":
			//System.out.println(DeadlockAbstractType.PRIORITYQUEUE);
			return MapleDeadlockAbstractType.PRIORITYQUEUE;

		case "WeakReference":
		case "Reference":
		case "Iterator":
		case "Iterable":
		case "Comparable":
			//System.out.println(DeadlockAbstractType.REFERENCE);
			return MapleDeadlockAbstractType.REFERENCE;

		case "StringBuffer":
		case "StringBuilder":
		case "String":
			return MapleDeadlockAbstractType.STRING;

		case "Invocable":
			return MapleDeadlockAbstractType.SCRIPT;

		default:
			if(MapleIgnoredTypes.isDataTypeIgnored(typeName)) {
				return MapleDeadlockAbstractType.OTHER;
			}

			//System.out.println(DeadlockAbstractType.NON_ABSTRACT);
			return MapleDeadlockAbstractType.NON_ABSTRACT;
		}
	}
}
