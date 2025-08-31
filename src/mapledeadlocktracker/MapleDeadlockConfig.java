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

import java.util.Properties;

/**
 *
 * @author RonanLana
 */
public class MapleDeadlockConfig {
    
    private static Properties prop;
        
    public static String getProperty(String key) {
        return prop.getProperty(key);
    }
    
    public static void loadProperties(Properties properties) {
        prop = properties;
    }
    
}
