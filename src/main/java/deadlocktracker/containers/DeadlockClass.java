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

public class DeadlockClass {
    String name;
    String pathName;
    String packName;
    boolean isAbstract;
    
    DeadlockClassType type;
    Set<String> importedEnums = new HashSet<>();
    
    public enum DeadlockClassType {
        CLASS, ENUM, INTERFACE
    }
    
    DeadlockClass parent;
    
    List<Integer> typeMasks = new LinkedList<>();   // holds abstract types from the class
    Set<Integer> typeMaskSet;
    
    List<String> supName;
    List<DeadlockClass> superClass = new LinkedList<>();    // technically there is only one superclass, but for the purposes of this code interfaces also accounts here.
    Map<String, DeadlockClass> privateClasses = new HashMap<>();
    
    Map<String, DeadlockClass> importList = new HashMap<>();   // holds solely the class name
    Map<String, List<String>> fullImportList = new HashMap<>();
    
    List<DeadlockFunction> methods = new ArrayList<>();
    Map<String, Integer> fields = new HashMap();
    
    public DeadlockClass(DeadlockClassType ctype, String className, String packageName, String classPathName, List<String> superNames, boolean abstracted, DeadlockClass parentClass) {
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
        return type.equals(DeadlockClassType.ENUM);
    }
    
    public boolean isInterface() {
        return type.equals(DeadlockClassType.INTERFACE);
    }
    
    public DeadlockClass getParent() {
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
    
    public void updateImport(String s, String full, DeadlockClass mdc) {
        importList.put(DeadlockStorage.getCanonClassName(mdc), mdc);
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
    
    public List<DeadlockClass> getImportClasses() {
        List<DeadlockClass> list = new LinkedList<>();
        for (DeadlockClass c : importList.values()) {
            if (c != null) list.add(c);
        }
        
        return list;
    }
    
    public List<Pair<String, DeadlockClass>> getImports() {
        List<Pair<String, DeadlockClass>> list = new LinkedList<>();
        
        for(Entry<String, DeadlockClass> e : importList.entrySet()) {
            list.add(new Pair<>(e.getKey(), e.getValue()));
        }
        
        return list;
    }
    
    public DeadlockClass getImport(String s) {
        return importList.get(s);
    }
    
    public void addPrivateClass(String s, DeadlockClass mdc) {
        privateClasses.put(s, mdc);
    }
    
    public DeadlockClass getPrivateClass(String s) {
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
    
    public void addClassMethod(DeadlockFunction classMethod) {
        methods.add(classMethod);
    }
    
    public DeadlockFunction getMethodByName(String name, List<Integer> params) {
        for (DeadlockFunction mdf : methods) {
            if (mdf.getName().contentEquals(name) && mdf.getParameters().equals(params)) {
                return mdf;
            }
        }
        
        return null;
    }
    
    public List<DeadlockFunction> getMethods() {
        return new ArrayList<>(methods);
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
    
    public List<DeadlockClass> getSuperList() {
        return superClass;
    }
    
    public void addSuper(DeadlockClass s) {
        if(s != null && s != this) {
            superClass.add(s);
        }
    }
    
    public List<List<Integer>> getArgsFromMethodName(String name) {
        List<List<Integer>> ret = new LinkedList();
        
        for(DeadlockFunction mdf : methods) {
            if(mdf.getName().contentEquals(name)) {
                ret.add(mdf.getParameters());
            }
        }
        
        return ret;
    }
    
    public DeadlockFunction getMethodOnSuperclass(String name, List<Integer> params) {
        DeadlockFunction mdf;

        for(DeadlockClass mdc : superClass) {
            mdf = mdc.getMethod(true, name, params);
            
            if(mdf != null) {
                return mdf;     // the only one structure implementing methods is the "real" superclass.
            }
            
            break;  // first element is the "real" superclass, no need to check others
        }
        
        return null;
    }
    
    public DeadlockFunction getTemplateMethodOnSuperclass(String name, List<Integer> params) {
        DeadlockFunction mdf;

        for(DeadlockClass mdc : superClass) {
            mdf = mdc.getTemplateMethod(true, name, params);
            
            if(mdf != null) {
                return mdf;     // the only one structure implementing methods is the "real" superclass.
            }
            
            break;  // first element is the "real" superclass, no need to check others
        }
        
        return null;
    }
    
    public DeadlockFunction getMethod(boolean checkSuper, String name, List<Integer> params) {
        DeadlockFunction ref = null;
        
        for(DeadlockFunction mdf : methods) {
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
    
    public DeadlockFunction getTemplateMethod(boolean checkSuper, String name, List<Integer> params) {
        for(DeadlockFunction mdf : methods) {
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
        for(Entry<String, DeadlockClass> c : importList.entrySet()) {
            if(c.getValue() != null) s += c.getValue().getPackageName() + c.getValue().getPathName() + " ";
            else s += "NULL_" + c.getKey() + " ";
        }
        s += "\nMETHODS: ";
        for(DeadlockFunction mdf : methods) {
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
