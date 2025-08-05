/*
    This file is part of the MapleDeadlockTracker detection tool
    Copyleft (L) 2025 RonanLana

    GNU General Public License v3.0

    Permissions of this strong copyleft license are conditioned on making available complete
    source code of licensed works and modifications, which include larger works using a licensed
    work, under the same license. Copyright and license notices must be preserved. Contributors
    provide an express grant of patent rights.
*/
package mapledeadlocktracker.strings;

/**
 *
 * @author RonanLana
 */

import java.util.HashSet;
import java.util.Set;

public class MapleIgnoredTypes {
    private static final Set<String> ignored = new HashSet<>();
    
    static {
        // ------ common data types ----------
        
        ignored.add("java");
        ignored.add("Object");
        ignored.add("Iterator");
        
        ignored.add("Throwable");
        ignored.add("Exception");
        ignored.add("IOException");
        ignored.add("SQLException");
        ignored.add("NullPointerException");
        
        ignored.add("Statement");
        ignored.add("PreparedStatement");
        ignored.add("ResultSet");
        ignored.add("Connection");
        
        ignored.add("Boolean");
        ignored.add("Character");
        ignored.add("Float");
        ignored.add("Double");
        ignored.add("Byte");
        ignored.add("Integer");
        ignored.add("Short");
        ignored.add("Long");
        
        ignored.add("Arrays");
        ignored.add("Collections");
        ignored.add("Collection");
        
        ignored.add("AtomicBoolean");
        ignored.add("AtomicInteger");
        ignored.add("AtomicLong");
        
        ignored.add("SecureRandom");
        ignored.add("Random");
        ignored.add("Point");
        ignored.add("Rectangle");
        
        ignored.add("Console");
        ignored.add("File");
        ignored.add("ByteOutputStream");
        ignored.add("FileOutputStream");
        ignored.add("PrintWriter");
        ignored.add("BufferedWriter");
        ignored.add("BufferedReader");
        
        ignored.add("Runtime");
        ignored.add("Properties");
        ignored.add("Thread");
        ignored.add("Calendar");
        ignored.add("Date");
        ignored.add("DateFormat");
        ignored.add("NumberFormat");
        ignored.add("TimeZone");
        ignored.add("Timestamp");
        ignored.add("TimeUnit");
        ignored.add("Locale");
        ignored.add("Pattern");
        
        ignored.add("Logger");
        ignored.add("ScheduledFuture");
        ignored.add("ScheduledThreadPoolExecutor");
        ignored.add("Runnable");
        ignored.add("Invocable");
        ignored.add("TimerManager");
        
        ignored.add("InetAddress");
        
        // ----- specific data types ---------
        
        ignored.add("SeekableLittleEndianAccessor");
        ignored.add("MaplePacketLittleEndianWriter");
        ignored.add("LittleEndianAccessor");
        ignored.add("LittleEndianWriter");
        ignored.add("ByteArrayOutputStream");
        
        ignored.add("IoSession");
        ignored.add("IoBuffer");
        ignored.add("ProtocolEncoderOutput");
        ignored.add("ProtocolDecoderOutput");
        
        ignored.add("MapleData");
        ignored.add("MapleDataFileEntry");
        ignored.add("MapleDataDirectoryEntry");
        ignored.add("MapleDataProvider");
        
        ignored.add("Node");
        ignored.add("NamedNodeMap");
        ignored.add("XMLDomMapleData");
    }
    
    public static boolean isDataTypeIgnored(String type) {
        return ignored.contains(type);
    }
    
    public static Set<String> getIgnoredTypes() {
        return ignored;
    }
}
