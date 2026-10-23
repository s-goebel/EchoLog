package de.goebel.echolog;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

public class CancelQuestionnaireReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        Log.i("EchoLog", "CancelQuestionnaireReceiver.onReceive: Cancel button clicked");

        String jsonObjectString = intent.getStringExtra("jsonObjectString");
        if (jsonObjectString == null || jsonObjectString.trim().isEmpty()) {
            Log.e("EchoLog", "CancelQuestionnaireReceiver.onReceive: jsonObjectString is null or empty!");
            return;
        }

        try {
            // clean up questionnaire:
            JSONObject jsonObject = new JSONObject(jsonObjectString);
            JavaScriptBridge.saveSkippedCsvRow(ctx, jsonObject);
            SamplingActions.cleanUpQuestionnaire(ctx, jsonObject);
        } catch (JSONException e) {
            Log.e("EchoLog", "CancelQuestionnaireReceiver.onReceive: Could not parse the jsonObjectString", e);
        }


    }
}

