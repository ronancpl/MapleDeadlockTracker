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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import mapledeadlocktracker.containers.Pair;

/**
 *
 * @author RonanLana
 */
public class MapleLinkedTypes {
    
    private static final Map<String, String> linkedTable = new HashMap<>();
    private static final List<Pair<String, String>> linked = new ArrayList<>();
    
    private static void instantiateDataLink(String link, String target) {
        linked.add(new Pair<>(link, target));
        linkedTable.put(link, target);
    }
    
    static {
        instantiateDataLink("Boolean", "boolean");
        instantiateDataLink("Character", "char");
        instantiateDataLink("Float", "int");
        instantiateDataLink("Double", "int");
        instantiateDataLink("Byte", "int");
        instantiateDataLink("Integer", "int");
        instantiateDataLink("Short", "int");
        instantiateDataLink("Long", "int");
        
        instantiateDataLink("float", "int");
        instantiateDataLink("double", "int");
        instantiateDataLink("byte", "int");
        instantiateDataLink("short", "int");
        instantiateDataLink("long", "int");
        instantiateDataLink("StringBuilder", "String");
        instantiateDataLink("StringBuffer", "String");
        
        instantiateDataLink("Set", "Set");
        instantiateDataLink("LinkedHashSet", "Set");
        instantiateDataLink("HashSet", "Set");
        
        instantiateDataLink("List", "List");
        instantiateDataLink("LinkedList", "List");
        instantiateDataLink("ArrayList", "List");
        instantiateDataLink("Deque", "List");
        instantiateDataLink("Queue", "List");
        
        instantiateDataLink("Entry", "Object");
        instantiateDataLink("Exception", "Object");
        
        instantiateDataLink("Map", "Map");
        instantiateDataLink("LinkedHashMap", "Map");
        instantiateDataLink("HashMap", "Map");
        instantiateDataLink("EnumMap", "Map");
        instantiateDataLink("EnumMap", "Map");
        instantiateDataLink("AbstractMap", "Map");
        
        instantiateDataLink("ReentrantReadWriteLock", "Lock");
        instantiateDataLink("ReentrantLock", "Lock");
        instantiateDataLink("ReadWriteLock", "Lock");
        instantiateDataLink("ReadLock", "Lock");
        instantiateDataLink("WriteLock", "Lock");
        
        instantiateDataLink("NashornScriptEngine", "Invocable");
    }
    
    public static List<Pair<String, String>> getLinkedTypes() {
        return new ArrayList<>(linked);
    }
    
    public static String getLinkedType(String target) {
        return linkedTable.containsKey(target) ? linkedTable.get(target) : target;
    }
}
