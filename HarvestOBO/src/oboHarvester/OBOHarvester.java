/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

//Commented for Server compile 
package oboHarvester;

import help.Helper;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import mongoConnect.MongoDatasetConnector;
// Add Tasks\00 Logs etc\Libs\json-simple-1.1.1.jar
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import yamlSettings.Settings;

/**
 * This is a harvester for OBO ontologies
 *      Harvests only hierarchical relations from the given OBO file (i.e. ISA)
 *      Have been tested for GO, DOID, Jochem and MESH but should be easily extended to work for any OBO formated ontology/hierarchy
 *      A special handle for DOID is done, where only terms with CUIs are harvested
 *      
 *      input: 
 *          1)  settings.yaml should be available in the project folder containing configurations for the prohramm to run
 *              a)  MongDB details
 *              b)  the name of the OBO resource to be harvested (one of doid, GO, Mesh_2018, Jochem etc)
 *          2)  The OBO file to be harvested should be also available in the project folder (e.g. doid.obo, GO.obo etc)  
 * 
 * @author tasosnent
 */
public class OBOHarvester {
    private static String pathDelimiter = "\\";    // The delimiter in this system (i.e. "\\" for Windows, "/" for Unix)
//  A list of all objects (concepts) read frm the OBO file
    private static JSONArray termList = new JSONArray();
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

//            String oboName = "Jochem";
//            String oboName = "doid";
//            String oboName = "GO";
//            String oboName = "mesh_2018";
            String oboName = s.getProperty("inputOBOName").toString(); // The OBO resource to be harvested
            String baseFolder = s.getProperty("baseFolder").toString(); // The OBO resource to be harvested
            String inputObo = baseFolder + pathDelimiter + oboName + ".obo";
        
        //Pparsing XML File with SAX - event orientent way 
            //log printing
            System.out.println(" " + new Date().toString() + " OutPut : " + oboName + " collection in MongoDB");                                                                                      
            Date start = new Date();
            JSONArray relations = harvestOBO(oboName,inputObo);
            
            //MongoDB connection
            String host = s.getProperty("mongodb/host").toString();
            int port = (Integer)s.getProperty("mongodb/port");
            String dbName = s.getProperty("mongodb/dbname").toString();        
            
            //Write relations in MongoDB
            MongoDatasetConnector connector = new MongoDatasetConnector(host, port,dbName,oboName);
            for(Object o : relations){
                JSONObject jo = (JSONObject)o;
                connector.add(jo.toJSONString());
            }

