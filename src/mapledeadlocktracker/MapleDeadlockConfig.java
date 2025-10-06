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

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import mapledeadlocktracker.graph.maker.CSharpGraph;
import mapledeadlocktracker.graph.maker.JavaGraph;
import mapledeadlocktracker.source.CSharpReader;
import mapledeadlocktracker.source.JavaReader;

import org.antlr.v4.runtime.tree.ParseTreeListener;

/**
 *
 * @author RonanLana
 */
public class MapleDeadlockConfig {

	private enum Language {

		JAVA("java", JavaGraph.class, new JavaReader()),
		CSHARP("c#", CSharpGraph.class, new CSharpReader()),
		UNSUPPORTED("", null, null);

		private final String name;
		private final Class<? extends MapleDeadlockGraphMaker> graph_class;
		private final ParseTreeListener parser;

		private Language(String name, Class<? extends MapleDeadlockGraphMaker> graph_class, ParseTreeListener parser) {
			this.name = name;
			this.graph_class = graph_class;
			this.parser = parser;
		}

		private String getName() {
			return this.name;
		}

		public Class<? extends MapleDeadlockGraphMaker> getGraphClass() {
			return this.graph_class;
		}

		private ParseTreeListener getParser() {
			return this.parser;
		}

		public static Class<? extends MapleDeadlockGraphMaker> getGraphMakerByName(String name) {
			name = name.trim().toLowerCase();
			for (Language l : Language.values()) {
				if(l.getName().contentEquals(name)) {
					return l.getGraphClass();
				}
			}

			return UNSUPPORTED.getGraphClass();
		}

		public static ParseTreeListener getParserByName(String name) {
			name = name.trim().toLowerCase();
			for (Language l : Language.values()) {
				if(l.getName().contentEquals(name)) {
					return l.getParser();
				}
			}

			return UNSUPPORTED.getParser();
		}
	}

	private static Properties prop;
	private static List<String> extensions;

	public static String getProperty(String key) {
		return prop.getProperty(key);
	}

	public static void loadProperties(Properties properties) {
		prop = properties;
		loadAssociatedFileExtensions();
	}

	public static void loadAssociatedFileExtensions() {
		extensions = new ArrayList<>();
		for (String sp : getProperty("extensions").split(",")) {
			sp = sp.trim();
			if (!sp.isEmpty()) {
				extensions.add("." + sp);    			
			}
		}
	}

	public static MapleDeadlockGraphMaker getGraphMakerFromProperty(String key) {
		try {
			return Language.getGraphMakerByName(getProperty(key)).newInstance();
		} catch (IllegalAccessException | InstantiationException | NullPointerException e) {
			e.printStackTrace();
			return null;
		}
	}

	public static ParseTreeListener getSourceParserFromProperty(String key) {
		try {
			return Language.getParserByName(getProperty(key));
		} catch (NullPointerException e) {
			e.printStackTrace();
			return null;
		}
	}

	public static List<String> getAssociatedFileExtensions() {
		return extensions;
	}
}
