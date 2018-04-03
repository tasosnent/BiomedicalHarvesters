/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

//Commented for Server compile 
package drugbankHarvester;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

/**
 * Collects JSONObjects and stores them into a JSONArray List
 * @author tasosnent
 */
public class JsonArrayProcessor {
private JSONArray list = null;

    /**
     * The list to store the JSONObjects
     * @param articleList 
     */
    public JsonArrayProcessor(JSONArray articleList) {
        this.list = articleList;
    }
    /**
     * Store this JSONObject to JSONArray List
     * @param article the JSONObject to be added in list
     */
    public void process(JSONObject article){
        //check for exceptional cases if any...
//        if(!something){
            list.add(article);
//        }
    }
}
