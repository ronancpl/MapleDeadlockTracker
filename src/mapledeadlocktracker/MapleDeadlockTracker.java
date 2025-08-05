/*
    This file is part of the HeavenMS (MapleSolaxiaV2) MapleStory Server
    Copyleft (L) 2017 RonanLana

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as
    published by the Free Software Foundation version 3 as published by
    the Free Software Foundation. You may not use, modify or distribute
    this program under any other version of the GNU Affero General Public
    License.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
package mapledeadlocktracker;

import mapledeadlocktracker.containers.MapleDeadlockStorage;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

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
        MapleDeadlockGraphMaker.dumpGraph();
    }
}
