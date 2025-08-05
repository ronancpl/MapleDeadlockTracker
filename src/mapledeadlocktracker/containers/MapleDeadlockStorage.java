/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package mapledeadlocktracker.containers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 *
 * @author RonanLana
 */
public class MapleDeadlockStorage {
    private static Map<String, Map<String, MapleDeadlockClass>> maplePublicPackages = new HashMap<>();
    private static Map<String, Map<String, MapleDeadlockClass>> maplePrivateClasses = new HashMap<>();
    private static Map<String, MapleDeadlockLock> mapleLocks = new HashMap<>();
    private static Map<String, MapleDeadlockLock> mapleReadWriteLocks = new HashMap<>();
    
    private static Map<MapleDeadlockClass, Integer> mapleClassDataTypes = new HashMap<>();
    private static Map<List<Integer>, Integer> mapleCompoundDataTypes = new HashMap<>();
    private static Map<String, Integer> mapleBasicDataTypes = new HashMap<>();
    private static Map<Integer, Integer> mapleElementalDataTypes = new HashMap<>();
    private static Integer[] mapleElementalTypes = new Integer[6];
    
    private static Map<Integer, Pair<Integer, Map<String, Integer>>> mapleReflectedClasses = new HashMap<>();
    private static Map<MapleDeadlockClass, List<MapleDeadlockClass>> mapleInheritanceTree = new HashMap<>();
    private static Pair<Integer, Integer> ignoredDataRange = null;
    
    private static List<MapleDeadlockFunction> mapleRunnableMethods = new ArrayList<>();
    
    public Map<String, Map<String, MapleDeadlockClass>> getPublicClasses() {
        return maplePublicPackages;
    }
    
    public Map<String, Map<String, MapleDeadlockClass>> getPrivateClasses() {
        return maplePrivateClasses;
    }
    
    public Map<String, MapleDeadlockLock> getLocks() {
        return mapleLocks;
    }
    
    public Map<String, MapleDeadlockLock> getReadWriteLocks() {
        return mapleReadWriteLocks;
    }
    
    public Map<MapleDeadlockClass, Integer> getClassDataTypes() {
        return mapleClassDataTypes;
    }
    
    public Map<List<Integer>, Integer> getCompoundDataTypes() {
        return mapleCompoundDataTypes;
    }
    
    public Map<String, Integer> getBasicDataTypes() {
        return mapleBasicDataTypes;
    }
    
    public Map<Integer, Integer> getElementalDataTypes() {
        return mapleElementalDataTypes;
    }
    
    public Integer[] getElementalTypes() {
        return mapleElementalTypes;
    }
    
    public Map<MapleDeadlockClass, List<MapleDeadlockClass>> getInheritanceTree() {
        return mapleInheritanceTree;
    }
    
    public Map<Integer, Pair<Integer, Map<String, Integer>>> getReflectedClasses() {
        return mapleReflectedClasses;
    }
    
    public List<MapleDeadlockFunction> getRunnableMethods() {
        return mapleRunnableMethods;
    }
    
    public void setIgnoredDataRange(Pair<Integer, Integer> ign) {
        ignoredDataRange = ign;
    }
    
    public Pair<Integer, Integer> getIgnoredDataRange() {
        return ignoredDataRange;
    }
    
    private static MapleDeadlockClass locatePrivateClass(String className, MapleDeadlockClass thisClass) {
        return thisClass.getPrivateClass(className);
    }
    
    public static MapleDeadlockClass locateInternalClass(String className, MapleDeadlockClass thisClass) {
        return locateThroughSuper(className, thisClass);
    }
    
    private static MapleDeadlockClass locateThroughSuper(String className, MapleDeadlockClass thisClass) {
        //System.out.println("testing " + className + " on " + thisClass.getPackageName() + thisClass.getName());
        if(thisClass.getName().contentEquals(className)) return thisClass;
        
        MapleDeadlockClass pc = locatePrivateClass(className, thisClass);
        if(pc != null) return pc;
        
        for(MapleDeadlockClass mdc : thisClass.getSuperList()) {
            MapleDeadlockClass m = locateThroughSuper(className, mdc);
            
            if(m != null) {
                return m;
            }
        }
        
        return null;
    }
    
    private static MapleDeadlockClass locateImportedClass(String className, MapleDeadlockClass thisClass) {
        return thisClass.getImport(className);
    }
    
    private static MapleDeadlockClass locateThroughPackage(String className, MapleDeadlockClass thisClass) {
        String pname = thisClass.getPackageName();
        
        if(pname.charAt(pname.length() - 1) == '.') {
            return maplePublicPackages.get(pname).get(className);
        } else {
            MapleDeadlockClass ret = maplePrivateClasses.get(pname).get(className);
            
            if(ret == null) {   // test on the package of the parent
                int idx = pname.lastIndexOf('.');
                pname = pname.substring(0, idx + 1);
                
                ret = maplePublicPackages.get(pname).get(className);
            }
            
            return ret;
        }
    }
    
    public static String getCanonClassName(MapleDeadlockClass mdc) {
        String packName = mdc.getPackageName();
        
        if(packName.charAt(packName.length() - 1) == '.') {
            return packName + mdc.getPathName();
        } else {
            String restName = mdc.getPathName();
            
            int idx = restName.indexOf('.');
            restName = restName.substring(idx + 1);
            
            return packName + "." + restName;
        }
    }
    
