/*
    This file is part of the MapleDeadlockTracker detection tool
    Copyleft (L) 2025 RonanLana

    GNU General Public License v3.0

    Permissions of this strong copyleft license are conditioned on making available complete
    source code of licensed works and modifications, which include larger works using a licensed
    work, under the same license. Copyright and license notices must be preserved. Contributors
    provide an express grant of patent rights.
*/
package mapledeadlocktracker.strings;

import java.util.ArrayList;
import java.util.List;
import mapledeadlocktracker.containers.Pair;

/**
 *
 * @author RonanLana
 */
public class MapleLinkedTypes {
    private static final List<Pair<String, String>> linked = new ArrayList<>();
    
    static {
        linked.add(instantiateDataLink("Boolean", "boolean"));
        linked.add(instantiateDataLink("Character", "char"));
        linked.add(instantiateDataLink("Float", "int"));
        linked.add(instantiateDataLink("Double", "int"));
        linked.add(instantiateDataLink("Byte", "int"));
        linked.add(instantiateDataLink("Integer", "int"));
        linked.add(instantiateDataLink("Short", "int"));
        linked.add(instantiateDataLink("Long", "int"));
        
        linked.add(instantiateDataLink("float", "int"));
        linked.add(instantiateDataLink("double", "int"));
        linked.add(instantiateDataLink("byte", "int"));
        linked.add(instantiateDataLink("short", "int"));
        linked.add(instantiateDataLink("long", "int"));
        linked.add(instantiateDataLink("StringBuilder", "String"));
        linked.add(instantiateDataLink("StringBuffer", "String"));
        
        linked.add(instantiateDataLink("Set", "Set"));
        linked.add(instantiateDataLink("LinkedHashSet", "Set"));
        linked.add(instantiateDataLink("HashSet", "Set"));
        
        linked.add(instantiateDataLink("List", "List"));
        linked.add(instantiateDataLink("LinkedList", "List"));
        linked.add(instantiateDataLink("ArrayList", "List"));
        linked.add(instantiateDataLink("Deque", "List"));
        linked.add(instantiateDataLink("Queue", "List"));
        
        linked.add(instantiateDataLink("Entry", "Object"));
        
        linked.add(instantiateDataLink("Map", "Map"));
        linked.add(instantiateDataLink("LinkedHashMap", "Map"));
        linked.add(instantiateDataLink("HashMap", "Map"));
        linked.add(instantiateDataLink("EnumMap", "Map"));
        
        linked.add(instantiateDataLink("ReentrantReadWriteLock", "Lock"));
        linked.add(instantiateDataLink("ReentrantLock", "Lock"));
        linked.add(instantiateDataLink("ReadWriteLock", "Lock"));
        linked.add(instantiateDataLink("ReadLock", "Lock"));
        linked.add(instantiateDataLink("WriteLock", "Lock"));
    }
    
    private static Pair<String, String> instantiateDataLink(String link, String target) {
        return new Pair<>(link, target);
    }
    
    public static List<Pair<String, String>> getLinkedTypes() {
        return linked;
    }
}
