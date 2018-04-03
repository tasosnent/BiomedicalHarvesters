# LiteratureHarvester

Creates sets of articles relevant to given disease (MeSH topic) from a given dated up to the moment of call.
In particular, for a given disease it creates three collections in a MongoDB database.
1. articles from PubMed with their abstract
2. articles from PubMed with their topics (MeSH descriptor ids)
3. articles from PMC with their full-text

## Requirements
java 1.8.0_91

MongoDB v2.4.9

## How to use

The main method is in MainHarvester class and takes three arguments
 1. args[0]     A DataSetId (e.g. DMD)
 2. args[1]     Mesh heading of the disease (e.g. "Muscular Dystrophy, Duchenne")
 3. args[2]     Date of last update in yyyy/mm/dd format (e.g. "2017/05/01")
 
### Configure
 Update the configurations in /settings.yaml
 
* baseFolder: The directory for indermediate files to be wrtitten (e.g. 'D:\tmp files')
* lastUpdate: Date of last update in yyyy/mm/dd format (e.g. "2017/05/01")
* mongodb: Details about the MongoDB database to be used to store the retrieved sets of articles with corresponding information.
    * host: The IP of the hsot (e.g. 127.0.0.1) 
    * port: The port (e.g. 27017)
    * dbname: The name of the Database (e.g. harvseting)
    
The output collections are created automatically in the given database based on the DataSetId and the date. e.g. If we call the harvester on 11th of Oct 2017, the collections will be named DMD_20171011_pmc, DMD_20171011_pubmed and DMD_20171011_pubmed_MeSH.
 
### Dependencies
All required libraries are listed in /dist/lib/Library Dependencies.txt

You should download them and put them in the /dist/lib/ before using HarvestEntrez.jar

### Run

A precompiled version is available in /dist/HarvestEntrez.jar

Example call:

> java -jar HarvestEntrez.jar "DMD" "Muscular Dystrophy, Duchenne" "2017/05/01"