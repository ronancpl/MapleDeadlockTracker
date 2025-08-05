/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package mapledeadlocktracker.containers;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 *
 * @author RonanLana
 */
public class MapleDeadlockEnum extends MapleDeadlockClass {
    Set<String> enumItems = new HashSet();
    
    public MapleDeadlockEnum(String className, String packageName, String classPathName, List<String> superNames, MapleDeadlockClass parent) {
        super(MapleDeadlockClassType.ENUM, className, packageName, classPathName, superNames, false, parent);
    }
    
    public void addEnumItem(String item) {
        enumItems.add(item);
    }
    
    public boolean containsEnumItem(String item) {
        return enumItems.contains(item);
    }
}
