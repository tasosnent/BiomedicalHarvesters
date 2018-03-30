/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

//Commented for Server compile 
package pubmedIndexing;

import java.util.ArrayList;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.util.BytesRef;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 *  Parses PubMed XML data and calls given IndexingProcessor to index them
        Using DTD 2017 : http://dtd.nlm.nih.gov/ncbi/pubmed/out/pubmed_170101.dtd
 * @author tasosnent
 */
public class PubmedArticleIndexingHandler extends DefaultHandler {

    private final IndexingProcessor processor;
    private String text = "";
    //indexing variables
    private Document mlcDoc = null; // also a FLAG : When null, we are outside MedlineCitation Tags
    private String fieldName = null;
    private String fieldValue = null;
    private String previousField = null;

    //Entities to store in Lucene index (if empty, all fields will be stored)
    private ArrayList <String> storedFields = new ArrayList <String> ();
    // Entities to ignore in parsing (used for b, i etc which are not real entities and cause breaking down of real entities e.g. Title)
    private ArrayList <String> ignoreFields = new ArrayList <String> ();
    
    private ArrayList <String> conflictParentFields = new ArrayList <String> ();
    private String conflictParentFieldName = null; // also a FLAG : When not null, we are inside a conflict parent field

    //handling Abstract text Label
    private String AbstractText_Label = null;
       
    public PubmedArticleIndexingHandler(IndexingProcessor processor) {
        this.processor = processor;
        
        //XML Elements which contain fields with conflict in name.
            //the names of those elements are added as a prefix to sub entities
//        conflictParentFields.add("Abstract"); No need for prefix - prefix added to OtherAbstract which have conflicts
//        conflictParentFields.add("Article"); No prefix for MedlineCitation - prefix added to Book and BookDocument
//        conflictParentFields.add("MedlineCitation"); No prefix for MedlineCitation - prefix added to Other conflict parents, so no suffix conflict belongs to MedlineCitation 
//        conflictParentFields.add("JournalIssue"); No prefix for JournalIssue - prefix added to Book which have conflicts
        conflictParentFields.add("AffiliationInfo");
        conflictParentFields.add("ArticleDate");
        conflictParentFields.add("Author");
        conflictParentFields.add("AuthorList");
        conflictParentFields.add("BeginningDate");
        conflictParentFields.add("Book");
        conflictParentFields.add("BookDocument");
        conflictParentFields.add("CommentsCorrections");
        conflictParentFields.add("ContributionDate");
        conflictParentFields.add("DataBankList");
        conflictParentFields.add("DateCompleted");
        conflictParentFields.add("DateCreated");
        conflictParentFields.add("DateRevised");
        conflictParentFields.add("DeleteCitation");
        conflictParentFields.add("DeleteDocument");
        conflictParentFields.add("DescriptorName");
        conflictParentFields.add("ELocationID");
        conflictParentFields.add("EndingDate");
        conflictParentFields.add("GeneralNote ");
        conflictParentFields.add("Grant");
        conflictParentFields.add("GrantList");
        conflictParentFields.add("Identifier");
        conflictParentFields.add("Investigator");
        conflictParentFields.add("Keyword");
        conflictParentFields.add("KeywordList");
        conflictParentFields.add("LocationLabel");
        conflictParentFields.add("MedlineJournalInfo");
        conflictParentFields.add("NameOfSubstance");
        conflictParentFields.add("Object");
        conflictParentFields.add("OtherAbstract");
        conflictParentFields.add("OtherID");
        conflictParentFields.add("PersonalNameSubject");
        conflictParentFields.add("PubDate");
        conflictParentFields.add("PublicationType");
        conflictParentFields.add("PublicationTypeList");
        conflictParentFields.add("PubmedArticleSet");
        conflictParentFields.add("PubmedBookArticleSet");
        conflictParentFields.add("PubmedBookData");
        conflictParentFields.add("PubmedData");
        conflictParentFields.add("QualifierName");
        conflictParentFields.add("Section");
        conflictParentFields.add("SupplMeshName");
        conflictParentFields.add("URL");       
        
        //XML Elements to be stored in the index, so that can be retrieved
            // if empty, all will be stored
            // all fields needed for getDocumentJSON(Document doc)method in SearchDocuments.java 
            // Entity name should be full: i.e. String value should contain suffix if applicable
            /* Important Note: all Attributes of PubDate Element (used for sorting) are stored by defeault!!! (see characters() method below) */
//        addStoredField("AbstractText");
//        addStoredField("Title");
//        addStoredField("DescriptorName");
//        addStoredField("PMID");
//        addStoredField("ArticleTitle");
//        addStoredField("MedlineDate");
//        addStoredField("GrantList-Acronym");
//        addStoredField("GrantList-Agency");
//        addStoredField("GrantList-Country");
//        addStoredField("GrantList-GrantID");
//        addStoredField("GrantList_CompleteYN");
//        addStoredField("DateCreated-Year");
//        addStoredField("DateCreated-Day");
//        addStoredField("DateCreated-Month");
        
        // Entities to ignore in parsing (used for b, i etc which are not real entities and cause breaking down of real entities e.g. Title)
            // Ignored by setFieldName(), which keeps the previous value to to fieldName variable 
        this.ignoreFields.add("b"); // bold
        this.ignoreFields.add("i"); // italics
        this.ignoreFields.add("sup"); 
        this.ignoreFields.add("sub"); 
        this.ignoreFields.add("u"); 
    }
   
