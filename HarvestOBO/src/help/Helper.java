/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
//Commented for Server compile 
package help;


import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import static java.lang.System.exit;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;
// Add gson-2.0.jar
import com.google.gson.GsonBuilder;
import java.text.SimpleDateFormat;
// Add json-simple-1.1.1.jar
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.ParseException;
// Add commons-io\commons-io\2.4\commons-io-2.4.jar
import static org.apache.commons.io.FileUtils.copyFile;


/**
 *
 * @author tasosnent
 * 
 * Various Helper methods
 * 
 */
public class Helper {
    public static boolean debugMode = true;
    /**
     * Reads a JSON file into a JSONObject
     * 
     * @param filePath  The path for the file to read
     * @return          The JSONOnject read
     */
    public static JSONObject readJsonFile(String filePath) {
        JSONObject json = null;
            // open input stream filePath.txt for reading purpose.
            BufferedReader br;
        try {
            br = new BufferedReader( new FileReader(filePath));
            //  Parse json object into a String to be parsed 
            json = (JSONObject) JSONValue.parseWithException(br);
            br.close();
        } catch (FileNotFoundException ex) {
            printMessage(" " + new Date().toString() + " caught a FileNotFoundException for file " + filePath,2);
        } catch (IOException ex) {
            printMessage(" " + new Date().toString() + " caught a IOException for file " + filePath,2);
        } catch (ParseException ex) {
            printMessage(" " + new Date().toString() + " caught a ParseException for file " + filePath,2);
            printMessage(ex.getMessage(),3);
            ex.printStackTrace();
        }
        return json;
    } 
  
    /**
     * Write a JSON Object into a file
     * 
     * @param filePath      Path to write the file
     * @param subbmission   JSON object to be written
     */
    public static void writeJsonFile(String filePath, JSONObject subbmission){
        try {
            BufferedWriter jsonWriter = new BufferedWriter(new FileWriter(filePath));
//            jsonWriter.append(subbmission.toJSONString());
            // print human readable using GsonBuilder library
            // disableHtmlEscaping to not replace sepcial characters
            jsonWriter.append(new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(subbmission));
            jsonWriter.flush();
            jsonWriter.close();
        } catch (IOException ex) {
            printMessage(" " + new Date().toString() + " caught a IOException for file " + filePath,2);
        }
    }
     
    /** print a message in specified depth
     * 
     * @param msg       String message
     * @param depth     number of tabs to intend
     */
    public static void printMessage(String msg, int depth ){
        for(int i = 0; i < depth; i++)
             System.out.print("\t");
        System.out.println(msg);
    }
    
    /** 
     * return "JSONArray of key provided" or null, in case of absence or wrong type
     * 
     * @param key   Name of field to search
     * @param o     JSONObject to search in 
     * @return      JSONArray found or null
     */
    public static JSONArray getJSONArray(String key, JSONObject o){
        JSONArray a = null;
        if(o.containsKey(key)){
           if(o.get(key) instanceof JSONArray){
               a = (JSONArray) o.get(key);
           } else {
//                printMessage("Warning: element with key "+key+" not a valid JSONArray. in " + o.toJSONString());               
           }
        }// else { printMessage("Error: element with key "+key+" not present in " + o.toJSONString());}
        return a;
    }
    
    /**
     * Get an Array from a JSONObject written in given JSON file.
     * @param key       Name of the array to get
     * @param jsonFile  Path of the file to read
     * @return          "JSONArray of key provided" or null, in case of absence or wrong type 
     */
    public static JSONArray getJSONArray(String key,String jsonFile){
        JSONObject ob = readJsonFile(jsonFile);
        return getJSONArray(key, ob);
    }
    
