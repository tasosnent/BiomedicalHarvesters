/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

//Commented for Server compile 
package pmcParsing;

import help.Helper;
import java.util.ArrayList;
import java.util.Date;
import java.util.Stack;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Reads a PMC XML file and creates corresponding JSON objects stored in JsonArrayProcessor "processor"
 *  Includes funding-group data.
 * 
 *      Unfortunately, no actual structure is implied by the DTD* therefore conflict handling will be used only when problems identified.
 *          * https://dtd.nlm.nih.gov/ncbi/pmc/articleset/nlm-articleset-2.0.dtd
 * @author tasosnent
 */
public class PmcHandler extends DefaultHandler {    
    private boolean debugMode = false;
    private final JsonArrayProcessor processor;
    private JSONObject article = null; // also a FLAG : When null, we are outside PubmedArticle Tags
    private String previousField = null;
    private JSONArray body = null; // also a FLAG : When null, we are outside body Tags [text body of article]
    private JSONArray body_ignored = null; 
    //TODO: handle conflicts
    private Stack<String> elements = new Stack <String> (); // A LIFO to keep the current Element
    private Stack<JSONObject> sections = new Stack <JSONObject> (); // A LIFO to sections of full-text body
    private String text = "";
    
    //valid of idType values : "pmid" and "pmc"
    private String idType = null;// also a FLAG : When null, not an interesting id to be stored
    
    // handling Entities with the same name : conflicts
    // Actually, casi all elements are conflicts for PMC! the allow any combination of contents for the vast majority of the elements (see elements with tag "ANY" in the DTD here : https://dtd.nlm.nih.gov/ncbi/pmc/articleset/nlm-articleset-2.0.dtd)
    private ArrayList <String> conflictFields = new ArrayList <String> (); // Name of element that appear as sub-elements of more than one type of elements
    private String basicElement = ""; // This is the basic element in XML file (e.g. article for PMC) Hence, even for conflicts, no prefix will be used for childrens of this entity    // TODO : handle conflict elements

    // Entities to ignore in parsing PMC body (used for ref etc) the content of these elements will not appear in the text    
    private ArrayList <String> ignoreFields = new ArrayList <String> ();

    //Entities to harvest 
        // * For conflict antities use a "Parent_" prefix, except for the basic entity (e.g. Entry for uniprot)
//    private ArrayList <String> storedAsStrArrays = new ArrayList <String> (); // The text content of those elements will be stored in a JSONArray field og The JSONObject
    
    public PmcHandler(JsonArrayProcessor processor) {
        this.processor = processor;
        
        // Entities to ignore in parsing of body text (corresponding text ignored)
            // Valid only for sub-elements of Body element
//        this.ignoreFields.add("b"); // bold
//        this.ignoreFields.add("i"); // italics
//        this.ignoreFields.add("sup"); 
//        this.ignoreFields.add("sub"); 
//        this.ignoreFields.add("u"); 
        this.ignoreFields.add("xref"); 
        this.ignoreFields.add("table-wrap"); 
        this.ignoreFields.add("fig"); 
        this.ignoreFields.add("list"); 
        this.ignoreFields.add("inline-formula"); 
        this.ignoreFields.add("disp-formula"); 
        this.ignoreFields.add("def-list"); 
        
        // Article is the basic entity. Hence, for Entry, no prefix will be used (i.e. no prefix, means article for a conflict)
        this.basicElement = "article";

    }
   
