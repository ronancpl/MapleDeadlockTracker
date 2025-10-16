/*
    This file is part of the DeadlockTracker detection tool
    Copyleft (L) 2025 RonanLana

    GNU General Public License v3.0

    Permissions of this strong copyleft license are conditioned on making available complete
    source code of licensed works and modifications, which include larger works using a licensed
    work, under the same license. Copyright and license notices must be preserved. Contributors
    provide an express grant of patent rights.
 */
package mapledeadlocktracker;

import org.antlr.v4.runtime.tree.*;

import mapledeadlocktracker.containers.MapleDeadlockEntry;
import mapledeadlocktracker.containers.MapleDeadlockLock;
import mapledeadlocktracker.containers.MapleDeadlockStorage;
import mapledeadlocktracker.source.JavaReader;
import mapledeadlocktracker.source.CSharpReader;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;

/**
 *
 * @author RonanLana
 */
public class MapleDeadlockTracker {

	private static boolean isSourceFile(String fName) {
		List<String> list = MapleDeadlockConfig.getAssociatedFileExtensions();

		if (list.isEmpty()) {
			return true;
		}

		for (String ext : list) {
			if (fName.endsWith(ext)) {
				return true;
			}
		}

		return false;
	}

	private static void listSourceFiles(String directoryName, List<String> files) {
		File directory = new File(directoryName);

		// get all the files from a directory
		File[] fList = directory.listFiles();
		for (File file : fList) {
			if (file.isFile()) {
				String fName = file.getName();
				if(isSourceFile(fName)) {
					files.add(file.getAbsolutePath());
				}
			} else if (file.isDirectory()) {
				listSourceFiles(file.getAbsolutePath(), files);
			}
		}
	}

	private static MapleDeadlockStorage parseSourceProject(String directoryName, MapleDeadlockGraphMaker g, ParseTreeListener reader) {
		List<String> fileNames = new ArrayList<>();
		listSourceFiles(directoryName, fileNames);

		for(String fName : fileNames) {
			System.out.println("Parsing '" + fName + "'");
			g.parseSourceFile(fName, reader);
		}
		System.out.println("Project file reading complete!\n");

		MapleDeadlockStorage ret;
		if (reader instanceof JavaReader) {
			ret = ((JavaReader) reader).compileProjectData();
		} else if (reader instanceof CSharpReader) {
			ret = ((CSharpReader) reader).compileProjectData();
		} else {
			ret = null;
		}

		return ret;     // finally, updates the storage table with relevant associations
	}

	private static void loadPropertiesFile() {
		Properties prop = new Properties();
		String fileName = "config.cfg";
		try (FileInputStream fis = new FileInputStream(fileName)) {
			prop.load(fis);
		} catch (IOException ex) {
			ex.printStackTrace();
		}

		MapleDeadlockConfig.loadProperties(prop);
	}

	private static Map<Integer, String> getGraphLockNames(MapleDeadlockGraphMaker g) {
		Map<Integer, String> r = new HashMap<>();

		Map<String, MapleDeadlockLock> m = g.getGraphLocks();
		for (Entry<String, MapleDeadlockLock> em : m.entrySet()) {
			if(em.getValue() != null) r.put(em.getValue().getId(), em.getKey());
		}

		return r;
	}

	private static void executeDeadlockTracker() {
		loadPropertiesFile();

		MapleDeadlockGraphMaker g = MapleDeadlockConfig.getGraphMakerFromProperty(("language"));
		ParseTreeListener l = MapleDeadlockConfig.getSourceParserFromProperty(("language"));

		String directoryName = MapleDeadlockConfig.getProperty("src_folder");
		if (l instanceof CSharpReader) ((CSharpReader) l).setSourceDirPrefixPath(directoryName);

		MapleDeadlockStorage md = parseSourceProject(directoryName, g, l);
		System.out.println("Project parse complete!\n");

		MapleDeadlockGraph mdg = g.generateSourceGraph(md);
		System.out.println("Project graph generated!\n");

		Map<Integer, String> r = getGraphLockNames(g);
		Set<MapleDeadlockEntry> mds = new MapleDeadlockGraphCruiser().runSourceGraph(mdg, md, r);
		MapleDeadlockGraphResult.reportDeadlocks(mds, r);
                
		//g.dumpGraph();
	}

	public static void main(String[] args) {
		executeDeadlockTracker();
	}

}
