package de.goebel.echolog;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

public class SnoozeReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        Log.i("EchoLog", "SnoozeReceiver.onReceive: Snooze button clicked");

        // delete notification:
        int notificationId = intent.getIntExtra("notificationId", -1);
        if (notificationId != -1) {
            android.app.NotificationManager nm = (android.app.NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.cancel(notificationId);
        } else {
            Log.e("EchoLog", "SnoozeReceiver.onReceive: There is no notification Id to read! Cannot remove the message.");
        }

        // Here comes snooze logic!
        String jsonObjectString = intent.getStringExtra("jsonObjectString");
        try {
            JSONObject jsonObject = new JSONObject(jsonObjectString);
            SamplingActions.setSnoozeTimer(jsonObject, ctx);
        } catch (JSONException e) {
            Log.e("EchoLog", "SnoozeReceiver.onReceive: Could not read the Json-Object:\n" + jsonObjectString);
        }
    }
}

