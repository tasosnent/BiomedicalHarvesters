/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

//Commented for Server compile 
package drugbankHarvester;

import java.util.ArrayList;
import java.util.Date;
import java.util.Stack;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 *
 * @author tasosnent
 */
public class DrugbankHandler extends DefaultHandler {
    
    private final JsonArrayProcessor processor;

    //Harvesting variables
    private Stack<String> elements = new Stack <String> (); // A LIFO to keep the current Element
    private Attributes attributes = null; // The attributes of the current element
    private JSONObject drug = null; // The object representing the drug we are parsing
    private StringBuffer tmpStr = new StringBuffer(); // Used to handle the fact tha Charaters() is no nececarily called once for each Element
    private StringBuffer externalIdentifierResource = new StringBuffer(); // The selecetd resources to harvest identifiers for them
    
    //Entities to harvest 
        // * For conflict entities use a "Parent_" prefix, except for the basic entity (e.g. Entry for uniprot)
    private ArrayList <String> storedAsStrArrays = new ArrayList <String> (); // The text content of those elements will be stored as items of a JSONArray field of The JSONObject
    private ArrayList <String> storedAsStr = new ArrayList <String> (); // The text content of those elements will be stored in a String field of The JSONObject
    private ArrayList <String> externalResourcesToHarvest = new ArrayList <String> (); // The ids provided for those resources will be stored in The JSONObject
    
    
    // handling Entities with the same name : conflicts
    private ArrayList <String> conflictFields = new ArrayList <String> (); // Name of element that appear as sub-elements of more than one type of elements
    private String basicElement = ""; // This is the basic element in XML file (e.g. Entry for Uniprot) Hence, even for conflicts, no prefix will be used for childrens of this entity    
    private String FieldPrefixSeparator = "_" ; // This is the basic element in XML file (e.g. Entry for Uniprot) Hence, even for conflicts, no prefix will be used for childrens of this entity    

    DrugbankHandler(JsonArrayProcessor processor) {
        this.processor = processor;
        
//      These are all The Elements that occure as subelements of more than one XML element (i.e. conflicts)
//      For those elements, the field name will have a prefix "Parent_FieldName", e.g. "kinetics_text" instead of just "text"
        this.conflictFields.add("allele");
        this.conflictFields.add("approved");
        this.conflictFields.add("average-mass");
        this.conflictFields.add("cas-number");
        this.conflictFields.add("category");
        this.conflictFields.add("citation");
        this.conflictFields.add("country");
        this.conflictFields.add("description");
        this.conflictFields.add("drug");
        this.conflictFields.add("drugbank-id");
        this.conflictFields.add("enzyme");
        this.conflictFields.add("enzymes");
        this.conflictFields.add("external-identifier");
        this.conflictFields.add("external-identifiers");
        this.conflictFields.add("gene-symbol");
        this.conflictFields.add("identifier");
        this.conflictFields.add("kind");
        this.conflictFields.add("monoisotopic-mass");
        this.conflictFields.add("name");
        this.conflictFields.add("organism");
        this.conflictFields.add("polypeptide");
        this.conflictFields.add("property");
        this.conflictFields.add("protein-name");
        this.conflictFields.add("pubmed-id");
        this.conflictFields.add("reaction");
        this.conflictFields.add("resource");
        this.conflictFields.add("route");
        this.conflictFields.add("rs-id");
        this.conflictFields.add("sequence");
        this.conflictFields.add("source");
        this.conflictFields.add("strength");
        this.conflictFields.add("synonym");
        this.conflictFields.add("synonyms");
        this.conflictFields.add("unii");
        this.conflictFields.add("uniprot-id");
        this.conflictFields.add("url");
        this.conflictFields.add("value");
        this.conflictFields.add("allele");


//      drug is the basic entity. Hence, for drug, no prefix will be used (i.e. no prefix, means Entry for a conflict)
        this.basicElement = "drugbank_drug"; // Actually, this is the "drug" element, but this is in conflict with "drugs_drug"
        this.FieldPrefixSeparator = "_"; // FieldPRefixes separater by "_" because "-" is already used in field names

//      Identifiers to those external resources will be harvested
        this.externalResourcesToHarvest.add("KEGG Drug");
        
        // *** Generalized Harvesting ***
        
//      Those will be String fields in the harvested objects  
        this.storedAsStr.add("indication");        
//      Those will be Arrays of Strings in the harvested objects  
        this.storedAsStrArrays.add("drug-interaction_drugbank-id"); // Interactant drugs
        this.storedAsStrArrays.add("article_pubmed-id"); // The Interactants from IntAct
//        this.storedAsStrArrays.add("drugbank-id"); // the ids of the drug 
//          [Not used : we do not actually want non-primary, i.e. deprecated, ids]
//          Get the drugbank-id, only when attribute primary is true. 
    }
   
