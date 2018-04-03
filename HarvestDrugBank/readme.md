# DrugBank Harvester

Parses the DrugBank XML file and creates a collection in a MongoDB database with all Drug-Drug interactions.

## Requirements
java 1.8.0_91

MongoDB v2.4.9

## How to use

The main method is in DrugbankHarvester class and takes one argument, the file with settings.
 
### Configure
 Update the configurations in /settings.yaml
 
* inputFilePath: the path to the  DrugBank XML file (e.g. 'D:\tmp files\DrugBank.xml')
* mongodb: Details about the MongoDB database to be used
    * host: The IP of the hsot (e.g. 127.0.0.1) 
    * port: The port (e.g. 27017)
    * dbname: The name of the Database (e.g. harvseting)
    * collection: The collection to write the drug-drug interactions (e.g. DrugBank)
    

### Dependencies
All required libraries are listed in /dist/lib/Library Dependencies.txt

You should download them and put them in the /dist/lib/ before using HarvestDrugBank.jar

### Run

A precompiled version is available in /dist/HarvestDrugBank.jar

Example call:

> java -jar HarvestDrugBank.jar "D:\tmp files\settings.yaml"