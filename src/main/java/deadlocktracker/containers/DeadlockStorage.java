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
public class DeadlockStorage {
    private static Map<String, Map<String, DeadlockClass>> PublicClasses = new HashMap<>();
    private static Map<String, Map<String, DeadlockClass>> PrivateClasses = new HashMap<>();
    private static Map<String, DeadlockLock> Locks = new HashMap<>();
    private static Map<String, DeadlockLock> ReadWriteLocks = new HashMap<>();
    
    private static Map<DeadlockClass, Integer> ClassDataTypes = new HashMap<>();
    private static Map<List<Integer>, Integer> CompoundDataTypes = new HashMap<>();
    private static Map<String, Integer> BasicDataTypes = new HashMap<>();
    private static Map<Integer, Integer> ElementalDataTypes = new HashMap<>();
    private static Integer[] ElementalTypes = new Integer[8];
    
    private static Map<Integer, Pair<Integer, Map<String, Integer>>> ReflectedClasses = new HashMap<>();
    private static Map<DeadlockClass, List<DeadlockClass>> InheritanceTree = new HashMap<>();
    private static Pair<Integer, Integer> ignoredDataRange = null;
    
    private static List<DeadlockFunction> RunnableMethods = new ArrayList<>();
    
    public static Map<String, Map<String, DeadlockClass>> getPublicClasses() {
        return PublicClasses;
    }
    
    public static Map<String, Map<String, DeadlockClass>> getPrivateClasses() {
        return PrivateClasses;
    }
    
    public Map<String, DeadlockLock> getLocks() {
        return Locks;
    }
    
    public Map<String, DeadlockLock> getReadWriteLocks() {
        return ReadWriteLocks;
    }
    
    public Map<DeadlockClass, Integer> getClassDataTypes() {
        return ClassDataTypes;
    }
    
    public Map<List<Integer>, Integer> getCompoundDataTypes() {
        return CompoundDataTypes;
    }
    
    public static Map<String, Integer> getBasicDataTypes() {
        return BasicDataTypes;
    }
    
    public Map<Integer, Integer> getElementalDataTypes() {
        return ElementalDataTypes;
    }
    
    public Integer[] getElementalTypes() {
        return ElementalTypes;
    }
    
    public Map<DeadlockClass, List<DeadlockClass>> getInheritanceTree() {
        return InheritanceTree;
    }
    
    public Map<Integer, Pair<Integer, Map<String, Integer>>> getReflectedClasses() {
        return ReflectedClasses;
    }
    
    public List<DeadlockFunction> getRunnableMethods() {
        return RunnableMethods;
    }
    
    public void setIgnoredDataRange(Pair<Integer, Integer> ign) {
        ignoredDataRange = ign;
    }
    
    public Pair<Integer, Integer> getIgnoredDataRange() {
        return ignoredDataRange;
    }
    
    private static DeadlockClass locateSubclass(String className, DeadlockClass thisClass) {
        return thisClass.getPrivateClass(className);
    }
    
    public static DeadlockClass locateInternalClass(String className, DeadlockClass thisClass) {
        return locateThroughSuper(className, thisClass);
    }
    
    private static DeadlockClass locateThroughSuper(String className, DeadlockClass thisClass) {
        //System.out.println("testing " + className + " on " + thisClass.getPackageName() + thisClass.getName());
        if(thisClass.getName().contentEquals(className)) return thisClass;
        
        DeadlockClass pc = locateSubclass(className, thisClass);
        if(pc != null) return pc;
        
        for(DeadlockClass mdc : thisClass.getSuperList()) {
            DeadlockClass m = locateThroughSuper(className, mdc);
            
            if(m != null) {
                return m;
            }
        }
        
        return null;
    }
    
    private static DeadlockClass locateImportedClass(String fullClassName, DeadlockClass thisClass) {
        return thisClass.getImport(fullClassName);
    }
    
    private static DeadlockClass locateThroughPackage(String fullClassName, DeadlockClass thisClass) {
        String pname = thisClass.getPackageName();
        
        if(pname.charAt(pname.length() - 1) == '.') {
            Map<String, DeadlockClass> m = PublicClasses.get(pname);
            return (m != null) ? m.get(fullClassName) : null;
        } else {
            DeadlockClass ret = PrivateClasses.get(pname).get(fullClassName);
            
            if(ret == null) {   // test on the package of the parent
                int idx = pname.lastIndexOf('.');
                pname = pname.substring(0, idx + 1);
                
                ret = PublicClasses.get(pname).get(fullClassName);
            }
            
            return ret;
        }
    }
    