    /**
     * Sets up drug-level variables
     * 
     * @param uri
     * @param localName
     * @param qName
     * @param attributes
     * @throws SAXException 
     */
    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
//        System.out.println("     " + qName + " { " );         
        startFieldName(qName);
        //initiallize attributes variable
        this.attributes = attributes;
        if (this.getFieldName().equals("drugbank_drug")) {//just entred a new drug Entity
//            System.out.println("New Entry Object created" );
            //initialize Entry Object and other harvesting variables
            setObj(new JSONObject());
        } else { // just entred a new Entity, other than drug
            if(!this.insideField("drugbank_drug")){//it's not a drug sub-Entity
                if (this.getFieldName().equals("drugbank")) {//The beginig of the process
                    System.out.println("\t *** New drugbank Hravesting Start ***" );
                } else {
                    //XML formar error
                    //Excecution shouldn't reach here - not a vaid XML element of the ones listed below
                    System.out.println("XML format error :\n\t" + qName + " not a valid XML Element start (drugbank, drug, drug sub-Entity). ");
                }            
            } //it's a drug sub-Entity
        }
    }

    /**
     * Handles Text content of XML elements
     * 
     * @param ch
     * @param start
     * @param length
     * @throws SAXException 
     */
    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
//        System.out.println(" \t characters for : " + getFieldName());
        String text = new String(ch, start, length);
//        System.out.println(" \t\t text  : " + text);
        if(this.insideField("drugbank_drug") ){//Inside a drug element
            
            // Get primary id
            if(this.getFieldName().equals("drugbank-id")){ // this is an "drugbank-id" element
                //check if primary
                if(this.getAttribute("primary").equals("true")){//this is a primary id
                    this.addText("drugbank-id", text);
                }// not a primary id, jsust continue
            } // not a drugbank-id element
            // Get links to other databases
            else if(this.getFieldName().equals("external-identifiers_external-identifier_resource")){ // this is an "externalIdentifierResource" element
                    this.externalIdentifierResource.append(text); // keep the "externalIdentifierResource" 
            } // not an "externalIdentifierResource" element
            else if( this.getFieldName().equals("external-identifiers_external-identifier_identifier")){ // This is an identifier element 
                if( this.externalIdentifierResource.length() > 0 // An "externalIdentifierResource" has been read
                    && this.externalResourcesToHarvest.contains(this.externalIdentifierResource.toString())){// and, the current "externalIdentifierResource" is included in the "harvested" ones.
                    // Put the identifier as a text field in the JSONObject
                    this.addText(this.externalIdentifierResource.toString(), text); 
                    // e.g. "KEGG Drug" : "D00217"
                }
            }// not an identifier element 
            
            // *** Generalized Harvesting ***
            
            // Get string values for Elements to be stored as Strings
            // E.g. for Drugbank, read an indication text
            if(this.isStoredAsStr(this.getFieldName())){ // This is a field to be stored as String
                this.addText(this.getFieldName(), text);
            } else // Not to be stored as String
            // Get string values for Elements to be stored as Array of Strings
            // E.g. for Uniprot, read an interactant id or an accession number
            if(this.isStoredAsStrArray(this.getFieldName())){ // This is a field to be stored as String item in a JSONArray
                tmpStr.append(text);// Strore the part of the id in the string buffer
            } 
            
        } else {//XML formar error
            //Excecution shouldn't reach here - Outside drug but with text content
            if(!text.trim().equals("")){
                System.out.println("Warnig: XML format error :\n\t Entity named " + getFieldName() + " has 'text content' but its outside a \"drug\" Element. \n\t\ttext content: \"" + text +"\"" );
            } 
            // ( if text is empty, the reason may be XML beautification with spaces which causes caracters() method to be called for XML Entities which are actually empty such as for MedlineCitation Entity itself" 
        }
    }

    /**
     * Nullifies drug-level variables to be reused
     * 
     * @param uri
     * @param localName
     * @param qName
     * @throws SAXException 
     */
    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