    /**
     * Sets up article level variables
     * @param uri
     * @param localName
     * @param qName
     * @param attributes
     * @throws SAXException 
     */
    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
//        System.out.println("     " + qName + " { " );
//        System.out.println(this.elements.toString());
        startFieldName(qName);
         if (qName.equals("article")) {//just entred a new article Entity
               //initialize FLAG article variable
             article = new JSONObject();
         } else { 
             if(this.insideArticle()){//it's a PMCArticle sub-Entity
                if(qName.equals("body")){//just entred a new article body Entity
                    // body is the full text of article
                    this.body = new JSONArray();          
                    this.body_ignored = new JSONArray();                      
                } // else other element than body begins
                else if(this.insideField("body") && this.getFieldName().equals("sec")){// just entered a section element
                        //create a section object for it
                        this.sections.add(new JSONObject());
                } // else, other element than section begins
                else if (this.insideField("article-meta")){// It is "article-meta" Entity or a sub-Entity of it
                    if (qName.equals("article-id")){//just entred a new article-id Entity
                        // Check id types to let Characters() "know" if they should store the id contained.
                        if(attributes.getLength() > 0){
                            String attName = null;
                            String attValue = null;
                            for(int i =0 ; i < attributes.getLength() ; i++){
                                attName = attributes.getQName(i);
                                attValue = attributes.getValue(i);
                                if(attName != null & attValue != null){
                                    if(attName.equals("pub-id-type") && attValue.equals("pmid")){
                                        this.idType = "pmid";
                                    } else if(attName.equals("pub-id-type") && attValue.equals("pmc")){
                                        this.idType = "pmc";
                                    }
                                }
                            }
                        }
                    }
                } // not "article-meta" Entity or a sub-Entity of it
             } else { //it's not a PMCArticle sub-Entity
                if (qName.equals("pmc-articleset")) {//The beginig of the process
                        if(debugMode) {
                            System.out.println(" " + new Date().toString() + " Handler > *** New pmc-articleset Parsing Start *** " );
                        }
                    } else if (qName.equals("Reply")){ // Error handling : A reply element is returned when the pmcid is not found
                        System.out.println(" " + new Date().toString() + " pmid not found : " );
                         if(attributes.getLength() > 0){
                                String attName = null;
                                String attValue = null;
                                String id = null;
                                String error = null;
                                for(int i =0 ; i < attributes.getLength() ; i++){
                                    attName = attributes.getQName(i);
                                    attValue = attributes.getValue(i);
                                    if(attName != null || attValue != null){
                                        System.out.println("\t " + attName + " : " + attValue );
                                    }
                                }
                            }
                    } else {
                        //XML formar error
                        //Execution shouldn't reach here - not a PubmedArticleSet or PubmedArticle and not a PubmedArticle sub-Entity
                        //allowed XML Elements are PubmedArticleSet is PubmedArticle and PubmedArticle sub-elements
                        System.out.println("XML format error :\n\t" + qName + " not a valid XML Element start (pmc-articleset, article or article sub-Entity).  ");
                    }              
             }
         }
    }

    /**
     * Adds data to json object representing article 
     *  FieldName="XMLTagName", FieldValue="XMLTextContent"
     * @param ch
     * @param start
     * @param length
     * @throws SAXException 
     */
    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
//        System.out.print(" \t characters for : ");
        String text = new String(ch, start, length);
        if(getFieldName()!= null){
//                    System.out.println(" \t " + getFieldName() + " \t->" + new String(ch, start, length));
            if(this.insideField("article-meta")){ // it is a sub-element of "article-meta"
                // Get article ids
                if(getFieldName().equals("article-id")){ // it is an id of the article
                    //check what kind of id is this
                    if( idType!=null ){
                        // add the id to the json element
                        this.addText(idType, text);
//                        System.out.println("    id>> " + text + "  " );

                    }       
                // Get article title
                } else if(this.insideField("title-group")){ // it is a sub-element of "title-group"
                    if(this.insideField("article-title")){ // it is the title of the article 
                        // add the title to the json element
                        this.addText("title", text);
                    }
                } 
            // Get article body
            } else if(this.insideBody()){ // this is full-text element or a sub element of it
                
                // 1) Custom texts : fields body_Parts, body_Filtered, body_Extras
                
                // Add sub-elements of body Element in corresponding list, depending on whther they should be ignored or not
                if(this.ignoredField())
                {
                    this.body_ignored.add(text.trim());
                } else {
                    this.body.add(text.trim());
                }
//                System.out.println(" \t characters for : " + getFieldName() + " \t->" + text);

                // 2) Alternative, separation into sections
                String textField = "text"; // this is the field name to be used for text inside sections, except for titles
                if(this.insideField("sec")){// it's a section or subelement
                    if(!this.ignoredField()){// it's not ignored field
                        // add text content in corresponding section object
                        if(this.getFieldName().equals("title")){ // This is the title of the section
                            textField = "title";
                        }
                        // Add the text to the current section element
                        this.addText(this.getSection(), textField, text);
                    }
                } else { // outside a section
                    
                }
                //sections
                //sub sections
                
            } 
        }
    }

    /**
     * "Saves" article, calling processor, and nullifies article level variables for next article parsing
     * @param uri
     * @param localName
     * @param qName
     * @throws SAXException 
     */
    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
