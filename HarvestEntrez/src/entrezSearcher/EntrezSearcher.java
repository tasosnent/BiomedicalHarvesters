/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package entrezSearcher;

//add httpcomponents-client-4.5.1 jars
// httpcore-4.4.3.jar
// httpclient-4.5.1.jar
// commons-logging-1.2.jar
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;

/**
 * Interface to use Entrez search and fetch e-utilities
 *      Executes queries to PubMed or PubMed Central via Entrez API
 *      Can handle queries containing lists of PMIDs 
 * @author tasosnent
 */
public class EntrezSearcher {
    private final static boolean debugMode = true; //Enables printing of messages for normal functions

    private String XmlFolderPath = null;
    private String db = ""; // "pubmed" or "pmc" 
    private String retFormat = ""; // "xml" or "json" 
    private ArrayList <String> queries = new ArrayList();    
    private int step = 200;    
    private int fileCount = 0; //used by fetchpubmed to name xmlFiles
    private int searchIdsLimit = 200;// search in pubmed for a subset of pmids to not have problem with pubmed query size
        //limit 200 is for pmc, for pubmed much miber limits may be used.
           
    final static Pattern webEnvPattern = Pattern.compile("<WebEnv>(.*?.)</WebEnv>");
    final static Pattern queryKeyPattern = Pattern.compile("<QueryKey>(.*?.)</QueryKey>");
    final static Pattern countPattern = Pattern.compile("<Count>(.*?.)</Count>"); 
    
    private ArrayList <String> webEnvs = new ArrayList();
    private ArrayList <String> queryKeys = new ArrayList();
    private ArrayList <String> counts = new ArrayList();
    private int countTotal = 0;
    private Date time = null;
    
    private ArrayList <String> searchIDs = null;    // Ids to restric seach in these
    
    private BufferedWriter writer = null;
    
    /**
     * Basic Constructor of Searcher by IDs
     *      with custom DB and return type for results
     * 
     * @param db            Default pubmed. Entrez DB to search and fetch from (i.e. pubmed or pmc)
     * @param retFormat     Default xml.    format in which results should be returned (i.e. xml or Medline, see https://www.ncbi.nlm.nih.gov/books/NBK25499/table/chapter4.T._valid_values_of__retmode_and/?report=objectonly) 
     * @param query         Query to search for 
     * @param searchIds     PMIDs or PMCIDs to search for
     * @param folderPath    folder to write and read files
     */
    public EntrezSearcher (String db, String retFormat, String query, ArrayList <String> searchIds, String folderPath){
        this.db = db;
        // For search by IDs searchIdsLimit is used to break list of IDs into parts
        if(this.db.equals("pmc"))
        {// For PMC services the limit is stricly 200 IDs
            this.searchIdsLimit = 200;
        } else {// For PubMed a larger limit of some thousands can be used
            this.searchIdsLimit = 4000;            
        }
        this.retFormat = retFormat;
        this.searchIDs = searchIds;
        addQueries(query);
        this.XmlFolderPath = folderPath;
    }
    /**
     * Constructor of Searcher by IDs
     *      with default DB and return type for results (pubmed and xml)
     * 
     * @param query         Query to search for 
     * @param searchIds     PMIDs or PMCIDs to search for
     * @param folderPath    folder to write and read files
     */    
    public EntrezSearcher (String query, ArrayList <String> searchIds, String folderPath){
        this("pubmed","xml", query,searchIds,folderPath);
    }   
    /**
     * Constructor of Searcher a query
     *      with default DB and return type for results (pubmed and xml)
     * 
     * @param query         Query to search for
     * @param folderPath    folder to write and read files
     */
    public EntrezSearcher (String query, String folderPath){
        this("pubmed","xml", query,folderPath);
    }
    /**
     * Constructor of Searcher a query
     *      with custom DB and return type for results
     * 
     * @param db            Entrez DB to search and fetch from (i.e. pubmed or pmc)
     * @param retFormat     Format in which results should be returned (i.e. xml or json)
     * @param query         Query to search for
     * @param folderPath    folder to write and read files
     */
    public EntrezSearcher (String db, String retFormat, String query, String folderPath){
        this.db = db;
        this.retFormat = retFormat;
        addQuery(query);
        this.XmlFolderPath = folderPath;
    }    
    
