
package de.goebel.echolog;

import static android.content.Context.MODE_PRIVATE;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {
    // a class, just for starting Methods if the system is started
    @Override
    public void onReceive(Context context, Intent intent) {
        if(intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {
            SharedPreferences prefs = context.getSharedPreferences("echolog", MODE_PRIVATE);

            // if the study started
            if(!prefs.getBoolean("studyStarted", false)) {
                Log.i("EchoBoot", "BootReceiver.onReceive: Device started. (Re-) Initializing daily timers");
                // Start DailyScheduler
                SamplingActions.setDailyTimers("study_design.json", context);
                return;
            }
        }
    }
}






