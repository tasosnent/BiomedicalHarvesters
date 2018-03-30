/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package pubmedIndexing;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

//add lucene-core-5.3.2.jar
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.MMapDirectory;
//add lucene-analyzers-common-5.3.1.jar
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.Analyzer;
import org.xml.sax.InputSource;
//lucene-queryparser-5.3.1.jar
import org.xml.sax.SAXException;

/**
 * Indexes all documents contained in XML files of the folder specified
 * 
 * @author tasosnent
 */
public class PubmedArticlesIndexer {

    //indexing vars
    private static final int forceMerge = 0; // FLAG: whether to use force merge or not. value 0 means dont use, greater value means forceMerge(forceMerge);
    private int currentFile = 0;
    
    /**
     * Index articles in XML files of given folder docsPath
     * @param docsPath      Folder with XML files to be indexed
     * @param indexPath     Path to the index to be created
     * @throws IOException 
     */
    public void indexDocs(String docsPath, final String indexPath) throws IOException {
        Path path = Paths.get(docsPath);
        if (Files.isDirectory(path)) {
          Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                //log printing
                System.out.print(" \t indexing " + (currentFile++) + " > "+ file.getFileName() + " : " );                 
//                System.out.print(nextFile() + " > " +file.getFileName() + " : ");
              try {
                indexMedlineCitationsEvents( indexPath, file.toString());
              } catch (IOException ignore) {
                // don't index files that can't be read.
              }
              return FileVisitResult.CONTINUE;
            }
          });
        } else {
            //log printing
            System.out.println(" " + new Date().toString() + " not a Directory : " + path);              
        }
      }    
    
    /**
     * Event driven indexing of articles in an XML File 
     * @param indexPath     Path to the index
     * @param XMLfile       XML File with PubMed articles
     * @throws IOException 
     */
    public static void indexMedlineCitationsEvents(String indexPath, String XMLfile)throws IOException{
        //whether to create new index or update existing
        boolean create = false;

        Date start = new Date();
        try {
//            System.out.println("Indexing to directory '" + indexPath + "'...");

//            Directory dir = FSDirectory.open(Paths.get(indexPath));
            Directory dir = MMapDirectory.open(Paths.get(indexPath));
            Analyzer analyzer = new StandardAnalyzer();
            IndexWriterConfig iwc = new IndexWriterConfig(analyzer);

            if (create) {
            // Create a new index in the directory, removing any
            // previously indexed documents:
                iwc.setOpenMode(OpenMode.CREATE);
            } else {
              // Add new documents to an existing index:
                iwc.setOpenMode(OpenMode.CREATE_OR_APPEND);
            }

            // Optional: for better indexing performance, if you
            // are indexing many documents, increase the RAM
            // buffer.  But if you do this, increase the max heap
            // size to the JVM (eg add -Xmx512m or -Xmx1g):
            //
            // iwc.setRAMBufferSizeMB(256.0);

            IndexWriter writer = new IndexWriter(dir, iwc);
            
            //Create an indexing processor for indexing
            IndexingProcessor processor = new IndexingProcessor(writer);
            
            load( processor , XMLfile);

            // NOTE: if you want to maximize search performance,
            // you can optionally call forceMerge here.  This can be
            // a terribly costly operation, so generally it's only
            // worth it when your index is relatively static (ie
            // you're done adding documents to it):
            //
//            TO DO : use forceMerge
            Date startForceMerge = new Date();
            if(forceMerge>0){
             writer.forceMerge(forceMerge);        
            }
            Date endForceMerge = new Date();
            
            writer.close();

            Date end = new Date();
            //log printing
            System.out.println(" " + TimeUnit.MILLISECONDS.toSeconds(end.getTime() - start.getTime()) + " seconds " + "- forceMerge time : " +TimeUnit.MILLISECONDS.toSeconds(endForceMerge.getTime() - startForceMerge.getTime()) + " seconds ");

        } catch (IOException e) {
            //log printing
            System.out.println(" " + new Date().toString() + " caught a " + e.getClass() + " with message: " + e.getMessage());     
        }
    }
    
    /**
     * Event driven parsing of MedLine XML data in file fileName
     * 
     * @param processor     IndexingProcessor to index articles parsed
     * @param fileName      XML file with PubMed articles
     */
    public static void load(IndexingProcessor processor, String fileName) {
        SAXParserFactory factory = SAXParserFactory.newInstance();

        try {
            SAXParser parser = factory.newSAXParser();
            File file = new File(fileName);
            InputStream inputStream= new FileInputStream(file);
            Reader reader = new InputStreamReader(inputStream,"UTF-8");
            InputSource is = new InputSource(reader);
            is.setEncoding("UTF-8");
            PubmedArticleIndexingHandler mlcHandler = new PubmedArticleIndexingHandler(processor);

            parser.parse(is, mlcHandler);

        } catch (ParserConfigurationException e) {
            //log printing
            System.out.println(" " + new Date().toString() + " indexing: caught a " + e.getClass() + " with message: " + e.getMessage());     
        } catch (SAXException | IOException e) {
             //log printing
            System.out.println(" " + new Date().toString() + " indexing: caught a " + e.getClass() + " with message: " + e.getMessage());     
        }
    }
}
