# OBO Harvester

Harvests hierarchical relations (i.e. ISA) from ontologies given in an OBO file and creates a collection in a MongoDB database with all these concept-concept relations.

## Requirements
java 1.8.0_91

MongoDB v2.4.9

## How to use

The main method is in OBOHarvester class and takes one argument, the file with settings.
 
### Configure
 Update the configurations in /settings.yaml
 
* baseFolder: the path to the folder where OBO ontologies are stored (e.g. 'D:\tmp files')
* inputOBOName: the name of the ontology obo file to be processed (e.g. 'doid'). The corresponging file (i.e. doid.obo ) should be accessible in the baseFolder. This name is also used as collection name in MongoDB.
* mongodb: Details about the MongoDB database to be used
    * host: The IP of the hsot (e.g. 127.0.0.1) 
    * port: The port (e.g. 27017)
    * dbname: The name of the Database (e.g. harvseting)
    

### Dependencies
All required libraries are listed in /dist/lib/Library Dependencies.txt

You should download them and put them in the /dist/lib/ before using HarvestOBO.jar

### Run

A precompiled version is available in /dist/HarvestOBO.jar

Example call:

> java -jar HarvestOBO.jar "D:\tmp files\settings.yaml"