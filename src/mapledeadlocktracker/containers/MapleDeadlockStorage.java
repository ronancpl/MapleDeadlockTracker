/*
    This file is part of the MapleDeadlockTracker detection tool
    Copyleft (L) 2025 RonanLana

    GNU General Public License v3.0

    Permissions of this strong copyleft license are conditioned on making available complete
    source code of licensed works and modifications, which include larger works using a licensed
    work, under the same license. Copyright and license notices must be preserved. Contributors
    provide an express grant of patent rights.
*/
package mapledeadlocktracker.containers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author RonanLana
 */
public class MapleDeadlockStorage {
    private static Map<String, Map<String, MapleDeadlockClass>> maplePublicClasses = new HashMap<>();
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
    
    public static Map<String, Map<String, MapleDeadlockClass>> getPublicClasses() {
        return maplePublicClasses;
    }
    
    public static Map<String, Map<String, MapleDeadlockClass>> getPrivateClasses() {
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
    
    public static Map<String, Integer> getBasicDataTypes() {
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
    
    private static MapleDeadlockClass locateSubclass(String className, MapleDeadlockClass thisClass) {
        return thisClass.getPrivateClass(className);
    }
    
    public static MapleDeadlockClass locateInternalClass(String className, MapleDeadlockClass thisClass) {
        return locateThroughSuper(className, thisClass);
    }
    
    private static MapleDeadlockClass locateThroughSuper(String className, MapleDeadlockClass thisClass) {
        //System.out.println("testing " + className + " on " + thisClass.getPackageName() + thisClass.getName());
        if(thisClass.getName().contentEquals(className)) return thisClass;
        
        MapleDeadlockClass pc = locateSubclass(className, thisClass);
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
            Map<String, MapleDeadlockClass> m = maplePublicClasses.get(pname);
            return (m != null) ? m.get(className) : null;
        } else {
            MapleDeadlockClass ret = maplePrivateClasses.get(pname).get(className);
            
            if(ret == null) {   // test on the package of the parent
                int idx = pname.lastIndexOf('.');
                pname = pname.substring(0, idx + 1);
                
                ret = maplePublicClasses.get(pname).get(className);
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
    
    public static Pair<String, String> getPrivatePackageClass(String canonName) {
        String[] names = canonName.split("\\.");
        
        String cname;
        int i;
        if (names.length > 0) {
            cname = names[0] + ".";
            
            int s = 0;
            for (i = 1; i < names.length; i++) {
                if(maplePublicClasses.get(cname) != null) {
                    s = 1;
                } else if(maplePrivateClasses.get(cname.substring(0, cname.length() - 1)) != null) {
                    break;
                }
                cname += names[i] + ".";
            }
            
            return new Pair<>(cname.substring(0, cname.length() - 1), names[i - 1] + "." + canonName.substring(Math.min(cname.length(), canonName.length() - 1)));
        } else {
            cname = canonName;
            i = 0;
            
            return new Pair<>(cname,"");
        }
    }
    
    private static Set<String> fetchPackageNamesFromImports(MapleDeadlockClass thisClass) {
        Set<String> packNames = new HashSet<>();
        for (MapleDeadlockClass mdc : thisClass.getImportClasses()) {
            packNames.add(mdc.getPackageName());
        }
        
        MapleDeadlockClass parentClass = thisClass.getParent();
        if (parentClass != null) {
            packNames.add(parentClass.getPackageName());
            packNames.addAll(fetchPackageNamesFromImports(parentClass));
        }
        
        return packNames;
    }
    
    public static MapleDeadlockClass locatePublicClass(String canonName, MapleDeadlockClass thisClass) {
        int idx = canonName.lastIndexOf('.');
        
        String packName = canonName.substring(0, idx + 1);
        String className = canonName.substring(idx + 1);
        
        Map<String, MapleDeadlockClass> m = maplePublicClasses.get(packName);
        if (m != null) {
            return m.get(className);
        }
        
        if (thisClass != null) {
            Set<String> packNames = fetchPackageNamesFromImports(thisClass);
            for (String pname : packNames) {
                m = maplePublicClasses.get(pname);
                if (m != null) {
                    MapleDeadlockClass mdc = m.get(className);
                    if (mdc != null) {
                        return mdc;
                    }
                }
            }
        }
        
        return null;    // could be classes not implemented on the project source scope
    }
    
    public static MapleDeadlockClass locateClassInternal(String className, MapleDeadlockClass thisClass) {
        if(thisClass == null) return null;
        
        MapleDeadlockClass ret;

        //check private classes
        ret = locateSubclass(className, thisClass);
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
    
    private static Pattern p = Pattern.compile("([\\w\\d_\\.]+)(?=(_[\\d]+))");
    
    private static String getNameFromCanonClass(String name) {
        Matcher m = p.matcher(name);
        if (m.find()) {
            return m.group(0);
        }
        
        return "";
    }
    
    private static boolean findClassInPath(String className, String pathNames[]) {
        for (int i = 0; i < pathNames.length; i++) {
            if (className.contentEquals(pathNames[i])) return true;
        }
        
        return false;
    }
    
    private static MapleDeadlockClass locatePrivateClass(String className, MapleDeadlockClass thisClass) {
        String privateClassName = className;
        String names[] = thisClass.getPathName().split("\\.");

        MapleDeadlockClass ret = locateClassInternal(className, thisClass);
        if(ret != null && findClassInPath(className, names)) {
            Map<String, MapleDeadlockClass> m = maplePrivateClasses.get(ret.getPackageName() + names[0]);
            if (m != null) {
                ret = m.get(privateClassName.substring(0, privateClassName.indexOf(className) + className.length()));
            }
        }
        
        return ret;
    }
    
    public static String getPublicPackageName(MapleDeadlockClass thisClass) {
        String s = thisClass.getPackageName();
        
        while (true) {
            if (maplePublicClasses.get(s) != null) return s;
            
            int i = s.substring(0, s.length() - 1).lastIndexOf('.');
            if (i < 0) return null;
            
            s = s.substring(0, i + 1);
        }
    }
    
    public static MapleDeadlockClass locateClass(String className, MapleDeadlockClass thisClass) {
        if(thisClass == null || className == null) return null;
        
        className = getNameFromCanonClass(className);
        
        //System.out.println("locating "  + className + " from " + MapleDeadlockStorage.getCanonClassName(thisClass));
        
        MapleDeadlockClass ret = locatePublicClass(getPublicPackageName(thisClass) + className, thisClass);
        if (ret != null) return ret;
        
        ret = locatePrivateClass(className, thisClass);
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
        for(Entry<String, Map<String, MapleDeadlockClass>> m : maplePublicClasses.entrySet()) {
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
        for(Entry<String, Map<String, MapleDeadlockClass>> m : maplePublicClasses.entrySet()) {
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