    // Event Handlers
    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
//        System.out.println("     " + qName + " { " );
         
        if (qName.equals("PubmedArticle")) {//just entred a new PubmedArticle Entity
//            System.out.println("New PubmedArticle Document created" );
            //initialize FLAG PubmedArticle variable 
            setMlcDoc(new Document());
            this.setFieldName(qName);// used  for Attributes Field Name prefix at the end of this method
        } else {
            if(this.insidePubmedArticle()){//it's a PubmedArticle sub-Entity
                this.setFieldName(qName);
                //Elements contained in a 'Conflict Parent Element' (i.e. year, month, day in Date) are added with their parent's fieldName as prefix
                if(insideConflictParent()){//It's a conflict-element 
                    // add a prefix in fieldName of conflict field
                    this.setFieldName(getConflictParentFieldName() + "-" + getFieldName());
//                    System.out.println(" \t > getFieldName : " + this.getFieldName());
                } else // by this "else" we avoid to replace ConflictParentFieldName for Elements which are Conflict themselfs (case AffiliationInfo which is Conflict and Conflict Parent)
                        // in such cases ConflictParentFieldName of ther parent is used for their sub-entities too
                if(isAConflictParentField(qName)){//its a conflict parent Field
                    //update FLAG variable with this conflict parent field name
                    this.setConflictParentFieldName(qName);
//                    System.out.println("\t  setConflictParentFieldName  " + qName);

                } 
            } else {//it's not a PubmedArticle sub-Entity
                if (qName.equals("PubmedBookArticle")) {//Other elements than PubmedArticle, not of interest currently
//                    System.out.println("\t *** PubmedBookArticle Start ***" );
                } else if(qName.equals("DeleteCitation")){
//                    System.out.println("\t *** Delete Citation Start ***" );
                } else if(qName.equals("PubmedArticleSet")){ //The beginig of the process
//                    System.out.println("\t *** New PubmedArticleSet Indexing Start ***" );
                } else {                 
                    //XML formar error
                    //Excecution shouldn't reach here - not a PubmedArticleSet or PubmedArticle and not a PubmedArticle sub-Entity
                    //allowed XML Elements are PubmedArticleSet is PubmedArticle and PubmedArticle sub-elements
                    System.out.println("XML format error :\n\t" + qName + " not a valid XML Element start (PubmedArticleSet, PubmedArticle, PubmedArticle sub-Entity).  ");
                }            
            }
        }
        //Add attribute fields : Add Entity Name as a prefix for Attribute Fields name
        if(attributes.getLength() > 0){
            String attName = null;
            String attValue = null;
            String attFieldName = null;
            for(int i =0 ; i < attributes.getLength() ; i++){
                attName = attributes.getQName(i);
                attValue = attributes.getValue(i);
                if(attName != null & attValue != null){
                    attFieldName = this.getFieldName() + "_" + attName;
                    Field mlcField = new TextField(attFieldName, attValue,  Field.Store.NO);
                    if(this.isAStoredField(attFieldName)){// The value of this Field is needed, so store it
                         // this stored TextField replaces the TextField created above
                        mlcField = new TextField(attFieldName, attValue,  Field.Store.YES);
                    }
//                    Field mlcField = new StringField(attFieldName, attValue, Field.Store.YES);
//                    System.out.println("New field added : " + attFieldName + " - " + attValue );
                    getMlcDoc().add(mlcField);
                    //in case of AbstractText elements and Label attribute, update AbstractText_Label variable with given value
                    if(attFieldName.equals("AbstractText_Label")){
                        setAbstractText_Label(attValue);
                    }
                }
            }
        }
    }

    @Override
    /**
     * Adds TextField for each XML tag containing "text"
     * FieldName="XMLTagName", FieldValue="XMLTextContent"
     */
    public void characters(char[] ch, int start, int length) throws SAXException {
//        System.out.println(" \t characters for : " + getFieldName());
        if(getFieldName() == null) { // Some text exists between sub-elements of an entity that opened but didn't end yet (e.g. Abstratct text)
            // This should be the case for entities containing pesudo-entities (<b>, <i>, <sub> etc)
            this.setFieldName(getPreviousFieldName());
        }
//        System.out.println(" \t characters for : " + getFieldName());
        //tag contains text
        setText(getText() + new String(ch, start, length));
//        System.out.println(" \t\t \"" + this.getText() +"\"");
    
        if(this.insidePubmedArticle() ){//Inside PubMedArticle
            if((getFieldName()!=null) & !this.getText().isEmpty()){//Fieldname exists and text content is not empty
                
                //Add Label prefix in AbstractText body
                //if element is AbstractText and AbstractText_Label variable has a value, add this value as prefix in text
                if(getFieldName().equals("AbstractText") & getAbstractText_Label() != null){
                        setText(getAbstractText_Label() + ": " + getText());
                        // Label must be adden only once...
                        //So, immediately nullify AbstractText_Label to avoid adding prefix multiple times due to segmented AbstractText (bug of AbstractText containing '>' characters)
                         setAbstractText_Label(null);
                }
//                // create corresponding index field and add it to the Document
//                Field mlcField = new TextField(this.getFieldName() , this.getText(), Field.Store.NO);
//                if(this.isAStoredField(this.getFieldName())){ // The value of this Field is needed, so store it
//                     // this stored TextField replaces the TextField created above                        
//                    mlcField = new TextField(this.getFieldName() , this.getText(), Field.Store.YES);
//                }
//                if(this.getConflictParentFieldName()!= null) {
//                    // For PubDate subelements use StringField which is indexed but not tokenized: the entire String value is indexed as a single token. Therefore, can be used for sorting or accessed through the field cache.
//                    if(this.getConflictParentFieldName().equals("PubDate")){
//                        // this StringField replaces the TextField created above
//                        mlcField = new StringField(this.getFieldName(), this.getText(), Field.Store.YES);
//                        Field sdvField = new SortedDocValuesField(this.getFieldName(), new BytesRef(this.getText())); 
////                        System.out.println(sdvField.fieldType().docValuesType());
//                        getMlcDoc().add(sdvField);
//                    } 
//                } 
////                    System.out.println("New field added : " + getFieldName() + " - " + this.getText() );
//                getMlcDoc().add(mlcField);
            }//else : Fieldname not exists or/and text content is empty
        } else {//XML formar error
            //Excecution shouldn't reach here - Outside PubmedArticle but with text content
            //The only XML Element outside PubmedArticle is PubmedArticleSet which hasn't text conent
            System.out.println("Warnig: XML format error :\n\t Entity named " + getFieldName() + " has 'text content' but its outside a PubmedArticle Element. \n\t\ttext content: " + this.getText() );
            // ( if text is empty, the reason may be XML beautification with spaces which causes caracters() method to be called for XML Entities which are actually empty such as for PubmedArticle Entity itself" 
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
//        System.out.println("     } //" + qName );

        if (qName.equals("PubmedArticle")){//exiting a PubmedArticle Entity
            // Call process to process (e.g. index, store to JSONArray) the document
            processor.process(this.getMlcDoc());
//            System.out.println("PubmedArticle instance nullified" );
            //Indexing done, now nullify Lucene doc for fusrther use
            setMlcDoc(null);
        } else {//exiting an Entity except PubmedArticle
            if(this.insidePubmedArticle()){//exiting an Entity belonging to PubmedArticle
//                System.out.println(" nullified " + getFieldName() + qName);
                // Add text in a field
                if((getFieldName()!=null) & !this.getText().isEmpty()){//Fieldname exists and text content is not empty
                    // create corresponding index field and add it to the Document
                    Field mlcField = new TextField(this.getFieldName() , this.getText(), Field.Store.NO);
                    if(this.isAStoredField(this.getFieldName())){ // The value of this Field is needed, so store it
                         // this stored TextField replaces the TextField created above                        
                        mlcField = new TextField(this.getFieldName() , this.getText(), Field.Store.YES);
                    }
                    if(this.getConflictParentFieldName()!= null) {
                        // For PubDate subelements use StringField which is indexed but not tokenized: the entire String value is indexed as a single token. Therefore, can be used for sorting or accessed through the field cache.
                        if(this.getConflictParentFieldName().equals("PubDate")){
                            // this StringField replaces the TextField created above
                            mlcField = new StringField(this.getFieldName(), this.getText(), Field.Store.YES);
                            Field sdvField = new SortedDocValuesField(this.getFieldName(), new BytesRef(this.getText())); 
    //                        System.out.println(sdvField.fieldType().docValuesType());
                            getMlcDoc().add(sdvField);
                        } 
                    } 
    //                    System.out.println("New field added : " + getFieldName() + " - " + this.getText() );
                    getMlcDoc().add(mlcField);
                    // Empty the text variable to be used by next element
                    this.setText("");
                }
                //At the end of AbstractText, nullify AbstractText_Label variable 
                if(getFieldName()!= null){
                    if( getFieldName().equals("AbstractText") & getAbstractText_Label() != null){
                        setAbstractText_Label(null);
                    }
                }   
                this.setFieldName(null);
                if(isAConflictParentField(qName)){//its a Conflict Parent Field 
                    if(this.getConflictParentFieldName().equals(qName)){//avoid to replace ConflictParentFieldName for Elements which are Conflict themselfs (case AffiliationInfo which is Conflict and Conflict Parent)
                        //update FLAG variable
                        this.setConflictParentFieldName(null);
//                        System.out.println("\t  setConflictParentFieldName null " );
                    }
                }
            } else {//exiting an Entity not belonging to PubmedArticle 
                if (qName.equals("PubmedArticleSet")) {//The end of the process
//                    System.out.println("\t *** PubmedArticleSet Indexing End ***" );
                } else {
                    //XML formar error
                    //Excecution shouldn't reach here - not a PubmedArticleSet or PubmedArticle and not a PubmedArticle sub-Entity
                    //allowed XML Elements are PubmedArticleSet is PubmedArticle and PubmedArticle sub-elements
                    System.out.println("Warning: XML format error :\n\t" + qName + " not a valid XML Element end (PubmedArticleSet, PubmedArticle, PubmedArticle sub-Entity).  ");
                } 
            }
        }
    }

    /**
     * @return true if parsing is inside a PubmedArticle XML Entity, false Otherwise.
     *      Proper work of this functions depends on adequate initiation and deletion of mlcDoc object (when entering and leaving a PubmedArticle Entity)
    */
    public boolean insidePubmedArticle() {
       return (this.getMlcDoc() != null);
    }
    
    /**
     * @return true if parsing is inside a Date XML Entity (one of the Fields contained in DateFields), false Otherwise.
      Proper work of this functions depends on adequate update of ConflictFieldName variable (when entering and leaving a Date Entiry)
    */
    public boolean insideConflictParent() {
       return (this.getConflictParentFieldName() != null);
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
//        this.text = text.trim();
        this.text = text;
    }

    /**
     * @return the fieldName
     */
    public String getFieldName() {
        return fieldName;
    }

    /**
     * @param FieldName the fieldName to set
     */
    public void setFieldName(String FieldName) {
        // Keep previous field for cases of removing bold, italics and other pseudo-entities
        if(this.getFieldName() != null){
            setPreviousFieldName(this.getFieldName());
        }
        // Check for pseudo-entities to be ignored
        if(!isIgnoredField(FieldName)){// it is a normal entity or null
            this.fieldName = FieldName;
        } else { // it is a pseudo-entity  
            this.fieldName = this.getPreviousFieldName(); // keep previous field
        }
    }

    /**
     * @return the mlcDoc
     */
    public Document getMlcDoc() {
        return mlcDoc;
    }

    /**
     * @param mlcDoc the mlcDoc to set
     */
    public void setMlcDoc(Document mlcDoc) {
        this.mlcDoc = mlcDoc;
    }

    /**
     * @return the ConflictFieldName
     */
    public String getConflictParentFieldName() {
        return conflictParentFieldName;
    }

    /**
     * @param DateFieldName the ConflictFieldName to set
     */
    public void setConflictParentFieldName(String DateFieldName) {
        this.conflictParentFieldName = DateFieldName;
    }
    
    /**
     * @param qName the fieldName to check if in list of ConflictParentFieldNames
     * @return true if qName is a ConflictParentFieldName, false otherwise
     */
    private boolean isAConflictParentField(String qName) {
        return this.conflictParentFields.contains(qName);
    }
    
    /**
     * @param qName the fieldName to check if in list of ignoreFields
     * @return true if qName is a ignoreFields, false otherwise
     */
    private boolean isIgnoredField(String qName) {
        return this.ignoreFields.contains(qName);
    }
    
    /**
     * Checks whether a given field is to be stored in lucene index (i.e, if contained in storedFields)
     *      if storedFields is empty, all fields are stored.
     * @param qName the fieldName to check if in list of storedFields
     * @return true if qName is a storedField, false otherwise
     */
    private boolean isAStoredField(String qName) {
        if (this.storedFields.isEmpty()){
            return true;
        } else {
            return this.storedFields.contains(qName);
        }
    }
    
    /**
     * @return the AbstractText_Label
     */
    public String getAbstractText_Label() {
        return AbstractText_Label;
    }

    /**
     * @param AbstractText_Label the AbstractText_Label to set
     */
    public void setAbstractText_Label(String AbstractText_Label) {
        this.AbstractText_Label = AbstractText_Label;
    }
    
    /**
     * Add extra fields to be stored/handled
     * @param storedField 
     */
    public final void addStoredField(String storedField){
        storedFields.add(storedField);
    }

    /**
     * @return the previousField
     */
    public String getPreviousFieldName() {
        return previousField;
    }

    /**
     * @param previousField the previousField to set
     */
    public void setPreviousFieldName(String previousField) {
        this.previousField = previousField;
    }
}

