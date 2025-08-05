/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
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
    ATOMICLONGARRAY("AtomicLongArray", initReflectedMethods(new String[]{"set", "lazySet", "compareAndSet", "weakCompareAndSet"}, new String[]{"void", "void", "boolean", "boolean"}), "long");
    
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
