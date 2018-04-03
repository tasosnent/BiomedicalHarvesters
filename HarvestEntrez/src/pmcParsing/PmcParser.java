/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pmcParsing;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Date;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import help.Helper;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.Reader;
import java.util.logging.Level;
import java.util.logging.Logger;
import mongoConnect.MongoDatasetConnector;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 *
 * @author tasosnent
 * 
 * Converts PMIDs to PMCIDs and vice versa
 * Create PMC test sets and other useful things
 * 
 */
public class PmcParser {
    // pmcJsonArray is a temporary varuable
    private JSONArray pmcJsonArray =  new JSONArray(); // here store all articles in json form to be written. Used by xmlsToAJson only and should be emptied
    private String JsonOutputFolder = ""; 
    
    /**
     * Basic constructor for a PmcParser
     * @param JsonOutputFolder          Folder to write JSON (PMC)test sets into
     */
    public PmcParser(String JsonOutputFolder){
        this.JsonOutputFolder = JsonOutputFolder;
        //create folders
        Helper.createFolder(this.JsonOutputFolder);
    }
 
    /**
     * Parse all PMC XML files contained in folder "basePath"
     *      Load JSON objects in pmcJsonArray variable 
     *      Write them into a MongoDB collection (targetFile)
     * 
     * @param basePath              Folder to read PMC XML files from
     * @param targetCollection      MongoDB collection (targetFile)
     */
    public void xmlsToMongo(String basePath, MongoDatasetConnector targetCollection) {
         Path path = Paths.get(basePath);
        if (Files.isDirectory(path)) {
            try {
                Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        System.out.println("\t> " + file.getFileName() );
                        //Parse XML file
                        LoadPMCXMLToMongo(file.toString(),targetCollection );
                        return FileVisitResult.CONTINUE;
                    }
                });
                
            } catch (IOException ex) {
                //log printing
                System.out.println(" " + new Date().toString() + " PmcConverter > IOExcepion" + ex.getMessage()); 
            }
        } else {
            //log printing
            System.out.println(" " + new Date().toString() + " not a Directory : " + path);              
        }
    }  
     
    /**
     * Parse all PMC XML files contained in folder "basePath"
     *      Load JSON objects in pmcJsonArray variable 
     *      Write them into a JSON file (targetFile)
     * 
     * @param basePath      Folder to read PMC XML files from
     * @param targetFile    File to write JSON articles into 
     */
    public void xmlsToAJson(String basePath, String targetFile) {
         Path path = Paths.get(basePath);
        if (Files.isDirectory(path)) {
            try {
                Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        System.out.println("\t> " + file.getFileName() );
                        //Parse XML file
                        LoadPMCXML(file.toString());
                        return FileVisitResult.CONTINUE;
                    }
                });
                
                //Write JSON file
                JSONObject result = new JSONObject();
                result.put("documents", this.pmcJsonArray);
                Helper.writeJsonFile(targetFile,result);
                // Empty the list to be used again
                this.emptyJSONArticles();
            } catch (IOException ex) {
                //log printing
                System.out.println(" " + new Date().toString() + " PmcConverter > IOExcepion" + ex.getMessage()); 
            }
        } else {
            //log printing
            System.out.println(" " + new Date().toString() + " not a Directory : " + path);              
        }
    }  
     
    /**
     * Add given JSON objects to pmcJsonArray list
     * @param pmcJsons    the JSON objects to add (corresponding to articles)
     */
    public void addJSONArticles(JSONArray pmcJsons){
        this.pmcJsonArray.addAll(pmcJsons);
    }
    
    /**
     * Replace list of PMCIDs with a new one 
     */
    public void emptyJSONArticles(){
        this.pmcJsonArray =  new JSONArray();
    }    

    /**
     * Parse all PMC XML files contained in folder "basePath"
     *      Write corresponding JSON files in folder "targetPath" 
     * 
     * @param basePath      Folder to read PMC XML files from
     * @param targetPath    Folder to write JSON files in (created if not exists)
     */
    public void xmlsToJsons(String basePath, String targetPath) {
        Path path = Paths.get(basePath);
        if (Files.isDirectory(path)) {
            // If target file does not exist, create it
            Path tPath = Paths.get(targetPath);
            if(!Files.isDirectory(tPath)){
                Helper.createFolder(targetPath);
            }
            try {
                Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        System.out.println("\t> " + file.getFileName() );
                        //Parse XML file
                        JSONObject result = readPMCXML(file.toString());
                        //Write JSON file
                        Helper.writeJsonFile(targetPath + "\\" + file.getFileName().toString().replace(".xml", ".json"),result );
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException ex) {
                Logger.getLogger(PmcParser.class.getName()).log(Level.SEVERE, null, ex);
            }
        } else {
            //log printing
            System.out.println(" " + new Date().toString() + " not a Directory : " + path);              
        }
    }  

    /**
     *  Reads an XML file of PMC articles and parses them (the event driven way) into a JSONArray  
     *      loads articles into targetCollection in MongoDB
     * @param pmcXMLfile    The XML file with PMC articles to parse
     */  
    private void LoadPMCXMLToMongo(String pmcXMLfile, MongoDatasetConnector targetCollection) {
        JSONArray articleList = new JSONArray(); // here will be stored the articles
        JsonArrayProcessor processor = new JsonArrayProcessor(articleList);
        // Load articles from XMLfile to JSONArray
        load(processor,pmcXMLfile);
        // Write all the articles to MongoDB
        for(Object o : articleList){
            JSONObject jo = (JSONObject)o;
            targetCollection.add(jo.toJSONString());            
        }
    }
    
    /**
     *  Reads an XML file of PMC articles and parses them (the event driven way) into a JSONArray  
     *      loads articles into pmcJsonArray variable
     * @param pmcXMLfile    The XML file with PMC articles to parse
     */    
    public void LoadPMCXML(String pmcXMLfile){
        JSONArray articleList = new JSONArray(); // here will be stored the articles
        JsonArrayProcessor processor = new JsonArrayProcessor(articleList);
        // Load articles from XMLfile to JSONArray
        load(processor,pmcXMLfile);
        this.pmcJsonArray.addAll(articleList);
    }
    
    /**
     *  Reads an XML file of PMC articles and parses them (the event driven way) into a JSONArray  
     * 
     * @param pmcXMLfile    The XML file with PMC articles to parse
     * @return              A JSONObject with articles in a JSONArray under the field "articles"
     */    
    public JSONObject readPMCXML(String pmcXMLfile){
        JSONArray articleList = new JSONArray(); // here will be stored the articles
        JsonArrayProcessor processor = new JsonArrayProcessor(articleList);
        // Load articles from XMLfile to JSONArray
        load(processor,pmcXMLfile);
//        System.out.println(articleList.size() + " articles read");
        JSONObject result = new JSONObject();
        result.put("articles", articleList);
        return result;
    }
    
    /**
     * Event driven parsing of PMC XML data
     *      Creates a JSON object for each article in file "fileName" and add it to the list of processor
     * 
     * @param processor     JsonArrayProcessor object, contains a list to be filled with JSON objects corresponding to articles
     * @param fileName      PMC XML file 
     */
    public void load(JsonArrayProcessor processor, String fileName) {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        try {
            SAXParser parser = factory.newSAXParser();
            PmcHandler pmcHandler = new PmcHandler(processor);
            File file = new File(fileName);
            InputStream inputStream= new FileInputStream(file);
            Reader reader = new InputStreamReader(inputStream,"UTF-8");
            InputSource is = new InputSource(reader);
            is.setEncoding("UTF-8");
            parser.parse(is, pmcHandler);

        } catch (ParserConfigurationException | SAXException | IOException e) {
            e.printStackTrace();
        }
    }    
    
    /**
     *  Reads a JSON file containing a JSONArray of IDs and returns an ArrayList with them.
     * 
     * @param inputFile     JSON file with JSONArray of IDs under "documents" field 
     * @return              List of IDs
     */
    public ArrayList<String> readJSONArrayIDs(String inputFile){
        JSONObject pmcidListObj = Helper.readJsonFile(inputFile);
        ArrayList<String> pmcids = new ArrayList <>();
        pmcids.addAll(Helper.getJSONArray("documents", pmcidListObj));
        return pmcids;
    }
   
}