//        System.out.println("     } //" + qName );
        if (qName.equals("article")){//exiting a PMCArticle Entity
            //Call process (this saves article in list)
            processor.process(this.article);
//            //nullify article variable for further use
            this.article = null;
        } else {//exiting an Entity except PubmedArticle
            if(this.insideArticle()){//exiting an Entity belonging to PMCArticle
                 if(qName.equals("body")){//exiting a body Entity
                    // add body field to article 
                    this.article.put("body_Parts", this.body);
                    this.article.put("body_Filtered", Helper.JSONArrayToString(this.body," "));
                    this.article.put("body_Extras", Helper.JSONArrayToString(this.body_ignored, " "));
                    // and nullify body variable for further use
                    this.body = null;
                    this.body_ignored = null;
                } else if (this.insideField("body") && this.getFieldName().equals("sec")){ // exiting a section
                    //Handle sections
                    // hold here the current section object, remove from stack of sections 
                    JSONObject section = this.endSection(); 
                    if(!this.hasAncestor("sec")){ // Exiting a top level section
                        //Add section to article object
                        this.addJSONObjToJSONArray("sections", section);
                    } else {// exiting a sub-section
                        // add this subsection as an element in the parent-section
                        // thi.getsection() noe returns the parent of the section closing, since this.endSection() above has removed the closing section from the stack
                        this.addJSONObjToJSONArray(this.getSection(), "subsections", section);
//                        if(section.containsKey("title")){
//                            this.getSection().put(section.get("title"), section);
//                        } else if (section.keySet().isEmpty()){
//                            System.out.println("Warning: Empty section for article " + article);
//                        }
                    }
                } else if (qName.equals("article-id")){//exiting an article-id Entity
                    this.idType = null;
                } 
            } else {//exiting an Entity not belonging to PMCArticle 
                if (qName.equals("pmc-articleset")) {//The end of the process
                    if(debugMode) {
                        System.out.println(" " + new Date().toString() + " Handler > *** pmc-articleset Parsing End *** " );
                    }
                 } else if (qName.equals("Reply")){ // Error handling : A reply element is returned when the pmcid is not found
                    System.out.println(" " + new Date().toString() + " End of Reply element" );
                } else {
                    //XML formar error
                    //Excecution shouldn't reach here - not a PubmedArticleSet or PubmedArticle and not a PubmedArticle sub-Entity
                    //allowed XML Elements are PubmedArticleSet is PubmedArticle and PubmedArticle sub-elements
                    System.out.println("Warning: XML format error :\n\t" + qName + " not a valid XML Element end (pmc-articleset, article or article sub-Entity).  ");
                } 
            }
        }
        endFieldName(qName); 
    }

    /**
    * Checks whether the current element is an element to ignore or a child of it.
    *      i.e. If any ignored field is currently open
    * @return true if should be ignored, false otherwise
    */
    private boolean ignoredField() {    
        for (String s : this.ignoreFields){
            if(this.insideField(s)){
//                System.out.print(s + " is open ");
                return true;
            }
        }
        return false;
    }    
    
    /**
    * Checks whether the current element should be ignored during Body filtering or not
    * @return true is should be ignored, false otherwise
    */
    private boolean isIgnored(String field) {
        return this.ignoreFields.contains(field);
    }
    
    /**
    * Checks whether the current element is inside Body element or not
    * @return true if inside body, false otherwise
    */
    private boolean insideBody() {
        boolean inside = false;
        if(this.body != null){
            inside = true;
        }
        return inside;
    }
    
    /**
    * Checks whether the current element is inside article element or not
    * @return true if inside article, false otherwise
    */   
    private boolean insideArticle() {
        boolean inside = false;
        if(this.article != null){
            inside = true;
        }
        return inside;
    }

    public String getPreviousField() {
        return previousField;
    }

    /**
     * @param previousField the previousField to set
     */
    public void setPreviousField(String previousField) {
        this.previousField = previousField;
    }
    
    /**
     * @return the text
     */
    public String getText() {
        return text;
    }

    /**
     * @param text the text to set
     */
    public void setText(String text) {
        this.text = text;
    }
    /**
     * Adds a text-value for given field in JSON object of the article
     *      If the field already has a text value, the new text is concatenated at the end. 
     *      If no text exists, it is just added.
     *      IMPORTANT : The function relies in the assumption that field should only hold string values.
     * @param field     the filed to add value (fields used should only hold "string" values, no arrays etc)
     * @param text      the "text-value" to be added
     */
    public void addText(String field, String text){
        text = text.trim();
        if(!text.equals("")){ // notmal string value
            if(this.article.keySet().contains(field)){ // field alredy exists, should have a text value
                this.article.put(field,this.article.get(field) + text);
            } else {
                this.article.put(field,text);
            } 
        } else { // empty string value
//            System.out.println(" Waring : empty string value ignored for field : \"" + field + "\"");                       
        }
    }
    
    /**
     * Adds a text-value for given field in JSON object given
     *      If the field already has a text value, the new text is concatenated at the end. 
     *      If no text exists, it is just added.
     *      IMPORTANT : The function relies in the assumption that field should only hold string values.
     * @param obj       the JSONObject to add the field-value in
     * @param field     the filed to add value (fields used should only hold "string" values, no arrays etc)
     * @param text      the "text-value" to be added
     */
    public void addText(JSONObject obj, String field, String text){
        text = text.trim();
        if(!text.equals("")){ // notmal string value
            if(obj.keySet().contains(field)){ // field alredy exists, should have a text value
               obj.put(field,obj.get(field) + text);
            } else {
                obj.put(field,text);
            } 
        } else { // empty string value
//            System.out.println(" Waring : empty string value ignored for field : \"" + field + "\"");                       
        }
    }
    
    /**
    * Checks whether the current element is the field given or a child of it.
    *      i.e. If the filed given is currently open
    * @return true if inside the field, false otherwise
    */
    private boolean insideField(String field) {
        return this.elements.contains(field);
    }
   
    /**
    * Checks whether the current element has the field given as ancestor.
    *      i.e. If the filed given is currently open
    * @return true if inside the field, false otherwise
    */
    private boolean hasAncestor(String field) {
        Stack<String> ancestors = new Stack<>();
        ancestors.addAll(this.elements);
        ancestors.pop();
        return ancestors.contains(field);
    }

    
    /**
     * Sets the current element
     *      Takes into account renaming for conflicts (i.e. adding parent name in fieldName) 
     * @param fieldName the fieldName to add
     */
    public void startFieldName(String fieldName) {
//        System.out.println(this.elements);
        String parent = this.getFieldName(); // At this point the new element has not been added to the stack, its parent is the current element
        // Put the element in the stack of elements, using the parent-prefix if needed (i.e. if conflict)
        this.elements.push(handleConflicts(parent,fieldName));
    }
    
    /**
     * Removes the current element
     *      Takes into account renaming for conflicts (i.e. adding parent name in fieldName) 
     * @param fieldName the fieldName to set
     */
    public void endFieldName(String fieldName) {
//        System.out.println(this.elements);
        if(getFieldName().equals(handleConflicts(this.getParent(),fieldName))){
            this.elements.pop();
        } else {
            System.out.println(" " + new Date().toString() + " Error: " + fieldName + " trying to close, is not the current open Element " + getFieldName() );
        }
    }
    
    /**
     * Reads the current element (without remove)
     * @return the fieldName
     */
    public String getFieldName() {
        if(this.elements.isEmpty())
            return "ROOT";
        return this.elements.peek();
    }
    
    /**
     * Adds a String item to a Json Array field of the JSON Object
     *  If not such a field exist, it is created 
     * 
     *      ***
     *      IMPORTANT : The function relies in the assumption that field should only be a list of string values.
     *      ***
     * 
     * @param field         String the name of the field to store the list e.g. "interatcants" : []
     * @param interactant   String the string value to be added in the list
     */
    public void addStringToJSONArray(String field, String interactant){
        JSONArray interactants = null;
        if(this.article.keySet().contains(field)){ // Interactants list alredy exists, just add
//            System.out.println(" \t existing " + field + " elements : " + this.entry.get(field) );
            interactants = (JSONArray)this.article.get(field);
          } else {
            interactants = new JSONArray();
          }
        interactants.add(interactant);
        this.article.put(field,interactants);
    }
    
    /**
     * Adds a JSONObject item to a Json Array field of the JSON Object
     *  If not such a field exist, it is created 
     * 
     *      ***
     *      IMPORTANT : The function relies in the assumption that field should only be a list of JSONObjects.
     *      ***
     * 
     * @param field         String the name of the field to store the list e.g. "interatcants" : []
     * @param obj   JSONObject the object of the section to be added in the list
     */
    public void addJSONObjToJSONArray(String field, JSONObject obj){
        JSONArray interactants = null;
        if(this.article.keySet().contains(field)){ // Interactants list alredy exists, just add
//            System.out.println(" \t existing " + field + " elements : " + this.article.get(field) );
            interactants = (JSONArray)this.article.get(field);
          } else {
            interactants = new JSONArray();
          }
        interactants.add(obj);
        this.article.put(field,interactants);
    }
    /**
     * Adds a JSONObject item to a Json Array field of the JSON Object given
     *  If not such a field exist, it is created 
     * 
     *      ***
     *      IMPORTANT : The function relies in the assumption that field should only be a list of JSONObjects.
     *      ***
     * 
     * @param obj           JSONObject the object of to get the value added
     * @param field         String the name of the field to store the list e.g. "interatcants" : []
     * @param item          JSONObject the item to be added in the obj Object
     */
    public void addJSONObjToJSONArray(JSONObject obj, String field, JSONObject item){
        JSONArray interactants = null;
        if(obj.keySet().contains(field)){ // Interactants list alredy exists, just add
//            System.out.println(" \t existing " + field + " elements : " + this.article.get(field) );
            interactants = (JSONArray)obj.get(field);
          } else {
            interactants = new JSONArray();
          }
        interactants.add(item);
        obj.put(field,interactants);
    }
    
    /**
    * Checks whether this is a conflict FieldsName
    * @param fieldName      a FieldName (STring) to be checked for whether it is a conflict field
    * @return               true if current element is a ConflictFieldName, false otherwise
    *   In case this is a conflict element, it's parent field-name will be used as a prefix
    */
    private boolean isAConflictField(String fieldName) {
        return this.conflictFields.contains(fieldName);
    }
    
    /**
     * Get The parent of the current element
     *      Important, Works only AFTER the current element has been added to elements stack!
     * @return      The parent of the Parent element (i.e. the previous element in the stack of elements)
     */
    private String getParent(){
//        System.out.println(this.elements.size() + " * " + this.elements);
        if(this.elements.size() <= 1){ // Ths is the root element
            return "ROOT";
        } else {
            return this.elements.get(this.elements.size()-2);
        }
    }
    /**
     * Handles the naming of conflict elements (adding parent-prefix if needed)
     *      For any element in the set of conflictFields adds the parent-field as e prefix
     *      Exception, for the basic element (e.g. Entry for uniprot) no prefix is added (since prefixes are used for all the rest occurrences, no prefix indicates the basic entity)
     * @param fieldName
     * @return 
     */
    private String handleConflicts(String parent, String fieldName){
         String finalFieldName = fieldName; // Initialize for the general case, where no prefix needed
        if(this.isAConflictField(fieldName)){ // This is a conflict Element (i.e. appears with parents of different type)
            if(!parent.equals(this.basicElement)){ // This is not a sub-element of basic entity, add the prefix
                finalFieldName = parent + "_" + fieldName;
            } // else, this is sub-element of the basic entity : Don't use a prefix - Conflict fields hold their initial name only for the basic element
        } // else, this is a normal Element (not a conflict) - no prefix needed
        
        return finalFieldName;
    }
    /**
     * Get the JSONObject representing the current section of the full text
     * @return 
     */
    private JSONObject getSection(){
        if(this.sections.empty())
            return null;                    
        return this.sections.peek();
    }
    /**
     * Remove the JSONObject representing the current section of the full text
     * @return 
     */
    private JSONObject endSection(){
        if(this.sections.empty())
            return null;                    
        return this.sections.pop();
    }

}