    public static String getCanonClassName(DeadlockClass mdc) {
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
    
    public static Pair<String, String> getPrivatePackageClass(String fullClassName) {
        String[] names = fullClassName.split("\\.");
        
        String cname;
        int i;
        if (names.length > 0) {
            cname = names[0] + ".";
            
            int s = 0;
            for (i = 1; i < names.length; i++) {
                if(PublicClasses.get(cname) != null) {
                    s = 1;
                } else if(PrivateClasses.get(cname.substring(0, cname.length() - 1)) != null) {
                    break;
                }
                cname += names[i] + ".";
            }
            
            return new Pair<>(cname.substring(0, cname.length() - 1), names[i - 1] + "." + fullClassName.substring(Math.min(cname.length(), fullClassName.length() - 1)));
        } else {
            cname = fullClassName;
            i = 0;
            
            return new Pair<>(cname,"");
        }
    }
    
    private static Set<String> fetchPackageNamesFromImports(DeadlockClass thisClass) {
        Set<String> packNames = new HashSet<>();
        for (DeadlockClass mdc : thisClass.getImportClasses()) {
            packNames.add(mdc.getPackageName());
        }
        
        DeadlockClass parentClass = thisClass.getParent();
        if (parentClass != null) {
            packNames.add(parentClass.getPackageName());
            packNames.addAll(fetchPackageNamesFromImports(parentClass));
        }
        
        return packNames;
    }
    
    public static DeadlockClass locatePublicClass(String fullClassName, DeadlockClass thisClass) {
        int idx = fullClassName.lastIndexOf('.');
        
        String packName = fullClassName.substring(0, idx + 1);
        String className = fullClassName.substring(idx + 1);
        
        Map<String, DeadlockClass> m = PublicClasses.get(packName);
        if (m != null) {
            return m.get(className);
        }
        
        if (thisClass != null) {
            Set<String> packNames = fetchPackageNamesFromImports(thisClass);
            for (String pname : packNames) {
                m = PublicClasses.get(pname);
                if (m != null) {
                    DeadlockClass mdc = m.get(className);
                    if (mdc != null) {
                        return mdc;
                    }
                }
            }
        }
        
        return null;    // could be classes not implemented on the project source scope
    }
    
    public static DeadlockClass locateClassInternal(String fullClassName, DeadlockClass thisClass) {
        if(thisClass == null) return null;
        
        DeadlockClass ret;
        String className = fullClassName.substring(fullClassName.indexOf(".") + 1);
        
        //check self class
        if(className.contentEquals(thisClass.getName())) return thisClass;

        //check private classes
        ret = locateSubclass(className, thisClass);
        if(ret != null) return ret;

        //check inside inherited classes
        ret = locateThroughSuper(className, thisClass);
        if(ret != null) return ret;

        //check imports
        ret = locateImportedClass(fullClassName, thisClass);
        if(ret != null) return ret;

        //check package
        return locateThroughPackage(fullClassName, thisClass);
    }
    
    private static Pattern p = Pattern.compile("([\\w\\d_\\.]*[\\w\\d\\.]*)(_[\\d]+)");
    private static Pattern p2 = Pattern.compile("[\\w\\d_\\.]*(_[\\d]+)");
    
    public static String getNameFromCanonClass(String name) {
        Matcher m = p2.matcher(name);
        if (!m.find()) {
            return name;
        }
        
        m = p.matcher(name);
        if (m.find()) {
            return m.group(1);
        }
        
        return "";
    }
    
    public static String getNameFromCanonClass(DeadlockClass mdc) {
        return getNameFromCanonClass(getClassPath(mdc));
    }
    
    private static Pair<String, String> locatePrivateClassPath(String s, String fullClassName) {
        s = fullClassName;
        
        int idx = s.length(), idx3 = s.length();
        
        String t = "", u = "";
        Map<String, DeadlockClass> m;
        while(true) {
            if(PrivateClasses.containsKey(s)) {
                u = fullClassName.substring(Math.max(s.lastIndexOf('.', s.length() - 1) + 1, 0), idx3 + 1);
                fullClassName = fullClassName.substring(idx3 + 1);
                break;
            }
            
            idx3 = s.lastIndexOf('.');
            if(idx3 < 0) {
                break;
            }
            
            idx = fullClassName.lastIndexOf('.', idx - 1);
            if(idx < 0) break;
            
            s = fullClassName.substring(0, idx);
        }
        
        m = PrivateClasses.get(s);
        idx = 0;
        if(m != null) {
            while(true) {
                if(m.containsKey(u + t)) {
                    break;
                }

                idx = fullClassName.indexOf('.', idx + 1);
                if(idx < 0) {
                    t = fullClassName;
                    break;
                }

                t = fullClassName.substring(0, idx);
            }
        }
        
        return new Pair<>(s, u + t);
    }
    
    private static DeadlockClass locatePrivateClass(String fullClassName, DeadlockClass thisClass) {
        String packName = thisClass.getPackageName(), className;
        if (!fullClassName.startsWith(packName)) return null;
        
        Pair<String, String> p = locatePrivateClassPath(getPublicPackageName(packName), fullClassName);
        packName = p.left;
        className = p.right;
        
        DeadlockClass ret = PrivateClasses.get(packName).get(className);
        return ret;
    }
    
    public static String getPublicPackageName(String s) {
        while (true) {
            if (PublicClasses.get(s) != null) return s;
            
            int i = s.substring(0, s.length() - 1).lastIndexOf('.');
            if (i < 0) return null;
            
            s = s.substring(0, i + 1);
        }
    }
    
    public static String getPublicPackageName(DeadlockClass thisClass) {
        String s = thisClass.getPackageName();
        return getPublicPackageName(s);
    }
    
    public static String getClassPath(DeadlockClass mdc) {
        return mdc.getPackageName() + (mdc.getPackageName().endsWith(".") ? "" : ".") + mdc.getPathName();
    }
    
    public static Pair<String, String> locateClassPath(String fullClassName) {
        String packName = getPublicPackageName(fullClassName);
        if(packName != null) {
            int idx = packName.length();
            String className = fullClassName.substring(idx);
            if (className.contentEquals("*")) return new Pair<>(packName, className);
            
            DeadlockClass c = PublicClasses.get(packName).get(className);
            if (c != null) return new Pair<>(packName, className);
            
            return locatePrivateClassPath(packName, fullClassName);
        }
        
        return null;
    }
    
    public static DeadlockClass locateClass(String fullClassName) {
        int idx = Math.max(fullClassName.lastIndexOf('.'), 0);
        
        String packName = getPublicPackageName(fullClassName);
        String className = fullClassName.substring(idx + 1);
        
        DeadlockClass c = PublicClasses.get(packName).get(className);
        if (c != null) return c;
        
        packName = fullClassName.substring(0, idx);
        return PrivateClasses.get(packName).get(className);
    }
    
    public static DeadlockClass locateClass(String className, DeadlockClass thisClass) {
        if(thisClass == null || className == null) return null;
        //System.out.println("locating "  + className + " from " + DeadlockStorage.getCanonClassName(thisClass));
        
        DeadlockClass ret = locateClassInternal(className, thisClass);
        if (ret != null) return ret;
        
        String fullClassName = className;
        
        ret = locatePublicClass(fullClassName, thisClass);
        if (ret != null) return ret;
        
        ret = locatePrivateClass(fullClassName, thisClass);
        if (ret != null) return ret;
        
        if (getNameFromCanonClass(thisClass).contentEquals(fullClassName)) {
            ret = thisClass;
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
        String s = "--------\n PUBLIC IMPORTS:\n";
        for(Entry<String, Map<String, DeadlockClass>> m : PublicClasses.entrySet()) {
            s += ("\nPACKAGE " + m.getKey() + "\n");
            for(Entry<String, DeadlockClass> c : m.getValue().entrySet()) {
                s += ("\t" + c.getValue().getPathName() + "\n");
                for(String i : c.getValue().getImportNames()) {
                    s += ("\t\t" + i + "\n");
                }
            }
        }
        
        return s;
    }
    
    private static String dumpCachedPackages() {
        String s = "--------\n PUBLIC:\n";
        for(Entry<String, Map<String, DeadlockClass>> m : PublicClasses.entrySet()) {
            s += ("\nPACKAGE " + m.getKey() + "\n");
            for(Entry<String, DeadlockClass> c : m.getValue().entrySet()) {
                s += ("\t" + c.getValue().getPathName() + " Super: " + c.getValue().getSuperNameList() + "\n");
            }
        }
        
        s += "--------\n PRIVATES:\n";
        for(Entry<String, Map<String, DeadlockClass>> m : PrivateClasses.entrySet()) {
            s += ("\nINCLASS " + m.getKey() + "\n");
            for(Entry<String, DeadlockClass> c : m.getValue().entrySet()) {
                s += ("\t" + c.getValue().getPathName() + "\n");
            }
        }
        
        return s;
    }
    
    @Override
    public String toString() {
        String s = "";
        
        //s += dumpCachedPackages() + "\n" + dumpCachedImports();
                
        s += "--------\n LOCKS:\n";
        for(Map.Entry<String, DeadlockLock> l : Locks.entrySet()) {
            s += (l.getKey() + " -> " + l.getValue() + "\n");
        }
        
        s += "--------\n READWRITELOCKS:\n";
        for(Map.Entry<String, DeadlockLock> l : ReadWriteLocks.entrySet()) {
            s += (l.getKey() + " -> " + l.getValue() + "\n");
        }
        
        s += "\n basic data types: \n";
        for(Map.Entry<String, Integer> e : BasicDataTypes.entrySet()) {
            s += e.getKey() + " " + e.getValue() + "\n";
        }
        
        s += "\n class data types: \n";
        for(Map.Entry<DeadlockClass, Integer> e : ClassDataTypes.entrySet()) {
            s += getCanonClassName(e.getKey()) + " " + e.getValue() + "\n";
        }
        
        s += "\n compound data types: \n";
        for(Map.Entry<List<Integer>, Integer> e : CompoundDataTypes.entrySet()) {
            s += e.getKey() + " : " + e.getValue() + "\n";
        }
        
        return s;
    }
}
