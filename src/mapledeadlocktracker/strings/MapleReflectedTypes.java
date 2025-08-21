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

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 *
 * @author RonanLana
 */
public enum MapleReflectedTypes {
    // NOTE: known class-type cannot be reflected, returns types must ALWAYS be basic types (that is, not known classes nor compounds)
    
    ATOMICBOOLEAN("AtomicBoolean", initReflectedMethods(new String[]{"set", "lazySet"}, new String[]{"void", "void"}), "boolean"),
    ATOMICINTEGER("AtomicInteger", initReflectedMethods(new String[]{"set", "lazySet", "compareAndSet", "weakCompareAndSet"}, new String[]{"void", "void", "boolean", "boolean"}), "int"),
    ATOMICLONG("AtomicLong", initReflectedMethods(new String[]{"set", "lazySet", "compareAndSet", "weakCompareAndSet"}, new String[]{"void", "void", "boolean", "boolean"}), "long"),
    ATOMICINTEGERARRAY("AtomicIntegerArray", initReflectedMethods(new String[]{"set", "lazySet", "compareAndSet", "weakCompareAndSet"}, new String[]{"void", "void", "boolean", "boolean"}), "int"),
    ATOMICLONGARRAY("AtomicLongArray", initReflectedMethods(new String[]{"set", "lazySet", "compareAndSet", "weakCompareAndSet"}, new String[]{"void", "void", "boolean", "boolean"}), "long"),
    STRING("String", initReflectedMethods(new String[]{}, new String[]{}), "Object"),
    LOCK("Lock", initReflectedMethods(new String[]{"lock", "unlock", "tryLock"}, new String[]{"void", "void", "void"}), "Object");
    
    private String name;
    private Map<String, String> methodReturns;
    private String defaultReturn;
    
    MapleReflectedTypes(String reflectedName, Map<String, String> methodReturnTypes, String defReturn) {
        name = reflectedName;
        methodReturns = methodReturnTypes;
        defaultReturn = defReturn;
    }
    
    public String getName() {
        return name;
    }
    
    public Map<String, String> getMethodReturns() {
        return methodReturns;
    }
    
    public String getDefaultReturn() {
        return defaultReturn;
    }
    
    private static Map<String, String> initReflectedMethods(String[] methods, String[] retValues) {
        Map<String, String> map = new LinkedHashMap<>();
        
        for(int i = 0; i < methods.length; i++) {
            map.put(methods[i], retValues[i]);
        }
        
        return map;
    }
    
    public static List<MapleReflectedTypes> getReflectedTypes() {
        return new ArrayList<>(Arrays.asList(MapleReflectedTypes.values()));
    }
}