//        System.out.println("     } //" + qName );
        if (this.getFieldName().equals("drugbank_drug")){//exiting a drug Entity
            //Call process to index the document
            processor.process(this.getEntry());
//            System.out.println("drug instance nullified" );
            setObj(null);
        } else {//exiting an Entity except drug
            if(this.insideField("drugbank_drug")){//exiting an Entity belonging to drug
                if(this.isStoredAsStrArray(this.getFieldName())){// Exiting an element that should be stored in a string array
                    this.addStringToJSONArray(this.getFieldName(),this.tmpStr.toString());// Strore the buffered String in the String list of the Entry Object
                    tmpStr.setLength(0); // reset tmpStr for further use
                } // Else not a field to be stored in an array of strings
                else if(this.getFieldName().equals("external-identifiers_external-identifier")){ // Exiting an "externalIdentifier" element
                    // Nullify the "external-identifiers_external-identifier_resource" so that this value will not be wrongly re-used for other identifiers
                    this.externalIdentifierResource.setLength(0);
                }
            } else {//exiting an Entity not belonging to drug
                if (this.getFieldName().equals("drugbank")) {//The end of the process
                    System.out.println("\t *** Drugbank Indexing End ***" );
                } else {
                    //XML formar error
                    //Excecution shouldn't reach here - not a vaid XML element of the ones listed below
                    System.out.println("XML format error :\n\t" + qName + " not a valid XML Element start (drugbank, drug, drug sub-Entity). ");
                }
            }
        }
        //Nullify attributes variable
        this.attributes = null;
        endFieldName(qName); 
    } 

    /**
     * @return the mlcDoc
     */
    public JSONObject getEntry() {
        return drug;
    }

    /**
     * @param mlcDoc the mlcDoc to set
     */
    public void setObj(JSONObject mlcDoc) {
        this.drug = mlcDoc;
    }

    /**
     * @param qName the FieldName to check if in list of storedFields as Arrays
     * @return true if qName is a storedField, false otherwise
     */
    private boolean isStoredAsStrArray(String qName) {
        return this.storedAsStrArrays.contains(qName);
    }
    /**
     * @param qName the FieldName to check if in list of storedFields as Strings
     * @return true if qName is a storedField, false otherwise
     */
    private boolean isStoredAsStr(String qName) {
        return this.storedAsStr.contains(qName);
    }   
    
    /**
    * Checks whether the current element is the field given or a child of it.
    *      i.e. If the filed given is currently open
    * @return true if should be ignored, false otherwise
    */
    private boolean insideField(String field) {
        return this.elements.contains(field);
    }
    
    /**
     * Sets the current element
     *      Takes into account renaming for conflicts (i.e. adding parent name in fieldName) 
     * @param qName the fieldName to add
     */
    public void startFieldName(String qName) {
//        System.out.println(this.elements);
        String parent = this.getFieldName(); // At this point the new element has not been added to the stack, its parent is the current element
        // Put the element in the stack of elements, using the parent-prefix if needed (i.e. if conflict)
        this.elements.push(handleConflicts(parent,qName));
    }
    
    /**
     * Removes the current element
     *      Takes into account renaming for conflicts (i.e. adding parent name in fieldName) 
     * @param qName the fieldName to set (as given by SAX Parser, without any prefix!)
     */
    public void endFieldName(String qName) {
//        System.out.println(this.elements);
        if(getFieldName().equals(handleConflicts(this.getParent(),qName))){
            this.elements.pop();
        } else {
            System.out.println(" " + new Date().toString() + " Error: " + qName + " trying to close, is not the current open Element " + getFieldName() );
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
     * Adds a text-value for given field in JSON object of the article
     *      If the field already has a text value, the new text is concatenated at the end. 
     *      If no text exists, it is just added.
     * 
     *      ***
     *      IMPORTANT : The function relies in the assumption that field should only hold string values.
     *      ***
     *      IMPORTANT : "Empty" strings are ignored. 
     *      ***
     * 
     * @param field     the filed to add value (fields used should only hold "string" values, no arrays etc)
     * @param text      the "text-value" to be added
     */
    public void addText(String field, String text){
        text = text.trim();
        if(!text.equals("")){ // notmal string value
    //        System.out.println(" addText for field " + field +  " : \"" + text + "\"");
            if(this.drug.keySet().contains(field)){ // field alredy exists, should have a text value
//            System.out.println(" \t existing : " + this.drug.get(field) );

                this.drug.put(field,this.drug.get(field) + text);
            } else {
                this.drug.put(field,text);
            }
    //        System.out.println(" \t new : " + this.drug.get(field) );
        } else { // empty string value
            System.out.println(" Waring : empty string value ignored for field : \"" + field + "\"");                       
        }
    }
    
    /**
     * Adds a String item to a Json Array field of the JSON Object
     *  If not such a field exist, it is created 
     * 
     *      ***
     *      IMPORTANT : The function relies in the assumption that field should only be a list of string values.
     *      ***
     * 
     * @param field         String the name of the field to store the list e.g. "interactants" : []
     * @param interactant   String the id of the interactant to be added in the list
     */
    public void addStringToJSONArray(String field, String interactant){
//        System.out.println(" \t addStringToJSONArray Called field : " + field + " string : " + interactant + " drug : " + this.drug );
        JSONArray interactants = null;
        if(this.drug.keySet().contains(field)){ // Interactants list alredy exists, just add
//            System.out.println(" \t existing " + field + " elements : " + this.drug.get(field) );
            interactants = (JSONArray)this.drug.get(field);
          } else {
            interactants = new JSONArray();
          }
        interactants.add(interactant);
        this.drug.put(field,interactants);
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
                finalFieldName = parent + FieldPrefixSeparator + fieldName;
            } // else, this is sub-element of the basic entity : Don't use a prefix - Conflict fields hold their initial name only for the basic element
        } // else, this is a normal Element (not a conflict) - no prefix needed
        
        return finalFieldName;
    }
    
    /**
     * Get the value of the attName attribute for the given field
     *      If no attributes exist all, or not the specific attribute exists, returns the empty string ("")
     * @param attName   The name of the attribute to retrieve
     * @return          The value of the attribute as a string (or "" instead of null, if no value exists)
     */
    private String getAttribute(String attName){
        String attValue = null;
        //Handle attributes
        if(attributes != null && attributes.getLength() > 0){// The entity has Attributes
            String currAttName = null;
            // For all attributes
            for(int i =0 ; i < attributes.getLength() ; i++){ 
                currAttName = attributes.getQName(i);
                if(currAttName != null && currAttName.equals(attName)){ // this is the requested attribute
                    attValue = attributes.getValue(i); // get the value
                }
            }
        }
        if (attValue == null)
            return "";
        else
            return attValue;
    }
}
