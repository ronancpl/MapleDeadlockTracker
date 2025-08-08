/*
    This file is part of the MapleDeadlockTracker detection tool
    Copyleft (L) 2025 RonanLana

    GNU General Public License v3.0

    Permissions of this strong copyleft license are conditioned on making available complete
    source code of licensed works and modifications, which include larger works using a licensed
    work, under the same license. Copyright and license notices must be preserved. Contributors
    provide an express grant of patent rights.
*/
package mapledeadlocktracker;

import mapledeadlocktracker.containers.MapleDeadlockEntry;
import mapledeadlocktracker.containers.MapleDeadlockStorage;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;

import javalexer.*;
import javaparser.*;

/**
 *
 * @author RonanLana
 */
public class MapleDeadlockTracker {

    /**
     * @param args the command line arguments
     */
    
    private static void parseJavaFile(String fileName, MapleDeadlockReader listener) {
        try {
            JavaLexer lexer = new JavaLexer(CharStreams.fromFileName(fileName));
            CommonTokenStream commonTokenStream = new CommonTokenStream(lexer);
            JavaParser parser = new JavaParser(commonTokenStream);
            ParseTree tree = parser.compilationUnit();
            
            ParseTreeWalker walker = new ParseTreeWalker();
            walker.walk(listener, tree);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private static void listJavaFiles(String directoryName, List<String> files) {
        File directory = new File(directoryName);

        // get all the files from a directory
        File[] fList = directory.listFiles();
        for (File file : fList) {
            if (file.isFile()) {
                String fName = file.getName();
                if(fName.endsWith(".java")) {
                    files.add(file.getAbsolutePath());
                }
            } else if (file.isDirectory()) {
                listJavaFiles(file.getAbsolutePath(), files);
            }
        }
    }
    
    private static MapleDeadlockStorage parseJavaProject(String directoryName) {
        List<String> fileNames = new ArrayList<>();
        listJavaFiles(directoryName, fileNames);
        
        MapleDeadlockReader reader = new MapleDeadlockReader();
        
        for(String fName : fileNames) {
            System.out.println("Parsing '" + fName + "'");
            parseJavaFile(fName, reader);
        }
        System.out.println("Project file reading complete!\n");
        
        return MapleDeadlockReader.compileProjectData();     // finally, updates the storage table with relevant associations
    }
    
    public static void main(String[] args) {
        MapleDeadlockStorage md = parseJavaProject("../HeavenMS/src");
        System.out.println("Project parse complete!\n");
        
        System.out.println(md);
        
        MapleDeadlockGraph mdg = MapleDeadlockGraphMaker.generateSourceGraph(md);
        System.out.println("Project graph generated!\n");
        
        Set<MapleDeadlockEntry> mds = new MapleDeadlockGraphCruiser().runSourceGraph(mdg);
        MapleDeadlockGraphResult.reportDeadlocks(mds);
        
        /*
        MapleDeadlockGraphMaker.dumpGraph();
                
        Map<String, Map<String, MapleDeadlockClass>> map = md.getPublicClasses();
        
        MapleDeadlockClass tracker = map.get("mapledeadlocktracker.graph.").get("MapleDeadlockGraphLock");
        MapleDeadlockClass parser = map.get("mapledeadlocktracker.").get("MapleDeadlockReader");
        MapleDeadlockClass grapher = map.get("mapledeadlocktracker.").get("MapleDeadlockGraphMaker");
        
        System.out.println(md.locateClass("MapleDeadlockGraphNode", tracker).getName());
        System.out.println(md.locateClass("JavaParserBaseListener", parser).getName());
        System.out.println(md.locateClass("MapleDeadlockFunction", grapher).getName());
        */
        
        
    }
}
