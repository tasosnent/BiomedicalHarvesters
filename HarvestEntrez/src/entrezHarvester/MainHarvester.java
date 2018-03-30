/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package entrezHarvester;

import pubmedIndexing.PubmedArticlesIndexer;
import entrezSearcher.EntrezSearcher;
import help.Helper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import mongoConnect.MongoDatasetConnector;
import pmcParsing.PmcParser;
import yamlSettings.Settings;

/**
 * Used to Create Sets of articles relevant to given disease 
 * 
 * @author tasosnent
 */
public class MainHarvester {

    //Hardecoded values
    private final static boolean debugMode = true; //Enables printing of messages for normal functions
    private static String pathDelimiter = "\\";    // The delimiter in this system (i.e. "\\" for Windows, "/" for Unix)
    
    private static Settings s; // The settings for the module
    
    String diseaseMeshID = "";  // MeSH id for the disease to searchNwrite for e.g. D009136 for DMD. Used for semantic searchNwrite.
    String dataSetID ="";            //ID of the current data Set, just for reference
    String query = "";              // the query to be searched
    String source = "";            // the datasource tp be searched
    // if this idList has a value, a PubmedSearcher By Ids will be used 
    String idList = null;             // list of pmids 
    
    String baseFolder = "";     // The folder to store files e.g. harvested XML and JSON output
    //Pubmed paths
    String xmlFolder = "";
    String indexfolder = "";
    String indexPath = "";
    String newPubmedIndexPath = "";
    String jsonFile = "";
    String jsonMeshFile = "";
    private MongoDatasetConnector mongoArticleDataSet; // An object to use for storage of harvested JSON article objects into MongoDB
    private MongoDatasetConnector mongoMeshRelationsDataSet; // An object to use for storage of harvested meshRelations JSON objects into MongoDB

    String logFile = "";

    Date start = null;
    Date end = null;
        
    /**
     * Create a Harvester
     *      default source is pubmed
     * @param dataSetID     An identifier for the specific data sets to be created
     * @param query         The query to be used for searchNwrite in the database
     */  
    public MainHarvester(String dataSetID, String query){
        this.dataSetID = dataSetID;
        this.query = query;
        this.source = "pubmed";
        createFolders();
    }    
    
    /**
     * Create a Harvester
     * @param dataSetID     An identifier for the specific data sets to be created
     * @param query         The query to be used for searchNwrite in the database
     * @param source        A list of IDs to restrict the query
     */
    public MainHarvester(String dataSetID, String query, String source){
        this.dataSetID = dataSetID;
        this.query = query;
        this.source = source;
        createFolders();
    }
    
    /**
     * Create a Harvester
     * @param dataSetID     An identifier for the specific data sets to be created
     * @param query         The query to be used for searchNwrite in the database
     * @param source        The data source to be searched (pubmed or pmc)
     * @param idList        A list of IDs to restrict the query
     */
    public MainHarvester(String dataSetID, String query, String source, String idList){
        this.dataSetID = dataSetID;
        this.query = query;
        this.source = source;
        this.idList = idList;
        createFolders();
    }
    