            Date end = new Date();
            long miliseconds = end.getTime() - start.getTime();
            String totalTime = String.format("%02d:%02d:%02d", 
                TimeUnit.MILLISECONDS.toHours(miliseconds),
                TimeUnit.MILLISECONDS.toMinutes(miliseconds) - 
                TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(miliseconds)),
                TimeUnit.MILLISECONDS.toSeconds(miliseconds) - 
                TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(miliseconds)));
            //log printing
            System.out.println(" " + new Date().toString() + " Total time of harvesting (hh:mm:ss) : " + totalTime);  
        
    }
    
    /**
     *  Event driven harvesting of OBO file 
     *      Harvests hierarchical relations (isa) only
     *      For DOID only concepts with UMLS CUIs available are considered
     * @param oboName       The name of the OBO ontology (foe disease Ontology this name should start in "doid" because special handling is done
     * @param inputObo      The file of the OBO ontology
     * @return              The list of relations
     */
    public static JSONArray harvestOBO(String oboName,String inputObo){
        JSONArray relations = new JSONArray();

        Date start = new Date();
            //test print code
            System.out.println("Harvesting file '" + inputObo + "'...");
            loadTerms(inputObo);
            if(oboName.startsWith("doid")){
                // For DO get CUI relations only
                relations = OBOHarvester.getCUIRelations(termList);
            } else {
                // For other resources get relations in native source IDs
                relations = OBOHarvester.getRelations(termList);
            }
            
            Date end = new Date();
            //log printing
            System.out.println(TimeUnit.MILLISECONDS.toSeconds(end.getTime() - start.getTime()) + " seconds ");

        return relations;
    }
   
    /**
     * Load all data (concepts) from the OBO file (oboPath) to the JSONArray list (termList)
     * @param inputObo      The file of the OBO ontology
     */
    public static void loadTerms(String inputObo){
               
        BufferedReader brObo = null;
        try {
            // open input stream for reading obo file.
            brObo = new BufferedReader( new FileReader(inputObo));
            String line = null;
            int linenum = 0;
            //      fieldName (.*?.) is Not greedy because some comments contain ":" character
            //      fieldValue is greedy to contain all remaining character up to the end of line.
            Pattern pairPattern = Pattern.compile("(.*?.): (.*.)"); //"field: value" pair  
            //      synonyms are contained in quotes (synonym not greedy for cases additional info contains quoted)
            Pattern synonymPattern = Pattern.compile("\"(.*?.)\"(.*.)"); //       
            //      jochem names contained in quotes (name greedy for case that name contains quote)
            Pattern namePattern = Pattern.compile("\"(.*.)\""); //   
            //      DO xrefs
            Pattern xrefPattern = Pattern.compile("(.*?.):(.*.)"); //   source -> id
            //      DO isa
            Pattern isaPattern = Pattern.compile("(.*?.) ! (.*.)"); //   source -> id
            boolean inTerm = false;
            String fieldName = null;
            String fieldValue = null;
            //Lucene document to be indexed
            JSONObject termObj = new JSONObject();
            Matcher matcher = null;
            Matcher synonymMatcher = null;
            Matcher nameMatcher = null;
            Matcher xrefMatcher = null;
            Matcher isaMatcher = null;
            while ((line = brObo.readLine()) != null ) {

                line = line.trim();
                //Look for a [Term] line
                if(line.startsWith("[Term]")){ //is a [Term] line
                    //a new Obo term begins
                    inTerm = true;
                } else { //not a [Term] line
                    if(inTerm){ // inside a [Term] block, look for "field: value" pairs...
                        matcher = pairPattern.matcher(line);
                        if (matcher.find()) {// "field: value" pair found
                            fieldName = matcher.group(1);
                            fieldValue = matcher.group(2);

                            //clean synonyms:
                            // i.e. "Granuloma of lacrimal passages (disorder)" EXACT [SNOMEDCT_2005_07_31:82836006] > Granuloma of lacrimal passages (disorder)
                            switch (fieldName) {
                                case "synonym":
                                    synonymMatcher = synonymPattern.matcher(fieldValue);
                                    if (synonymMatcher.find()){
                                        fieldValue = synonymMatcher.group(1);
                                    }   break;
                                case "name":
                                    nameMatcher = namePattern.matcher(fieldValue);
                                    if (nameMatcher.find()){
                                        fieldValue = nameMatcher.group(1);
                                    }   break;
                                case "xref":
                                    xrefMatcher = xrefPattern.matcher(fieldValue);
                                    if (xrefMatcher.find()){
                                        fieldName = xrefMatcher.group(1);
                                        fieldValue = xrefMatcher.group(2);
                                    }   break;
                                case "is_a":
                                    isaMatcher = isaPattern.matcher(fieldValue);
                                    if (isaMatcher.find()){
                                        fieldValue = isaMatcher.group(1);
                                    }   break;
                                case "alt_id":
                                    isaMatcher = isaPattern.matcher(fieldValue);
                                    if (isaMatcher.find()){
                                        fieldValue = isaMatcher.group(1);
                                        if(termObj.containsKey("alt_id")){
                                            
                                        }
                                    }   break;
                                default:
                                    break;
                            }
                            // Alternative ids may be more than one, so handle separately to keep them all in an Arraylist
                            if(!fieldName.equals("alt_id")){
                                termObj.put(fieldName,fieldValue);
                            } else {
                                JSONArray altids;
                                if(termObj.containsKey(fieldName)){
                                    altids = (JSONArray) termObj.get(fieldName);
                                } else {
                                    altids = new JSONArray();
                                }
                                altids.add(fieldValue);
                                termObj.put(fieldName,altids);
                            }
                        } else {// no "field: value" pair found.
                            /* End of Term block! */
                            inTerm = false;

                            //write current object to the JSON list
                            termList.add(termObj);

                            //nullify data for the next [Term] block
                            termObj = new JSONObject();
                            fieldName = null;
                            fieldValue = null;
                        }
                    } // else : outside [Term] block, just go to next line untill a [Term] line apears
                }
                //End of conditionals, count lines just for reference
                linenum++;
                if(linenum%100000 == 0) {
                    System.out.println(" \t" + new Date().toString() + " In line : " + linenum);
                }
            }   
            brObo.close();
        } catch (FileNotFoundException ex) {
            Logger.getLogger(OBOHarvester.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException e) {
            //log printing
            System.out.println(" " + new Date().toString() + " caught a " + e.getClass() + " with message: " + e.getMessage());     
        } finally {
            try {
                brObo.close();
            } catch (IOException ex) {
                Logger.getLogger(OBOHarvester.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }    
    
    /**
     * Returns a JSONArray with relations between CUIs to be inserted in knowledge base
     *      This is used for DOID only, where some concepts have a "UMLS_CUI" field.
     *      Currently harvests ISA relations only
     * @param termList
     * @return 
     */
    public static JSONArray getCUIRelations(JSONArray termList){
        // DOID -> CUI
        HashMap <String, String> cuis = new HashMap <>();
        // term JSONObjects
        ArrayList <JSONObject> terms = new ArrayList<>();
        // 
        JSONArray relations = new JSONArray();
        //Find CUIs
        for(int i = 0; i < termList.size(); i++){
            JSONObject t = (JSONObject) termList.get(i);
            if(t.containsKey("UMLS_CUI"))
            {
                cuis.put(Helper.getAnyType("id", t), Helper.getAnyType("UMLS_CUI", t));
                terms.add(t);
                if(t.containsKey("alt_id")){
                    JSONArray altids = Helper.getJSONArray("alt_id", t);
                    for(int j = 0; j < altids.size(); j++){
                        String altid = (String) altids.get(j);
                        cuis.put(altid, Helper.getAnyType("UMLS_CUI", t));
                    }
                }
            }
        }
        System.out.println (" cuis: " + cuis.size());
        System.out.println (" terms: " + terms.size());

        JSONObject tmpRelation;
        // Find parents
        for(JSONObject t : terms){
            if(t.containsKey("is_a")){
                String parent = Helper.getAnyType("is_a", t);
                String doid = Helper.getAnyType("id", t);
                if(cuis.containsKey(parent)){
                    tmpRelation = new JSONObject();
                    tmpRelation.put("s", cuis.get(doid));
                    tmpRelation.put("o", cuis.get(parent));
                    tmpRelation.put("p", "IS_A");
                    relations.add(tmpRelation);
                }
            }
        }
    return relations;
    }
    
    /**
     * Returns a JSONArray with relations between concept (in native IDs) to be inserted in knowledge base
     *      Currently harvests ISA relations only
     */
    private static JSONArray getRelations(JSONArray termList) {
        JSONArray relations = new JSONArray();
        JSONObject tmpRelation;
        // Find parents
        for(int i = 0; i < termList.size(); i++){
            JSONObject t = (JSONObject) termList.get(i);
            if(t.containsKey("is_a")){
                String parent = Helper.getAnyType("is_a", t);
                String child = Helper.getAnyType("id", t);
                tmpRelation = new JSONObject();
                tmpRelation.put("s", child);
                tmpRelation.put("o", parent);
                tmpRelation.put("p", "IS_A");
                relations.add(tmpRelation);
            }
        }
    return relations;
    }
}
