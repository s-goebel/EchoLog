
package de.goebel.echolog;

import android.content.Context;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;

public class JsonOperations {



    public static JSONObject readJSONObject(String jsonPath, Context ctx) {
        String jsonText = FileOperations.read(jsonPath, ctx);
        try {
            return new JSONObject(jsonText);
        } catch (Exception e) {
            Log.e("EchoLog", "JsonOperations.readJSONObject: Error parsing JSON", e);
            return null;
        }
    }

    public static void logAllJsonEntries(JSONObject jsonObject) {
        if (jsonObject == null) {
            Log.i("EchoLog", "JsonOperations.logAllJsonEntries: JSONObject is null");
            return;
        }

        // get all keys
        Iterator<String> keys = jsonObject.keys();

        while (keys.hasNext()) {
            String key = keys.next();
            try {
                Object value = jsonObject.get(key); // get() returns object
                Log.i("EchoLog", "JsonOperations.logAllJsonEntries: \"" + key + "\": " + value);
            } catch (JSONException e) {
                Log.e("EchoLog", "JsonOperations.logAllJsonEntries: Error reading key: " + key, e);
            }
        }
    }
}