/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package main.java.com.amco.amcoticketmt.utils;

import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 *
 * @author user
 */
public class MTProperties {
   private static final Logger logger = LogManager.getLogger(MTProperties.class);

    private static Map<PropName, Object> map = new HashMap();

    public static final String FILE = "AmcoTicketOBUProperties.properties";

    static {
        InputStreamReader reader = null;
        try {
            reader = new InputStreamReader(MTProperties.class.getResourceAsStream(FILE), "UTF8");
            Properties prop = new Properties();

            prop.load(reader);

            for (Object key : prop.keySet()) {
                final String propNameS = (String) key;
                final PropName propName = PropName.valueOf(propNameS);
                map.put(propName, prop.getProperty(propNameS));
            }
        } catch (Exception ex) {
            logger.error("Error while reading properties: " + ex.getMessage(), ex);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception er) {
                    logger.error(er);
                }
            }
        }
    }

    public static String getString(PropName propName, String defaultValue) {
        if (map.containsKey(propName)) {
            return (String) map.get(propName);
        } else {
            return defaultValue;
        }
    }

    public static Integer getInt(PropName propName, Integer defaultValue) {
        if (map.containsKey(propName)) {
            return Integer.valueOf((String) map.get(propName));
        } else {
            return defaultValue;
        }
    }

    public static Short getShort(PropName propName, Short defaultValue) {
        if (map.containsKey(propName)) {
            return Short.valueOf((String) map.get(propName));
        } else {
            return defaultValue;
        }
    }

    public static Boolean getBoolean(PropName propName, Boolean defaultValue) {
        if (map.containsKey(propName)) {
            return Boolean.valueOf((String) map.get(propName));
        } else {
            return defaultValue;
        }
    }

    public static Double getDouble(PropName propName, Double defaultValue) {
        if (map.containsKey(propName)) {
            return Double.valueOf((String) map.get(propName));
        } else {
            return defaultValue;
        }
    }

    public static String getString(PropName propName) {
        return getString(propName, null);
    }

    public static Integer getInt(PropName propName) {
        return getInt(propName, null);
    }

    public static Short getShort(PropName propName) {
        return getShort(propName, null);
    }

    public static Boolean getBoolean(PropName propName) {
        return getBoolean(propName, null);
    }

    public static Double getDouble(PropName propName) {
        return getDouble(propName, null);
    }
}

