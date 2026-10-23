package de.goebel.echolog;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class AlarmReceiver extends BroadcastReceiver {
    public JSONObject helpWithJsonObject(String jsonObjectString) {
        try {
            return new JSONObject(jsonObjectString);
        } catch (JSONException e) {
            Log.e("EchoLog", "AlarmReceiver.helpWithJsonObject: could not interpret as a JSONObject: \n" + jsonObjectString);
            return null;
        }
    }
    @Override
    public void onReceive(Context ctx, Intent intent) {
        String jsonObjectString = intent.getStringExtra("jsonObjectString");
        Log.i("EchoLog", "AlarmReceiver.onReceive: Alarm triggered!\n" + jsonObjectString);

        JSONObject jsonObject = helpWithJsonObject(jsonObjectString);
        if(jsonObject == null) return;
        SharedPreferences prefs = ctx.getSharedPreferences("echolog", Context.MODE_PRIVATE);

        // IF STUDY ENDED:
        boolean finishedEndQuestionnaire = prefs.getBoolean("END_QUESTIONNAIRE_COMPLETED", false);
        boolean finishedStudy = prefs.getBoolean("STUDY_ENDED_NOW", false);
        boolean isEndTimer = jsonObject.optBoolean("is_end_reminder", false);
        if(finishedEndQuestionnaire) {
            Log.i("EchoLog", "AlarmReceiver.onReceive: EndQuestionnaire already finished!");
            return;
        } else if(finishedStudy && !isEndTimer) {
            Log.i("EchoLog", "AlarmReceiver.onReceive: Old timer is to be ignored, because the study ended.");
            return;
        }

        String timeBase = jsonObject.has("anchor_datetime") ? jsonObject.optString("anchor_datetime") : jsonObject.optString("datetime", "");
        String notificationIdString = timeBase + "___" + jsonObject.optString("filepath_html", "") + "___" + jsonObject.optString("filepath_json", "");
        int notificationId = Math.abs(notificationIdString.hashCode());

        if(!jsonObject.has("anchor_datetime") && !jsonObject.optBoolean("is_end_reminder", false)) { // always skipping end-reminder here!
            // save in SharedPrefs as pending questionnaire (only if it is not snoozing!)
            try {
                String existing = prefs.getString("pending_questionnaires", "[]");
                JSONArray pendingArray = new JSONArray(existing);
                pendingArray.put(jsonObject);
                prefs.edit().putString("pending_questionnaires", pendingArray.toString()).apply();
                Log.i("EchoLog", "AlarmReceiver.onReceive: Saved pending questionnaire");
            } catch (Exception e) {
                Log.e("EchoLog", "AlarmReceiver.onReceive: Error saving pending questionnaire", e);
            }
        }


        // Sound:
        try {
            JSONObject config = jsonObject;
            if(!config.optBoolean("quiet_notification", false)) {
                Uri sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
                if (sound == null)
                    sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

                MediaPlayer mp = MediaPlayer.create(ctx, sound);
                if (mp != null) {
                    mp.setLooping(false);
                    mp.start();
                    mp.setOnCompletionListener(MediaPlayer::release);
                }
            }
        } catch (Exception e) {
            Log.e("EchoLog", "AlarmReceiver.onReceive: Error playing alarm sound", e);
        }

        // Notification Channel:
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "echolog_channel",
                    "EchoLog Questionnaires",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notifications for study questionnaires");
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0, 200, 100, 200});

            NotificationManager nm = ctx.getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }

        // Intent for QuestionnaireListActivity
        Intent openIntent = new Intent(ctx, QuestionnaireListActivity.class);
        openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent contentIntent = PendingIntent.getActivity(
                ctx,
                notificationId,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Snooze Button Intent
        PendingIntent snoozePendingIntent = null;
        if(jsonObject.optBoolean("allow_snoozing")) {
            Intent snoozeIntent = new Intent(ctx, SnoozeReceiver.class);
            snoozeIntent.putExtra("jsonObjectString", jsonObjectString);
            snoozeIntent.putExtra("notificationId", notificationId);

            snoozePendingIntent = PendingIntent.getBroadcast(
                    ctx,
                    notificationId,
                    snoozeIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
        }

        // Cancel Button Intent
        Intent cancelIntent = new Intent(ctx, CancelQuestionnaireReceiver.class);
        cancelIntent.putExtra("jsonObjectString", jsonObjectString);
        cancelIntent.putExtra("notificationId", notificationId);

        PendingIntent cancelPendingIntent = PendingIntent.getBroadcast(
                ctx,
                notificationId + 1,
                cancelIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Extract title and message from JSON
        String title = "Questionnaire";
        String message = "Please complete the questionnaire";
        try {
            JSONObject config = new JSONObject(jsonObjectString);
            title = config.optString("title", title);
            message = config.optString("message", message);
        } catch (Exception e) {
            Log.e("EchoLog", "AlarmReceiver.onReceive: Error reading title/message from JSON", e);
        }

        // Format: **title** + newline + message with bold styling
        String fullText = title + "\n" + message;
        SpannableString spannableText = new SpannableString(fullText);
        spannableText.setSpan(
                new StyleSpan(android.graphics.Typeface.BOLD),
                0,
                title.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );

        NotificationCompat.Builder builder = null;
        if(snoozePendingIntent != null) {
            // Notification:
            builder = new NotificationCompat.Builder(ctx, "echolog_channel")
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle("EchoLog")
                    .setContentText(spannableText)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(spannableText))
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setAutoCancel(false)
                    .setOngoing(true)
                    .setVibrate(new long[]{0, 200, 100, 200})
                    .setCategory(Notification.CATEGORY_ALARM)
                    .setContentIntent(contentIntent)
                    .addAction(R.drawable.ic_launcher_foreground, ctx.getString(R.string.snooze), snoozePendingIntent)
                    .addAction(R.drawable.ic_launcher_foreground, ctx.getString(R.string.dont_ask_again), cancelPendingIntent);
        } else {
            // Notification:
            builder = new NotificationCompat.Builder(ctx, "echolog_channel")
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle("EchoLog")
                    .setContentText(spannableText)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(spannableText))
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setAutoCancel(false)
                    .setOngoing(true)
                    .setVibrate(new long[]{0, 200, 100, 200})
                    .setCategory(Notification.CATEGORY_ALARM)
                    .setContentIntent(contentIntent)
                    .addAction(R.drawable.ic_launcher_foreground, ctx.getString(R.string.dont_ask_again), cancelPendingIntent);
        }


        NotificationManagerCompat.from(ctx).notify(notificationId, builder.build());

        Log.i("EchoLog", "AlarmReceiver.onReceive: Notification sent with ID " + notificationId);
    }
}