    /**
     * When searching with a list of ids the size limit of query may be restrictive,
     *      so if id list is big, break it into more queries.
     * 
     * @param query 
     */
    private void addQueries(String query){
        ArrayList <String> idsFullSet = new ArrayList <String>(searchIDs);
        while(idsFullSet.size() > 0){
            ArrayList <String> idsSmallSet = new ArrayList <String>(); 
            if(debugMode) {
                System.out.println("\t Pmids not used in query available : " + idsFullSet.size());
            }
            idsSmallSet.addAll(idsFullSet.subList(0,Math.min(getSearchIdsLimit(), idsFullSet.size())));
            idsFullSet.removeAll(idsSmallSet);
            String idsConstrain ="";
                idsConstrain = idsConstrain(idsSmallSet);
            addQuery(query,idsConstrain);
        }
    }
    
    /**
     * Adds a single query into queries set
     * 
     * @param query     The query
     */
    private void addQuery(String query){
        this.queries.add( query);
    }
    
    /**
     * Adds a single query into queries set
     * 
     * @param query         The query (should and with "AND" to fit with ids list constraint.
     * @param idsConstrain  The "query part" containing a list of ids to be concatenated. 
     */
    private void addQuery(String query, String idsConstrain){
        this.queries.add( idsConstrain + query);
    } 
    
    /**
     * Fetch results for all queries and save them into files
     */
    public void fetch(){    
        for(int i = 0; i < this.queries.size(); i++){
            fetch(i);
        }
    }
    /**
     * Fetch results of "query i" and save them into files.
     * 
     * @param i     the index of query to fetch results for
     */
    private void fetch(int i){
        // if Search completes succesfully
        if(searchOK(i)){// proceed to fetch
            int docs = Integer.parseInt(counts.get(i));
            int start = 0;
            // while end of results not reached
            // i.e. stops when the last document of the last batch (start + step) is grater than the total count of docs
            while(start < docs){
                //get next part of results
                fetchPart(i,start,step);
                start += step;
            }
        } else { // print a message for wrong search of specific query
            // Log printing
            if(debugMode) {
                System.out.println(" " + new Date().toString() + " "  + db + " Fetch > No succesful search for query : " + this.queries.get(i));
            }
        }
    }
    
    /**
     * Fetch a specified part from results of the specified query.
     *      write data fetched in a file (e.g. pubmed1.xml)
     * 
     * @param i         index of the query to fetch result for
     * @param start     first article to fetch      : &retstart=0
     * @param step      how many articles to fetch  : &retmax=10
     */
    private void fetchPart(int i,int start, int step){
    //Commented for Server compile 
       String currentFile = this.XmlFolderPath + "\\" + db + (fileCount++) + "." + this.retFormat;
    //Uncommented for Server compile 
    //       String currentFile = this.XmlFolderPath + "/" + db + (fileCount++) + ".xml";

        // Log printing
        if(debugMode) {
            System.out.println(" " + new Date().toString() + " "  + db + " Fetch > Fetching articles " 
                    + start + " to " +  ( start + step )
                    + " \n\t writing to : " + currentFile);
        }
        try {
            //write here response from Pubmed
            writer = new BufferedWriter(new FileWriter(currentFile));
            
            //assemble the esearch URL
            String base = "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/";

            String url = base + "efetch.fcgi?db="+db+"&query_key=" + queryKeys.get(i)
                    + "&WebEnv=" + webEnvs.get(i) + "&usehistory=y&rettype=" + retFormat + "&retmode=" + retFormat
                    + "&retstart=" + start + "&retmax=" + step;
            // Log printing
            if(debugMode) {
                System.out.println(" "  + db + "  Search > Fetch url : " + url);
            }
                    
            // send GET request
            RequestConfig globalConfig = RequestConfig.custom().setCookieSpec(CookieSpecs.IGNORE_COOKIES).build();
            CloseableHttpClient client = HttpClients.custom().setDefaultRequestConfig(globalConfig).build();

            HttpGet request = new HttpGet(url);
            request.addHeader(base, base);

            HttpResponse response;
            response = client.execute(request);
            BufferedReader rd = new BufferedReader( new InputStreamReader(response.getEntity().getContent()));
            StringBuffer result = new StringBuffer();
            String line = "";

            while ((line = rd.readLine()) != null) {
                writer.append(line);
                writer.newLine();
            }
            
            writer.flush();
            writer.close();            
        } catch (IOException ex) {
        // Log printing
            if(debugMode) {
                System.out.println(" " + new Date().toString() + " "  + db + " Fetch > IO Exception " + ex.getMessage());
            }
        }            
    }
    
