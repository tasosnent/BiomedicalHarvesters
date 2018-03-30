/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

//Commented for Server compile 
package pubmedIndexing;

import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriter;

/**
 * Indexes Lucene documents into given Lucene index
 *      Called by PubmedArticleHandler
 * @author tasosnent
 */
public class IndexingProcessor {
    
    private IndexWriter writer = null;
    /**
     * The (Lucene) index to documents (e.g. articles found by corresponding PubmedArticleHandler)
     * @param writer 
     */
    public IndexingProcessor(IndexWriter writer){
        this.writer = writer;    
    }
    
    /**
     * Indexes Lucene document into specified Lucene index
     * @param mlc   The Lucene document to index
     */
    public void process( Document mlc){
         try{
                writer.addDocument(mlc);

            } catch (IOException e) {
                System.out.println(" caught a (in MedlineCitationProcessor : process) " + e.getClass() +
                "\n with message: " + e.getMessage());
            }
    }
}
