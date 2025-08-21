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

/**
 *
 * @author RonanLana
 */

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class MapleDeadlockClass {
    String name;
    String pathName;
    String packName;
    boolean isAbstract;
    
    MapleDeadlockClassType type;
    Set<String> importedEnums = new HashSet<>();
    
    public enum MapleDeadlockClassType {
        CLASS, ENUM, INTERFACE
    }
    
    MapleDeadlockClass parent;
    
    List<Integer> typeMasks = new LinkedList<>();   // holds abstract types from the class
    Set<Integer> typeMaskSet;
    
    List<String> supName;
    List<MapleDeadlockClass> superClass = new LinkedList<>();    // technically there is only one superclass, but for the purposes of this code interfaces also accounts here.
    Map<String, MapleDeadlockClass> privateClasses = new HashMap<>();
    
    Map<String, MapleDeadlockClass> importList = new HashMap<>();   // holds solely the class name
    Map<String, List<String>> fullImportList = new HashMap<>();
    
    Map<String, MapleDeadlockFunction> methods = new HashMap<>();
    Map<String, Integer> fields = new HashMap();
    
    public MapleDeadlockClass(MapleDeadlockClassType ctype, String className, String packageName, String classPathName, List<String> superNames, boolean abstracted, MapleDeadlockClass parentClass) {
        type = ctype;
        name = className;
        
        if (!classPathName.contains(".")) {
            packName = packageName;
            pathName = classPathName;
        } else {
            String names[] = classPathName.split("\\.");
            for (int i = 0; i < names.length - 1; i++) {
                packageName += names[i] + ".";
            }

            packName = packageName;
            pathName = names[names.length - 1];
        }
        
        parent = parentClass;
        
        supName = superNames;
        isAbstract = abstracted;
        
        importList.put(packName + "*", null);    // default import all classes from same package
    }
    
    public boolean isEnum() {
        return type.equals(MapleDeadlockClassType.ENUM);
    }
    
    public boolean isInterface() {
        return type.equals(MapleDeadlockClassType.INTERFACE);
    }
    
    public MapleDeadlockClass getParent() {
        return parent;
    }
    
    public int getMaskedTypeSize() {
        return typeMasks.size();
    }
    
    public void addMaskedType(Integer i) {
        typeMasks.add(i);
    }
    
    public void updateMaskedType(Integer index, Integer v) {
        typeMasks.set(index, v);
    }
    
    public List<Integer> getMaskedTypes() {
        return typeMasks;
    }
    
    public Set<Integer> getMaskedTypeSet() {
        return typeMaskSet;
    }
    
    public void generateMaskedTypeSet() {
        typeMaskSet = new LinkedHashSet(typeMasks);
    }
    
    public void addImport(String s) {
        importList.put(s, null);
    }
    
    public void updateImport(String s, String full, MapleDeadlockClass mdc) {
        importList.put(MapleDeadlockStorage.getCanonClassName(mdc), mdc);
        importList.put(mdc.getName(), mdc);
        
        List<String> ls = fullImportList.get(s);
        if(ls == null) {
            ls = new LinkedList();
            fullImportList.put(s, ls);
        }
        ls.add(full);
    }
    
    public void removeImport(String s) {
        importList.remove(s);
    }
    
    public List<String> getImportNames() {
        return new ArrayList<>(importList.keySet());
    }
    
    public List<MapleDeadlockClass> getImportClasses() {
        List<MapleDeadlockClass> list = new LinkedList<>();
        for (MapleDeadlockClass c : importList.values()) {
            if (c != null) list.add(c);
        }
        
        return list;
    }
    
    public List<Pair<String, MapleDeadlockClass>> getImports() {
        List<Pair<String, MapleDeadlockClass>> list = new LinkedList<>();
        
        for(Entry<String, MapleDeadlockClass> e : importList.entrySet()) {
            list.add(new Pair<>(e.getKey(), e.getValue()));
        }
        
        return list;
    }
    
    public MapleDeadlockClass getImport(String s) {
        return importList.get(s);
    }
    
    public void addPrivateClass(String s, MapleDeadlockClass mdc) {
        privateClasses.put(s, mdc);
    }
    
    public MapleDeadlockClass getPrivateClass(String s) {
        return privateClasses.get(s);
    }
    
    public void addFieldVariable(Integer type, String name) {
        fields.put(name, type);
    }
    
    public void updateFieldVariable(String name, Integer type) {
        fields.put(name, type);
    }
    
    public Integer getFieldVariable(String name) {
        return fields.get(name);
    }
    
    public Map<String, Integer> getFieldVariables() {
        return fields;
    }
    
    public void addClassMethod(MapleDeadlockFunction classMethod) {
        methods.put(classMethod.getName(), classMethod);
    }
    
    public MapleDeadlockFunction getMethodByName(String name) {
        return methods.get(name);
    }
    
    public List<MapleDeadlockFunction> getMethods() {
        return new ArrayList<>(methods.values());
    }
    
    public String getName() {
        return name;
    }
    
    public String getPathName() {
        return pathName;
    }
    
    public String getPackageName() {
        return packName;
    }
    
    public void setPackageName(String packName) {
        this.packName = packName;
    }
    
    public List<String> getSuperNameList() {
        return supName;
    }
    
    public List<MapleDeadlockClass> getSuperList() {
        return superClass;
    }
    
    public void addSuper(MapleDeadlockClass s) {
        if(s != null && s != this) {
            superClass.add(s);
        }
    }
    
    public List<List<Integer>> getArgsFromMethodName(String name) {
        List<List<Integer>> ret = new LinkedList();
        
        for(MapleDeadlockFunction mdf : methods.values()) {
            if(mdf.getName().contentEquals(name)) {
                ret.add(mdf.getParameters());
            }
        }
        
        return ret;
    }
    
    public MapleDeadlockFunction getMethodOnSuperclass(String name, List<Integer> params) {
        MapleDeadlockFunction mdf;

        for(MapleDeadlockClass mdc : superClass) {
            mdf = mdc.getMethod(true, name, params);
            
            if(mdf != null) {
                return mdf;     // the only one structure implementing methods is the "real" superclass.
            }
            
            break;  // first element is the "real" superclass, no need to check others
        }
        
        return null;
    }
    
    public MapleDeadlockFunction getTemplateMethodOnSuperclass(String name, List<Integer> params) {
        MapleDeadlockFunction mdf;

        for(MapleDeadlockClass mdc : superClass) {
            mdf = mdc.getTemplateMethod(true, name, params);
            
            if(mdf != null) {
                return mdf;     // the only one structure implementing methods is the "real" superclass.
            }
            
            break;  // first element is the "real" superclass, no need to check others
        }
        
        return null;
    }
    
    public MapleDeadlockFunction getMethod(boolean checkSuper, String name, List<Integer> params) {
        MapleDeadlockFunction ref = null;
        
        for(MapleDeadlockFunction mdf : methods.values()) {
            byte exactState = mdf.hasExactHeading(name, params);
            
            if(exactState == 1) {
                return mdf;
            } else if(exactState == 0) {
                ref = mdf;
            }
        }
        
        if(ref != null) {   // if the exact heading wasn't found, return something similar-typed
            return ref;
        }
        
        return checkSuper ? getMethodOnSuperclass(name, params) : null;
    }
    
    public MapleDeadlockFunction getTemplateMethod(boolean checkSuper, String name, List<Integer> params) {
        for(MapleDeadlockFunction mdf : methods.values()) {
            if(mdf.hasSimilarHeading(name, params)) {
                return mdf;
            }
        }
        
        return checkSuper ? getTemplateMethodOnSuperclass(name, params) : null;
    }
    
    public Set<String> getEnums() {
        return importedEnums;
    }
    
    public void setEnums(Set<String> values) {
        importedEnums.addAll(values);
    }
    
    @Override
    public String toString() {
        String s = "\t" + packName + name;
        s += "\nEXTENDS (1st) / IMPLEMENTS: " + superClass;
        s += "\nIMPORTS: ";
        for(Entry<String, MapleDeadlockClass> c : importList.entrySet()) {
            if(c.getValue() != null) s += c.getValue().getPackageName() + c.getValue().getPathName() + " ";
            else s += "NULL_" + c.getKey() + " ";
        }
        s += "\nMETHODS: ";
        for(MapleDeadlockFunction mdf : methods.values()) {
            s += (mdf.toString() + " ");
        }
        s += "\nFIELD VARS: ";
        for(Entry<String, Integer> e : fields.entrySet()) {
            s += (e.getKey() + " " + e.getValue() + ", ");
        }
        s += "\n\n";
        
        return s;
    }
}