    /** Return "JSONObject of key provided" or null, in case of absence or wrong type
     * 
     * @param key   Name of field to search
     * @param o     JSONObject to search in 
     * @return      JSONObject found or null
     */
    public static JSONObject getJSONObject(String key, JSONObject o){
        JSONObject a = null;
        if(o.containsKey(key)){
           if(o.get(key) instanceof JSONObject){
               a = (JSONObject) o.get(key);
           } else {
                printMessage("Warning: element with key "+key+" not a valid JSONObject. in " + o.toJSONString(),2);               
           }
        } //else {  printMessage("Warning: element with key "+key+" not present in " + o.toJSONString()); }
        return a;
    }
    
    /** Return "String of key provided" or null, in case of absence or wrong type
     * 
     * @param key   Name of field to search
     * @param o     JSONObject to search in 
     * @return      String found or null
     */
    public static String getString(String key, JSONObject o){
        String a = null;
        if(o.containsKey(key)){
           if(o.get(key) instanceof String){
               a = (String) o.get(key);
           } else {
                printMessage("Warning: element with key "+key+" not a valid String. in " + o.toJSONString(),2);               
           }
        } //else {  printMessage("Warning: element with key "+key+" not present in " + o.toJSONString()); }
        return a;
    }   

    /** 
     * Return "Integer value of key provided" or null, in case of absence or wrong type
     * 
     * @param key   Name of field to search
     * @param o     JSONObject to search in 
     * @return      Integer found or null
     */
    public static Integer getInteger(String key, JSONObject o){
        if(o.containsKey(key)){
           if(o.get(key) instanceof Integer){
              int a = (Integer) o.get(key);
                return a;
           } else {
                printMessage("Warning: element with key "+key+" not a valid int. in " + o.toJSONString(),2);               
           }
        } //else {  printMessage("Warning: element with key "+key+" not present in " + o.toJSONString()); }
        return null;
    }  
    
    /** Return "Double value of key provided" or null, in case of absence or wrong type
     * 
     * @param key   Name of field to search
     * @param o     JSONObject to search in 
     * @return      Double found or null
     */
    public static Double getDouble(String key, JSONObject o){
        if(o.containsKey(key)){
           if(o.get(key) instanceof Double){
              double a = (Double) o.get(key);
                return a;
           } else {
                printMessage("Warning: element with key "+key+" not a valid Double. in " + o.toJSONString(),2);               
           }
        } //else {  printMessage("Warning: element with key "+key+" not present in " + o.toJSONString()); }
        return null;
    }   

    /** Return "String value of key provided" or null, in case of absence or wrong type
     * 
     * @param key   Name of field to search
     * @param o     JSONObject to search in 
     * @return      String found or null
     */
    public static String getAnyType(String key, JSONObject o){
        if(o.containsKey(key)){
            return o.get(key)+"";           
        } //else {  printMessage("Warning: element with key "+key+" not present in " + o.toJSONString()); }
        return null;
    }  
    
    /** 
     * Return "Float value of key provided" or null, in case of absence or wrong type
     * 
     * @param key   Name of field to search
     * @param o     JSONObject to search in 
     * @return      Float found or null
     */
    public static Float getFloat(String key, JSONObject o){
        if(o.containsKey(key)){
           if(o.get(key) instanceof Float){
              float a = (Float) o.get(key);
                return a;
           } else {
                printMessage("Warning: element with key "+key+" not a valid float. in " + o.toJSONString(),2);               
           }
        } //else {  printMessage("Warning: element with key "+key+" not present in " + o.toJSONString()); }
        return null;
    }    
    
    /**
     * Create a folder in the specified path
     * 
     * @param folder Path to create the folder
     * @return  true if folder created, false otherwise
     */
    public static boolean createFolder(String folder){
        if (!(new File(folder)).mkdirs()) {
           printMessage("Warning!: Folder "+ folder +" allready Exists",2);   
           return false;
        } else {
//            printMessage(" Folder "+ folder +" succesfully created");            
           return true;
        }
    }

