/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

//Commented for Server compile 
package drugbankHarvester;

import help.Helper;
import java.io.File;
import java.io.IOException;
import java.util.Date;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import mongoConnect.MongoDatasetConnector;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.xml.sax.SAXException;
import yamlSettings.Settings;

/**
 * Used to harvest DrugBank primary XML file into MongoDB Collection 
 *      input: 
 *          1)  settings.yaml should be available in the project folder containing configurations for the program to run
 *              a)  MongDB details
 *              b)  The file path to the XML file of DrugBank
 *          2)  the XML file of DrugBank to be harvested should be also available in the project folder   
 * 
 * @author tasosnent
 */
public class DrugbankHarvester {
    private static String pathDelimiter = "\\";    // The delimiter in this system (i.e. "\\" for Windows, "/" for Unix)
    private static Settings s; // The settings for the module
    
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {            
        String settingsFile;
        if(args.length == 1){ // command line call
            // TO DO add checks for these values
            System.err.println(" " + new Date().toString() + " \t Creating data-set using settings file : " + args[0]);
            settingsFile = args[0];
        } else { // hardcoded call with default settings file named settings.yaml available in the project main folder
            settingsFile = "." + pathDelimiter + "settings.yaml";
        }
        //Load settings from file
        s = new Settings(settingsFile);

        // The file to be harvested
//        String XMLfile = "D:\\42 IASIS tmp files\\Structured Resources\\DrugBank\\full database.xml";
//        String XMLfile = "D:\\42 IASIS tmp files\\Structured Resources\\DrugBank\\DB00316.xml";
        String XMLfile = s.getProperty("inputFilePath").toString();
        
        //MongoDB connection
        String host = s.getProperty("mongodb/host").toString();
        int port = (Integer)s.getProperty("mongodb/port");
        String dbName = s.getProperty("mongodb/dbname").toString();     
        String collection = s.getProperty("mongodb/collection").toString();            
        MongoDatasetConnector connector = new MongoDatasetConnector(host, port,dbName,collection);

        //Pparsing XML File with SAX - event driven way 
        //log printing
        System.out.println(" " + new Date().toString() + " Harvest DrugBank " );                                                                                      
        Date start = new Date();
        JSONArray drugs = LoadXMLFile(XMLfile);
        //Write drugs in MongoDB
        for(Object o : drugs){
            JSONObject drug = (JSONObject)o;
            //Write drug interactions in MongoDB
            JSONArray drugsInterctants = Helper.getJSONArray("drug-interaction_drugbank-id", drug);
            String drugId = Helper.getString("drugbank-id", drug);
            if(drugsInterctants != null){
                for(Object i : drugsInterctants){
                    String interactantID = (String)i;
                    JSONObject interaction = new JSONObject();
                    interaction.put("p", "INTERACTS_WITH");
                    interaction.put("s", drugId);
                    interaction.put("o", interactantID);
                    connector.add(interaction.toJSONString());
                }
            }
        }
        
        //Write in JSON file
//        JSONObject ob = new JSONObject();
//        ob.put("drugs", drugs);
//        Helper.writeJsonFile("harvested.json",ob);
        Date end = new Date();
        Helper.printTime(start, end, "harvesting");

    }
    
    /** Event driven indexing of drugbank XML data
     *      parses XMLfile and calls handlers for XML elements found
     * @param processor
     * @param fileName 
     */
    public static void load(JsonArrayProcessor processor, String fileName) {
        SAXParserFactory factory = SAXParserFactory.newInstance();

        try {
            SAXParser parser = factory.newSAXParser();
            File file = new File(fileName);
            DrugbankHandler mlcHandler = new DrugbankHandler(processor);

            parser.parse(file, mlcHandler);

        } catch (ParserConfigurationException | SAXException | IOException e) {
            System.out.println(" " + new Date().toString() + " load(...) method caught a " + e.getClass() + " with message: " + e.getMessage());
        }
    }
    
    /**
     *  Reads an XML file of DrugBank entries and parses them (the event driven way) into a JSONArray  
     * @param XMLfile    The XML file with DrugBank entries to parse
     * @return           a JSONArray with all entries read from the XML files as JSON objects
     */    
    public static JSONArray LoadXMLFile(String XMLfile){
        JSONArray articleList = new JSONArray(); // here will be stored the articles
        JsonArrayProcessor processor = new JsonArrayProcessor(articleList);
        // Load articles from XMLfile to JSONArray
        load(processor,XMLfile);
        return articleList;
    }
}
