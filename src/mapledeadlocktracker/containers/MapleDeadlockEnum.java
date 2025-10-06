/*
    This file is part of the DeadlockTracker detection tool
    Copyleft (L) 2025 RonanLana

    GNU General Public License v3.0

    Permissions of this strong copyleft license are conditioned on making available complete
    source code of licensed works and modifications, which include larger works using a licensed
    work, under the same license. Copyright and license notices must be preserved. Contributors
    provide an express grant of patent rights.
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
		super(DeadlockClassType.ENUM, className, packageName, classPathName, superNames, false, parent);
	}

	public void addEnumItem(String item) {
		enumItems.add(item);
	}

	public boolean containsEnumItem(String item) {
		return enumItems.contains(item);
	}

	public HashSet<String> getEnumItems() {
		return new HashSet<>(enumItems);
	}
}