    /** 
     * Get a list of all files (FilePaths) contained in the folder (and sub-folders)
     * 
     * @param folderPath    the path of a folder with files 
     * @return              a ArrayList of all files(FilePaths) contained in the folder (and sub-folders)
     */
    public static ArrayList <Path> readSubmissionsFolder(String folderPath){
    final ArrayList <Path> files = new ArrayList <> ();
    Path path = Paths.get(folderPath);
    if (Files.isDirectory(path)) {
        try {
            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    files.add(file);
//                    System.out.println(file);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ex) {
            //log printing
            printMessage(" " + new Date().toString() + " Exception walking in directory : " + path,2); 
        }
    } else {
        //log printing
        printMessage(" " + new Date().toString() + " not a Directory : " + path,2);              
    }
    return files;
}
    
    /** 
     * Get a list of all files (FileNames) contained in the folder (and sub-folders)
     * 
     * @param folderPath    the path of a folder with files 
     * @return              a ArrayList of all files(FileNames) contained in the folder (and sub-folders)
     */
    public static ArrayList <String> readSubmissionsFileNames(String folderPath){
         ArrayList <String> s = new ArrayList <>();
         ArrayList <Path> paths = readSubmissionsFolder(folderPath);
            for(Path p : paths){
                s.add(p.toFile().getName().replace(".json", ""));
//                System.out.println(p.toFile().getName().replace(".json", ""));
            }
         return s;
     }
    
    /**
     * Get a list of files in a folder
     * @param basePath
     * @return 
     */
    public static ArrayList<Path> getFilesInFolder(String basePath)   {
        final ArrayList<Path> files = new ArrayList();
        Path path = Paths.get(basePath);
        if (Files.isDirectory(path)) {
            try {
                Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        files.add(file);
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException ex) {
                System.out.println(" " + new Date().toString() + " Exception : " + ex.getLocalizedMessage());                         
            }
            
        } else {
            //log printing
            System.out.println(" " + new Date().toString() + " not a Directory : " + path);              
        }
        return files;
    }  

    /** Read a file with a list of IDs (one ID per line) and return a list of them 
     * 
     * @param filePath  Path to file containing a list of IDs (one ID per line)
     * @return          ArrayList of IDs
     */
    public static ArrayList<String> readListIds(String filePath) {
        ArrayList <String> Ids = new ArrayList <String>();
        String json = "";
        try{
            // open input stream filePath.txt for reading purpose.
            BufferedReader br = new BufferedReader( new FileReader(filePath));
            String line = "";
            while ((line = br.readLine()) != null ) { 
                line = line.trim();
                if(!line.equals("")){
                    Ids.add(line);                    
                }
            }
        } catch (FileNotFoundException ex) {
            System.out.println(" \t Error\t: readListIds FileNotFoundException : " + ex.getLocalizedMessage());
            exit(100);
        } catch (UnsupportedEncodingException ex) {
            System.out.println(" \t Error\t: readListIds UnsupportedEncodingException : " + ex.getLocalizedMessage());
            exit(100);
        } catch (IOException ex) {
            System.out.println(" \t Error\t: readListIds IOException : " + ex.getLocalizedMessage());
            exit(100);
        }
        return Ids;
    }  
    
    /**
     * Convert JSONArray of Strings into a String
     * 
     * @param StrArr    Array of Strings
     * @return          String of concatenated Strings
     */
    public static String JSONArrayToString(JSONArray StrArr){
        return JSONArrayToString(StrArr, "");
    }
    
    /**
     * Convert JSONArray of Strings into a String
     * 
     * @param StrArr    Array of Strings
     * @param delimiter The delimiter string to be used
     * @return          String of concatenated Strings
     */
    public static String JSONArrayToString(JSONArray StrArr, String delimiter){
        String str = new String();
            for(int i=0 ; i < StrArr.size(); i++){
                if(i != 0){
                    str += delimiter;
                }
                str += StrArr.get(i).toString();
            }
        return str;
    }
    