    /**
     * Search for all searcher's queries in specified DB using Entrez API and "history parameter"
     *      Using POST method for search
     */
    public void search() {
        try {
            for(int i = 0; i < this.queries.size(); i++){
                    search(i);
                    // in case of error stop
                    if(!this.searchOK(i)){
                        System.out.println("\t" + new Date().toString() + " "  + db + " Search for rest queries cancelled, because failed for query " + i + " : " + this.queries.get(i));
                        break;
                    }
            }
        } catch (UnsupportedEncodingException ex) {
            Logger.getLogger(EntrezSearcher.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    /**
     * Search for i-th query in specified DB using Entrez API and "history parameter"
     *      Using POST method for search
     *      Only called by search() iteratively
     * @param i
     * @throws UnsupportedEncodingException 
     */
    private void search(int i) throws UnsupportedEncodingException{
        //assemble the esearch URL
        String base = "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/";
        String url = base + "esearch.fcgi?db=" + db;
        
        // Log printing
        if(debugMode) {
            System.out.println(" " + new Date().toString() + " "  + db + " Search > URL :" + url);
        }        

        // send POST request
        RequestConfig globalConfig = RequestConfig.custom().setCookieSpec(CookieSpecs.IGNORE_COOKIES).build();
        CloseableHttpClient client = HttpClients.custom().setDefaultRequestConfig(globalConfig).build();
        
        HttpPost  request = new HttpPost (url);
        List <NameValuePair> nvps = new ArrayList <>();
        nvps.add(new BasicNameValuePair("term", queries.get(i)));
        nvps.add(new BasicNameValuePair("usehistory", "y"));
        request.setEntity(new UrlEncodedFormEntity(nvps));

        // Log printing
        if(debugMode) {
            System.out.println(" " + new Date().toString() + " "  + db + " Search > query : " + queries.get(i));
        }                
        
        CloseableHttpResponse response;
        try {
            response = client.execute(request);
            BufferedReader rd = new BufferedReader( new InputStreamReader(response.getEntity().getContent()));
            StringBuffer result = new StringBuffer();
            String line = "";

            Matcher matcher = null;
            while ((line = rd.readLine()) != null) {
                //parse WebEnv, QueryKey and Count (# records retrieved)
                if(line.startsWith("<eSearchResult>")){
//                    System.out.println(line);
                    //Get webEnv
                    matcher = webEnvPattern.matcher(line);
                    if (matcher.find())
                    {
                        webEnvs.add(i,matcher.group(1));
                    } else {
                        System.out.println("No WebEnv found in " + line);
                    }
                    // get QueryKey
                    matcher = queryKeyPattern.matcher(line);
                    if (matcher.find())
                    {
                        queryKeys.add(i,matcher.group(1));
                    } else {
                        System.out.println("No QueryKey found in " + line);
                    }
                    // get Count
                    matcher = countPattern.matcher(line);
                    if (matcher.find())
                    {
                        counts.add(i,matcher.group(1));
                    } else {
                        System.out.println("No Count found in " + line);
                    }
                } else if (line.startsWith("<ERROR>")){
                    System.out.println(" " + new Date().toString() + " "  + db + " Search > ERROR :" + line);
                    System.out.println(line);
                }
//                just for testing
//                result.append(line);
//                if(debugMode) {
//                    System.out.println(" \t\t" + line );
//                }
            }
            response.close();
            
             // Log printing
            if(debugMode) {
                if(counts.size() > 0 && queryKeys.size() > 0 && webEnvs.size() > 0 ){
                    System.out.println(" " + new Date().toString() + " "  + db + " Search > Search data :"  
                        + "\t count > " + counts.get(i)
                        + ",\t queryKey > " + queryKeys.get(i)
                        + ",\t WebEnv > " + webEnvs.get(i));
                } else {
                    System.out.println(" " + new Date().toString() + " "  + db + " Search > Search data : No count and/or queryKey and/or WebEnv found "  );
                }
            }  
        } catch (IOException ex) {
            // Log printing
            if(debugMode) {
                System.out.println(" " + new Date().toString() + " "  + db + "  Search > Exception :" + ex.getMessage());
            }  
        }  
    }
   
    /**
    * Create "ids constrain" part for a query
    *       For PMCIDs removes "PMC" part, if contained, because Entrez expects only the number in the search
    * 
    * @param uids   List of ids (PMIDs or PMCIDs) to search for (in PubMed or PubMedCentral respectively)
    * @return 
    */ 
    private String idsConstrain(ArrayList <String> uids){
        String query = "";
        if(uids.size() > 0){
            query = " ( " 
            // If uids used are PMC ids, remove PMC part - not recognized by Entrez utilites! 
                + uids.get(0).replace("PMC", "") 
                + "[UID] ";

            for(int i = 1; i < uids.size() ; i++){
                query += "OR " 
                // If uids used are PMC ids, remove PMC part - not recognized by Entrez utilites! 
                    + uids.get(i).replace("PMC", "") 
                    + "[UID] ";
            }
            query += ") ";   
        }
        return query;        
    }
   
    /**
     * Check that the Specified search was successful 
     * @param i     index of search query (part)
     * @return      true for successful search, false otherwise
     */
    public boolean searchOK(int i){
        if(counts.size() > 0 && counts.get(i) != null &&
            queryKeys.size() > 0 && queryKeys.get(i) != null &&
            webEnvs.size() > 0 && webEnvs.get(i) != null)
            return true;
        else
            return false;
    }

    public int getQueriesSize(){
        return this.queries.size();
    }
    
    /**
     * Get query key for the specified (sub)query
     * @param i index of the specified (sub)query 
     * @return the querykey string
     */
    public String getQueryKey(int i){
        return this.queryKeys.get(i);
    }
    
    /**
     * Get webenv for the specified (sub)query
     * @param i index of the specified (sub)query 
     * @return the webenv string
     */
    public String getWebEnv(int i){
        return this.webEnvs.get(i);
    }
    
    /**
     * Get specified (sub)query
     * @param i index of the specified (sub)query 
     * @return the (sub) query string
     */
    public String getQuery(int i){
        return this.queries.get(i);
    }
 
    /**
     * Get count of returned results for (sub-)query i
     * @param i index of the specified (sub)query to get count for
     * @return 
     */
    public int getCount(int i){
        int c = 0;
        if(this.counts.size() >= i){
            c = Integer.parseInt(this.counts.get(i));
        }
        return c;
    }    
    
    /**
     * Get total count of returned results
     * @return 
     */
    public int getCount(){
        int c = 0;
        for(String s : this.counts){
            c += Integer.parseInt(s);
        }
        return c;
    }
    
    /**
     * @return the searchIdsLimit
     */
    public int getSearchIdsLimit() {
        return searchIdsLimit;
    }

    /**
     * @param aSearchIdsLimit the searchIdsLimit to set
     */
    public void setSearchIdsLimit(int aSearchIdsLimit) {
        searchIdsLimit = aSearchIdsLimit;
    }   
    
}
