 
package de.goebel.echolog;
 
import static android.content.Context.MODE_PRIVATE;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
 
public class MidnightReceiver extends BroadcastReceiver {
 
    @Override
    public void onReceive(Context ctx, Intent intent) {
        SharedPreferences prefs = ctx.getSharedPreferences("echolog", MODE_PRIVATE);
        if(!prefs.getBoolean("studyStarted", false)) {
            Log.i("EchoLog", "MidnightReceiver.onReceive started.");
            SamplingActions.setDailyTimers("study_design.json", ctx);
            SamplingActions.setMidnightAlarm(ctx); // if the study started
            return;
        }
    }
}
 
 
 
 
 
 