    /**
     * Convert Array of Strings into a String
     * 
     * @param StrArr    Array of Strings
     * @return          String of concatenated Strings
     */
    public static String StringArrayToString(String[] StrArr){
        String str = new String();
            for(int i=0 ; i < StrArr.length; i++){
                str += StrArr[i];
            }
        return str;
    }
    
    /**
     * Convert Array of Strings into a JSONArray of Strings
     * @param StrArr    Array of Strings
     * @return          corresponding JSONArray of Strings
     */
    public static JSONArray StringArrayToJSONList(String[] StrArr){
        JSONArray List = new JSONArray();
            for(int i=0 ; i < StrArr.length; i++){
                List.add(StrArr[i]);
            }
        return List;
    }
    
    /**
     * Prints a message of time passed for a job
     * 
     * @param start Date object before the job
     * @param end   Date object after the job
     * @param job   a String describing the job
     */
    public static void printTime(Date start, Date end, String job){
         long miliseconds = end.getTime() - start.getTime();
                String totalTime = String.format("%02d:%02d:%02d", 
                    TimeUnit.MILLISECONDS.toHours(miliseconds),
                    TimeUnit.MILLISECONDS.toMinutes(miliseconds) - 
                    TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(miliseconds)),
                    TimeUnit.MILLISECONDS.toSeconds(miliseconds) - 
                    TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(miliseconds)));
                //log printing
                System.out.println(" " + new Date().toString() + " Total " + job + " time : " + totalTime); 
    }
       
    /**
     *  Copies a file from one path to another
     * 
     * @param sourceFile    Source file to be copied
     * @param targetFolder  Folder to copy file there
     */
    public static void copyFileToFolder(String sourceFile, String targetFolder){
        try { 
                File source = new File(sourceFile);
                copyFile(source, new File(targetFolder + "\\" + source.getName()));
            } catch (IOException ex) {
                System.out.println(" " + new Date().toString() + " Exception copying " + sourceFile + " to folder " + targetFolder );
                System.out.println(ex.getMessage() );
            }
    }
    
    /**
     * Convert a JSONArray of articles (as JSONObjects) into a Corresponding HashMap (with PMID as key)
     * @param list      JSONArray of articles (as JSONObjects with PMID as key) 
    * @return           Corresponding HashMap with PMID as key
     */
    public static HashMap <String,JSONObject> articlesJSONArraytoHashMap(JSONArray list){
        HashMap <String,JSONObject> articles = new HashMap();
//        System.out.println(" " + new Date().toString() + " articlesJSONArraytoHashMap called with a list of size : " + list.size());

        for(Object o : list){
            JSONObject jo = (JSONObject)o;

            if(jo.containsKey("pmid")){
                String pmid = Helper.getAnyType("pmid", jo);
                articles.put(pmid, jo);
            } else {
                System.out.println(" " + new Date().toString() + " Error : object without pmid found in Array : " + jo);
            }
        }
//                System.out.println(" " + new Date().toString() + " articlesJSONArraytoHashMap finished with a map of size : " + articles.size());
        return articles;
    }
      
    /**
     * Get current date in specified format for PubMed Queries
     * @return      a date in format of PubMed queries
     */
    public static String dateNow(){
        String date = "";
        SimpleDateFormat format1 = new SimpleDateFormat("yyyy/MM/dd");
            Date now = new Date();
            date = format1.format(now);
        return date;
    }
    
    /**
     * Write a String into a file
     * 
     * @param filePath      Path to write the file
     * @param content       String value to be written
     */
    public static void writeFile(String filePath, String content){
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(filePath));
//            jsonWriter.append(subbmission.toJSONString());
            // print human readable using GsonBuilder library
            // disableHtmlEscaping to not replace sepcial characters
            writer.append(content);
            writer.flush();
            writer.close();
        } catch (IOException ex) {
            printMessage(" " + new Date().toString() + " caught a IOException writing in file " + filePath,2);
            ex.printStackTrace();
        }
    }
    
}