    /**
     * Creates a Data Set 
     * 
     *  1)  Calls EntrezSearcher to Search PubMed annotated articles with a specific MeSH heading
     *          Documents found are stored in XML files in folder: PrimaryXMLs
     *  2)  Calls PubmedArticlesIndexer to index downloaded documents in a Lucene index
     *          This call creates Lucene_index
     *  3)  Reads Lucene_index and writes a JSON file and/or lucene index with the selected fields.
     *          This program adds (again*) "has abstract" restriction to the data (other restrictions may be added too)
     *          The input-index should also contain those restrictions, as the XML file should be the result of the appropriate pubmedQuery. 
     *          
     *  e.g. "DMD" "Muscular Dystrophy, Duchenne" "2017/05/01"
     *          >   java -jar entrezHarvester.jar "DMD" "Muscular Dystrophy, Duchenne" "2017/05/01"
     * 
     * @param args 
     *      args[0]     DataSetId (e.g. DMD)
     *      args[1]     Mesh heading of the disease (e.g. "Muscular Dystrophy, Duchenne")
     *      args[2]     Date of last update in yyyy/MM/dd format (e.g. "2017/05/01")
     */
    public static void main(String[] args) {
               
        //Load settings from file
        s = new Settings("." + pathDelimiter + "settings.yaml");
//        System.out.println(s.getProperty("baseFolder"));
        // get the last update setting
        String lastUpdateDate = s.getProperty("lastUpdate").toString(); // The date of the lust run of the system e.g. 2018/02/01 to be used to retrieve only newer articles
        String dateSuffix = Helper.dateNow().replace("/", ""); // A suffix based on the current date to be used in dataset id creation
        // Update the last update setting for the next use
        s.setProperty("lastUpdate", Helper.dateNow());


        String datasetID ="";       //ID of the current testSet, this should be the id used by Participants Area Platform database
        String meshTerm = "";       //Weekly list
        // dataSetId -> Mesh term (label)
        HashMap <String,String> dataSetMeSHTerms = new HashMap <> ();
        // dataSetId -> date of last update (yyyy/MM/dd)
        HashMap <String,String> dataSetDateUp = new HashMap <> ();

        if(args.length == 3){ // command line call
            // TO DO add checks for these values
            System.err.println(" " + new Date().toString() + " \t Creating data-set " + args[0] + ",  MeSH : " + args[1] + " and last update date :" + args[2]);
            dataSetMeSHTerms.put(args[0], args[1]);
            dataSetDateUp.put(args[0], args[2]);
        } else { // hardcoded call for three case study diseases
            dataSetMeSHTerms.put("DMD_"+dateSuffix, "Muscular Dystrophy, Duchenne");
            dataSetDateUp.put("DMD_"+dateSuffix, lastUpdateDate);            
            dataSetMeSHTerms.put("LC_"+dateSuffix, "Lung Neoplasms");
            dataSetDateUp.put("LC_"+dateSuffix, lastUpdateDate);
            dataSetMeSHTerms.put("AD_"+dateSuffix, "Alzheimer Disease");
            dataSetDateUp.put("AD_"+dateSuffix, lastUpdateDate);

        }
        // create query based on a MeSH term
            // This is a map so that it can be extensible : 
            // If we want to test multiple queries, just put more elements in queries HahMap
        HashMap <String,String> queries = new HashMap <> ();
        
        //For each test id
        ArrayList<String> dataSetIds = new ArrayList<>();
        dataSetIds.addAll(dataSetMeSHTerms.keySet());
        Collections.sort(dataSetIds);
        String lastUpdate = "";
        for(String dataSetId : dataSetIds){
            datasetID = dataSetId;
            meshTerm = dataSetMeSHTerms.get(dataSetId);
            // If a date of "last update" is provided, narrow the searchNwrite after that date, 
            if(dataSetDateUp.get(dataSetId) != null){
                lastUpdate = dataSetDateUp.get(dataSetId);
            } else{// Else, use a default minimum value
                lastUpdate = "1900/01/01";
            }
                
            // Query for the data set differentiation of functionality based on suffixes
                // source (pubmed or pmc) -> query
            queries.put("pubmed"," " + meshTerm + " [MeSH Terms] "
                + " AND ( hasabstract not hasretractionof not haserratumfor not haspartialretractionof )"
                + " AND ( " + lastUpdate + "[Date - Completion] :" + Helper.dateNow() + "[Date - Completion]) " );  
            queries.put("pmc"," " + meshTerm + " [MeSH Terms] "
//                + " AND ( hasabstract not hasretractionof not haserratumfor not haspartialretractionof )"
                    + "AND (" + lastUpdate + ":" + Helper.dateNow() + "[pmclivedate]) "
                    + "AND open access[filter] AND cc license[filter]" );  
            // Suffix denotes what source is targeted by the query
            
            // Example query : "Dementia" [MeSH Terms] AND ( hasabstract not hasretractionof not haserratumfor not haspartialretractionof )  AND ( 1900/01/01[Date - Completion] :2017/10/11[Date - Completion])
            
            for(String source : queries.keySet()){
//                System.err.println(" - ");
//                System.err.println(" " + new Date().toString() + " \t Prepare " + datasetID + " data set : " );
                // When testing multiple queries, add queryID in datasetID so that different folders will be created
                MainHarvester tsm = new MainHarvester(datasetID, queries.get(source),source);
                // Do steps to create dataSet(s)("Do not stop and wait before indexing", "Do not add extra fields that the official ones")                    
                tsm.doSteps(true);     
//                System.err.println(" - ");
            }      
        }
    }
    
    /**
     * Create data-set for current MainHarvester settings
     * 
     * @param extraFields   Whether to include extraFields (MeshUI) in JSON file. In general should be false.
     */
    public void doSteps( boolean extraFields){
        Date start = new Date();
//        System.err.println(" " + new Date().toString() + " \t Searching and Fetching... ");
        int results = search();
//        int results = 1;
//        System.err.println(" " + new Date().toString() + " \t Parsing and Indexing... ");
        if(results > 0){
            if(this.source.equals("pubmed")){
            indexPubmedDocuments();    
////        System.err.println(" " + new Date().toString() + " \t Selecting and Writing... ");
            createPubmedDataSet(extraFields);
            } else if(this.source.equals("pmc")){
                PmcParser pc = new PmcParser(this.baseFolder);
//              Write all in one JSON file may cause "out of memory exception"
                pc.xmlsToMongo(this.xmlFolder, this.mongoArticleDataSet);
//              Wirte in separate JSON files, memory safe for small "harvesting step"
//                pc.xmlsToJsons(this.xmlFolder, this.jsonFile.replace("JSON.json", "\\PMCjson"));
            }
        }
        Date end = new Date();
//        Helper.printTime(start, end, "creating data set " + dataSetID );
//        if(!this.jsonFile.equals("")){
//        System.err.println(" " + new Date().toString() + " \t " + this.dataSetID + " : \t " + Helper.getPmids(this.jsonFile).size() + " \t " + this.jsonFile);
//        }        
    }
  