    public static Pair<String, String> getPrivateNameParts(String canonName) {
        String packName, curName, className = canonName;
        
        int idx = className.indexOf('.');
        if(idx != -1) {
            curName = className.substring(0, idx);
            className = className.substring(idx + 1);
        } else {
            curName = className;
            className = "";
        }
        
        packName = curName;
        
        Map<String, MapleDeadlockClass> m;
        while(true) {
            m = maplePrivateClasses.get(packName);
            if(m != null) break;
            
            idx = className.indexOf('.');
            if(idx == -1) return null;
            
            curName = className.substring(0, idx);
            className = className.substring(idx + 1);
            
            packName += ("." + curName);
        }
        
        className = curName + "." + className;
        return new Pair<>(packName, className);
    }
    
    public static MapleDeadlockClass locateCanonClass(String canonName) {
        int idx = canonName.lastIndexOf(".");
        
        String packName = canonName.substring(0, idx + 1);
        String className = canonName.substring(idx + 1);
        
        //System.out.println("p: " + packName + " c: " + className);
        try {
            return maplePublicPackages.get(packName).get(className);
        } catch(Exception e) {}
        
        Pair<String, String> p = getPrivateNameParts(canonName);
        if(p != null) {
            packName = p.left;
            className = p.right;
            
            return maplePrivateClasses.get(packName).get(className);
        }
        
        return null;    // could be classes not implemented on the project source scope
    }
    
    public static MapleDeadlockClass locateClassInternal(String className, MapleDeadlockClass thisClass) {
        if(thisClass == null) return null;
        
        MapleDeadlockClass ret;

        //check private classes
        ret = locatePrivateClass(className, thisClass);
        if(ret != null) return ret;

        //check inside inherited classes
        ret = locateThroughSuper(className, thisClass);
        if(ret != null) return ret;

        //check imports
        ret = locateImportedClass(className, thisClass);
        if(ret != null) return ret;

        //check package
        return locateThroughPackage(className, thisClass);
    }
    
    public static MapleDeadlockClass locateClass(String className, MapleDeadlockClass thisClass) {
        if(thisClass == null) return null;
        
        //System.out.println("locating "  + className + " from " + MapleDeadlockStorage.getCanonClassName(thisClass));
        
        MapleDeadlockClass ret;
        if(!className.contains(".")) {
            ret = locateClassInternal(className, thisClass);
        } else {
            String names[] = className.split("\\.");
            
            ret = locateClassInternal(names[0], thisClass);
            if(ret != null) {
                for(int i = 1; i < names.length; i++) {
                    ret = locatePrivateClass(names[i], ret);
                    if(ret == null) break;
                }
            }
            
            if(ret == null) {
                return locateCanonClass(className);
            }
        }
        
        return ret;
    }
    
    // adapted from String.hashCode()
    public static Long hash64(String string) {
        Long h = 1125899906842597L; // prime

        for (int i = 0; i < string.length(); i++) {
            h = 31*h + string.charAt(i);
        }
        return h;
    }
    
    private static String dumpCachedImports() {
        String s = "--------\nMaple PUBLIC IMPORTS:\n";
        for(Entry<String, Map<String, MapleDeadlockClass>> m : maplePublicPackages.entrySet()) {
            s += ("\nPACKAGE " + m.getKey() + "\n");
            for(Entry<String, MapleDeadlockClass> c : m.getValue().entrySet()) {
                s += ("\t" + c.getValue().getPathName() + "\n");
                for(String i : c.getValue().getImportNames()) {
                    s += ("\t\t" + i + "\n");
                }
            }
        }
        
        return s;
    }
    
    private static String dumpCachedPackages() {
        String s = "--------\nMaple PUBLIC:\n";
        for(Entry<String, Map<String, MapleDeadlockClass>> m : maplePublicPackages.entrySet()) {
            s += ("\nPACKAGE " + m.getKey() + "\n");
            for(Entry<String, MapleDeadlockClass> c : m.getValue().entrySet()) {
                s += ("\t" + c.getValue().getPathName() + " Super: " + c.getValue().getSuperNameList() + "\n");
            }
        }
        
        s += "--------\nMaple PRIVATES:\n";
        for(Entry<String, Map<String, MapleDeadlockClass>> m : maplePrivateClasses.entrySet()) {
            s += ("\nINCLASS " + m.getKey() + "\n");
            for(Entry<String, MapleDeadlockClass> c : m.getValue().entrySet()) {
                s += ("\t" + c.getValue().getPathName() + "\n");
            }
        }
        
        return s;
    }
    
    @Override
    public String toString() {
        String s = "";
        
        //s += dumpCachedPackages() + "\n" + dumpCachedImports();
                
        s += "--------\nMaple LOCKS:\n";
        for(Map.Entry<String, MapleDeadlockLock> l : mapleLocks.entrySet()) {
            s += (l.getKey() + " -> " + l.getValue() + "\n");
        }
        
        s += "--------\nMaple READWRITELOCKS:\n";
        for(Map.Entry<String, MapleDeadlockLock> l : mapleReadWriteLocks.entrySet()) {
            s += (l.getKey() + " -> " + l.getValue() + "\n");
        }
        
        s += "\nMaple basic data types: \n";
        for(Map.Entry<String, Integer> e : mapleBasicDataTypes.entrySet()) {
            s += e.getKey() + " " + e.getValue() + "\n";
        }
        
        s += "\nMaple class data types: \n";
        for(Map.Entry<MapleDeadlockClass, Integer> e : mapleClassDataTypes.entrySet()) {
            s += getCanonClassName(e.getKey()) + " " + e.getValue() + "\n";
        }
        
        s += "\nMaple compound data types: \n";
        for(Map.Entry<List<Integer>, Integer> e : mapleCompoundDataTypes.entrySet()) {
            s += e.getKey() + " : " + e.getValue() + "\n";
        }
        
        return s;
    }
}
