package org.maggus.gpusher;

import javax.swing.*;
import javax.swing.filechooser.FileSystemView;
import java.io.*;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

/**
 * Created by Mike on 2017-06-16.
 */
public abstract class Config {
    public final String APP_DIR;
    public final String CONFIG_FILE_NAME;
    public final boolean global;
    public String configComment;

    public Config(String appTag, boolean global){
        String tag = appTag.toLowerCase().trim();
        tag = tag.replaceAll("[^a-zA-Z0-9-]", "");
        this.global = global;
        APP_DIR = "." + tag;
        CONFIG_FILE_NAME = (!global ? "." : "") + tag + ".properties";
    }

    public File getUserDir() {
        JFileChooser fr = new JFileChooser();
        FileSystemView fw = fr.getFileSystemView();
        return fw.getDefaultDirectory();
    }

    public File getWorkingDir() {
        return new File(Paths.get(".").toAbsolutePath().normalize().toString());
    }

    public void loadConfig() {
        File prefDir;
        if(global)
            prefDir = new File(getUserDir(), APP_DIR);
        else
            prefDir = getWorkingDir();
        if (!prefDir.exists() || !prefDir.isDirectory())
            prefDir.mkdirs();
        File propFile = new File(prefDir, CONFIG_FILE_NAME);
        if (!propFile.exists() || !propFile.canRead()) {
            System.err.println("! no config file " + propFile.getAbsolutePath()); // _DEBUG
            return;
        }
        try {
            Properties props = new Properties();
            props.load(new FileReader(propFile));

            onLoad(props);

        } catch (FileNotFoundException e) {
            System.err.println("! no config file " + propFile.getAbsolutePath() + "; " + e); // _DEBUG
        } catch (IOException e) {
            System.err.println("! can not read config file " + propFile.getAbsolutePath() + "; " + e); // _DEBUG
        }
    }

    public void saveConfig() {
        File prefDir;
        if(global)
            prefDir = new File(getUserDir(), APP_DIR);
        else
            prefDir = getWorkingDir();
        if (!prefDir.exists() || !prefDir.isDirectory())
            prefDir.mkdirs();
        File propFile = new File(prefDir, CONFIG_FILE_NAME);
        try {
            Properties props = new Properties();

            onSave(props);

            props.store(new FileWriter(propFile), configComment);
        } catch (IOException e) {
            System.err.println("! can not save config file " + propFile.getAbsolutePath() + "; " + e); // _DEBUG
        }
    }

    abstract void onLoad(Properties props);

    abstract void onSave(Properties props);

    public static Boolean parseBoolean(String val){
        try {
            return Boolean.parseBoolean(val);
        } catch (Exception ex) {
            return null;
        }
    }

    public static Integer parseInteger(String val){
        try {
            return Integer.parseInt(val);
        } catch (Exception ex) {
            return null;
        }
    }

    public static Long parseLong(String val){
        try {
            return Long.parseLong(val);
        } catch (Exception ex) {
            return null;
        }
    }

    public static void saveValue(Properties props, String tag, Object val){
        if(val == null)
            return;
        else if(val instanceof Boolean)
            props.setProperty(tag, Boolean.toString((Boolean)val));
        else if(val instanceof Long)
            props.setProperty(tag, Long.toString((Long)val));
        else if(val instanceof Integer)
            props.setProperty(tag, Integer.toString((Integer)val));
        else
            props.setProperty(tag, val.toString());
    }

    public static Object loadValue(Properties props, String tag) {
        // TODO: 2017-06-20 figure out a way to store and load values with their type
        return null;
    }

    public static void saveList(Properties props, String tag, List col, ItemSerializer ser){
        tag = tag.toUpperCase().trim();
        int size = col.size();
        props.setProperty(tag + "-SIZE", Integer.toString(size));
        int n = 1;
        for (Iterator iter = col.iterator(); iter.hasNext(); ) {
            Object val = iter.next();
            props.setProperty(tag + "-" + n, ser.writeToString(val));
            n++;
        }
    }

    public static List loadList(Properties props, String tag, ItemSerializer ser) {
        tag = tag.toUpperCase().trim();
        List list = new ArrayList();
        String configItemsNum = props.getProperty(tag + "-SIZE");
        int itemsNum = configItemsNum != null ? Integer.parseInt(configItemsNum) : 0;
        for (int n = 1; n <= itemsNum; n++) {
            String val = props.getProperty(tag + "-" + n);
            if (val == null)
                continue;
            Object v = ser.readFromString(val);
            list.add(v);
        }
        return !list.isEmpty() ? list : null;
    }

    public static interface ItemSerializer<T>{
        String writeToString(T val);
        T readFromString(String str);
    }

    public static String listToString(List values){
        if(values == null || values.isEmpty())
            return "";
        StringBuilder sb = new StringBuilder();
        for(Object val : values){
            if(val == null)
                continue;
            if(sb.length() > 0)
                sb.append(", ");
            sb.append(val.toString());
        }
        return sb.toString().trim();
    }
}