    /** Step 0)
     * Create all folders and database connections needed 
     */
    private void createFolders(){
        //tmp code
        baseFolder = s.getProperty("baseFolder") + pathDelimiter + dataSetID + pathDelimiter + source + pathDelimiter; 
//        baseFolder = "." + pathDelimiter + dataSetID + pathDelimiter + source + pathDelimiter; 
        Helper.createFolder(baseFolder);
        //folder with primary XMLs files 
        xmlFolder = baseFolder + "XMLs"; 
        Helper.createFolder(xmlFolder);
        //folder with primary index
        indexfolder =  "Lucene_index";
        indexPath = baseFolder + indexfolder;
        // Files to export final data sets
        // TODO :  remove this newIndex, should not be used probably
        newPubmedIndexPath = baseFolder +"Lucene_index_selected";
        jsonFile = baseFolder + "JSON.json";                            

        //MongoDB connection
        String host = s.getProperty("mongodb/host").toString();
        int port = (Integer)s.getProperty("mongodb/port");
        String dbName = s.getProperty("mongodb/dbname").toString();        
        mongoArticleDataSet = new MongoDatasetConnector(host, port,dbName,dataSetID + "_" + source);
        //For PubMed create addtional dataset for Mesh relations harvested
        if(this.source.equals("pubmed")){
            mongoMeshRelationsDataSet = new MongoDatasetConnector(host, port,dbName,dataSetID + "_" + source + "_MeSH");
            jsonMeshFile = baseFolder + "MESH.json";                            
        }
        // Redirect output to Log file 
//        logFile = baseFolder + "log.txt";
//        try {
//            System.setOut(new PrintStream(logFile));
//        } catch (FileNotFoundException ex) {
//            System.out.println(" " + new Date().toString() + " problem setting " + logFile + " as log file : " + ex.getMessage());
//        }
    }

    /** Step 1)
     * Search PubMed or PMC and write hits 
     *  
     * @return  Number of elements matching the query
     */
    public int search(){
        int dataElementsfound = 0;
        // Search for pubmed or pmc
        EntrezSearcher pc;
        pc = new EntrezSearcher(this.source, "xml", query, xmlFolder); 
        start = new Date();
        pc.search();
        dataElementsfound = pc.getCount();
        if(dataElementsfound > 0){
            pc.fetch();
        }
        end = new Date();
        Helper.printTime(start,end, "Searching " + this.source); 
        return dataElementsfound;
    }
    
    /** Step 2)
     * Index documents into a lucene index
     */
    public void indexPubmedDocuments(){
        PubmedArticlesIndexer tdi = new PubmedArticlesIndexer();
        //Parsing XML File with SAX - event orientent way 
        try{
            //log printing
            System.out.println(" " + new Date().toString() + " indexPath : " + indexPath);                                                                                      
             start = new Date();
                tdi.indexDocs(xmlFolder, indexPath);
             end = new Date();
            Helper.printTime(start, end,"indexing"); 
        } catch (IOException e) {
            //log printing
            System.out.println(" " + new Date().toString() + " caught a (indexing) " + e.getClass() + " with message: " + e.getMessage());
        }  
    }
    
    /** Step 3)
     * Create test data files (JSON file and/or index)
     * @param extraFields whether to include extra fields (publication year and grant) in JSON file or not. For official test sets is normally false.
     */
    public void createPubmedDataSet(boolean extraFields){
        //Restrictions: Only documents with abstract and without DescriptorName should be included to test data
            String query = "+AbstractText:[\\\"\\\" TO *] ";

        dataWriter searcher;
        try {
            searcher = new dataWriter(indexPath,null,null,null,mongoArticleDataSet,mongoMeshRelationsDataSet,extraFields);
            start = new Date();
            try{
                searcher.searchNwrite(query);
            } catch (Exception e) {
                System.out.println(" caught a (searcher.Search) " + e.getClass() + "\n with message: " + e.getMessage());
//                e.printStackTrace();
            }
            end = new Date();
            Helper.printTime(start, end,"Creating Test Data"); 
        } catch (IOException ex) {
                System.out.println(" caught a (searcher.Search) " + ex.getClass() + "\n with message: " + ex.getMessage());
//                ex.printStackTrace();
        }
            
    }

}
