
package de.goebel.echolog;

import static android.content.Context.MODE_PRIVATE;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

public class SamplingActions {
    public static void cleanUpQuestionnaire(Context ctx, JSONObject jsonObject) {
        if (jsonObject == null) return;

        try {
            // get every information of the jsonObject we need:
            String datetime = jsonObject.optString("datetime", "");
            String filepathHtml = jsonObject.optString("filepath_html", "");
            String filepathJson = jsonObject.optString("filepath_json", "");

            String timeBase = jsonObject.has("anchor_datetime") ? jsonObject.optString("anchor_datetime") : datetime;
            int alarmRequestCode = Math.abs(Objects.hash(timeBase, filepathHtml, filepathJson));

            String notificationIdString = timeBase + "___" + filepathHtml + "___" + filepathJson;
            int notificationId = Math.abs(notificationIdString.hashCode());

            // Close Notification:
            android.app.NotificationManager nm = (android.app.NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.cancel(notificationId);
            }

            // delete all running alerts of the future:
            AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            Intent intent = new Intent(ctx, AlarmReceiver.class);
            // Recreate the whole pending intent object of the beginning:
            PendingIntent pi = PendingIntent.getBroadcast(
                    ctx,
                    alarmRequestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            if (am != null && pi != null) {
                am.cancel(pi);
                pi.cancel();
            }

            // delete the questionnaire from the shared prefs:
            SharedPreferences prefs = ctx.getSharedPreferences("echolog", MODE_PRIVATE);
            JSONArray pendingArray = new JSONArray(prefs.getString("pending_questionnaires", "[]"));
            JSONArray updatedArray = new JSONArray();

            // go through all questionnaires:
            for (int i = 0; i < pendingArray.length(); i++) {
                JSONObject item = pendingArray.getJSONObject(i);
                String itemPathJson = item.optString("filepath_json", "");
                String itemPathHtml = item.optString("filepath_html", "");
                String itemTime = item.has("anchor_datetime") ? item.optString("anchor_datetime") : item.optString("datetime", "");

                // only the questionnaires that are NOT the questionnaire we search for are put into the new updated array:
                if (!(filepathJson.equals(itemPathJson) && filepathHtml.equals(itemPathHtml) && timeBase.equals(itemTime))) {
                    updatedArray.put(item);
                }
            }
            // set the new array:
            prefs.edit().putString("pending_questionnaires", updatedArray.toString()).apply();

            Log.i("EchoLog", "SamplingActions.cleanUpQuestionnaire: Cleared the Questionnaire with the ID: " + notificationId);

        } catch (Exception e) {
            Log.e("EchoLog", "SamplingActions.cleanUpQuestionnaire: Could not clear the Questionnaire!", e);
        }
    }

    @SuppressLint("ScheduleExactAlarm")
    public static void setTimerForDateTime(JSONObject config, Context ctx) {
        // a method, which takes an JS-Object of one single date time sampling appointment and sets it
        try {
            Log.i("EchoLog", "SamplingActions.setTimerForDateTime: config.toString() =\n" + config.toString());

            // we need the target Datetime in milliseconds:
            String datetime = config.getString("datetime");
            long targetTimeMillis = 0;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ZonedDateTime zdt = LocalDateTime.parse(datetime)
                        .atZone(ZoneId.systemDefault());
                targetTimeMillis = zdt.toInstant().toEpochMilli();
            }

            // if the target Datetime lies in the past, we skip everything else!
            if (targetTimeMillis <= System.currentTimeMillis()) {
                Log.e("EchoLog", "SamplingActions.setTimerForDateTime: The DateTime-Appointment lies in the past or the Android Version is too old!\n");
                return;
            }

            // fill the intent for the AlarmReceiver with all the information, to pass it through:
            Intent i = new Intent(ctx, AlarmReceiver.class);

            // better method: just give the accessor the json-object
            i.putExtra("jsonObjectString", config.toString());

            // create pendingIntent for the AlarmManager
            PendingIntent pi = PendingIntent.getBroadcast(
                    ctx,
                    Math.abs(Objects.hash(config.has("anchor_datetime") ? config.optString("anchor_datetime") : datetime, config.getString("filepath_html"), config.getString("filepath_json"))),
                    i,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            // set alert:
            AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, targetTimeMillis, pi);
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, targetTimeMillis, pi);
            }

            // set "killer" (the timer, that just clears everything in the end, if the user missed the questionnaire):
            if(!config.has("anchor_datetime")) {
                long durationMillis = 0;
                String durationStr = config.optString("duration", "01:00:00");
                LocalTime parsedTime = TimeOperations.getLocalTime(durationStr);
                if(parsedTime != null) {
                    durationMillis = parsedTime.toSecondOfDay() * 1000L;
                }
                long extraSnoozeMillis = 0;
                if (config.optBoolean("allow_snoozing") && config.has("snooze")) {
                    try {
                        JSONObject snooze = config.getJSONObject("snooze");
                        int times = snooze.optInt("times", 0);
                        int snoozeTime = snooze.optInt("snooze_time", 0);
                        extraSnoozeMillis = (times + 1) * snoozeTime * 1000L;
                    } catch (Exception e) {
                        Log.e("EchoLog", "SamplingActions.setTimerForDateTime: ", e);
                        return;
                    }
                }

                // important: IF BOTH ARE ZERO => instant-kill
                long adder = durationMillis != 0 ? durationMillis : extraSnoozeMillis;
                long expireMillis = targetTimeMillis + adder;

                Intent cancelIntent = new Intent(ctx, CancelQuestionnaireReceiver.class);
                cancelIntent.putExtra("jsonObjectString", config.toString());

                int baseId = Math.abs(Objects.hash(datetime, config.getString("filepath_html"), config.getString("filepath_json")));
                PendingIntent cancelPi = PendingIntent.getBroadcast(
                        ctx,
                        baseId + 2,
                        cancelIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                );

                AlarmManager amExp = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
                if (amExp != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        amExp.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, expireMillis, cancelPi);
                    } else {
                        amExp.setExact(AlarmManager.RTC_WAKEUP, expireMillis, cancelPi);
                    }
                    Log.i("EchoLog", "SamplingActions.setTimerForDateTime: Scheduled Expiration-Killer for " + durationStr + " (+ " + (extraSnoozeMillis/1000) + "s snooze) in the future.");
                }
            }

            Log.i("EchoLog", "SamplingActions.setTimerForDateTime: the Timer has started. datetime: " + datetime + ", HTML: " + config.getString("filepath_html") + ", JSON: " + config.getString("filepath_json"));
        }
        catch (Exception e) {
            Log.e("EchoLog", "SamplingActions.setTimerForDateTime: Could not Start the Timer.", e);
        }
    }

    public static void setDailyTimers(String filepath, Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences("echolog", MODE_PRIVATE);

        // no execution before the study started!
        if(!prefs.getBoolean("studyStarted", false)) {
            return;
        }

        // clear all pending questionnaires (=> no errors with old buttons)
        ctx.getSharedPreferences("echolog", MODE_PRIVATE)
                .edit().remove("pending_questionnaires").apply();

        // get object with all plans:
        Log.i("EchoLog", "SamplingActions.setDailyTimers: Getting JSON-Object. Filepath: " + filepath);
        JSONObject jsonObject = JsonOperations.readJSONObject(filepath, ctx);

        try {
            Log.i("EchoLog", "SamplingActions.setDailyTimers: Getting all Sampling Designs. Filepath: " + filepath);
            // Extract the sampling plan lists as JSON-Objects:
            JSONObject allSamplingPlans = (JSONObject) jsonObject.get("sampling_designs");
            Log.i("EchoLog", "SamplingActions.setDailyTimers: Got all of the Sampling-Plans: \n" + allSamplingPlans.toString());

            Log.i("EchoLog", "SamplingActions.setDailyTimers: Testing if study started or ended already.");
            String studyStartString = jsonObject.optString("study_start");
            String endString = jsonObject.optString("end");
            LocalDateTime studyStart = TimeOperations.getLocalDateTime(studyStartString);
            LocalDateTime end = TimeOperations.getLocalDateTime(endString);
            LocalDateTime now = LocalDateTime.now();
            if(now.isBefore(studyStart)) {
                Log.i("EchoLog", "SamplingActions.setDailyTimers: The study has not started yet. Doing nothing!\nIt starts at: " + studyStartString + ". Now is: " + now.toString());
                return;
            }
            // -------------------------------------------------------------------- END-Questionnaire!
            else if(now.isAfter(end)) {
                Log.i("EchoLog", "SamplingActions.setDailyTimers: The study has ended already. User is recommended to fill out the final questionnaire. Study ended here: " + endString);
                SamplingActions.reminderEndQuestionnaire(allSamplingPlans.getJSONObject("end"), jsonObject, ctx);
                return; // Safety: do not start any other questionnaire!
            }


            JSONArray dateTimePlans = (JSONArray) allSamplingPlans.get("datetime");
            Log.i("EchoLog", "SamplingActions.setDailyTimers: Setting DateTimePlans.\n" + dateTimePlans.toString());
            setDateTimePlans(dateTimePlans, jsonObject, ctx);

            JSONArray sinceStart = (JSONArray) allSamplingPlans.get("since_start"); // ~ local_study_start!
            Log.i("EchoLog", "SamplingActions.setDailyTimers: Setting SinceStartPlans.\n" + sinceStart.toString());
            setSinceStartPlans(sinceStart, jsonObject, ctx);

            JSONArray periodic = (JSONArray) allSamplingPlans.get("periodic");
            Log.i("EchoLog", "SamplingActions.setDailyTimers: Setting PeriodicPlans.\n" + periodic.toString());
            setPeriodicPlans(periodic, jsonObject, ctx);

            JSONArray periodicRandomized = (JSONArray) allSamplingPlans.get("periodic_randomized");
            Log.i("EchoLog", "SamplingActions.setDailyTimers: Setting PeriodicRandomizedPlans.\n" + periodicRandomized.toString());
            setPeriodicRandomizedPlans(periodicRandomized, jsonObject, ctx);

        } catch (JSONException e) {
            Log.e("EchoLog", "SamplingActions.setDailyTimers: \"" + filepath + "\" includes no sampling_designs or they are not shaped correctly: \n" + FileOperations.read(filepath, ctx), e);
            throw new RuntimeException(e);
        }
    }

    private static void setDateTimePlan(JSONObject dateTimePlan, JSONObject config, Context ctx) {
        Log.i("EchoLog", "SamplingActions.setDateTimePlan: " + dateTimePlan.toString());

        // check if the plan is for today and if so, set it!
        String dateOfDatetime = dateTimePlan.optString("datetime").substring(0, 10);
        String localDate = LocalDate.now().toString();
        if(!TimeOperations.isLaterDay(dateOfDatetime, localDate) && !TimeOperations.isLaterDay(localDate, dateOfDatetime)) {
            setTimerForDateTime(dateTimePlan, ctx);
        }
    }

    private static void setDateTimePlans(JSONArray dateTimePlans, JSONObject config, Context ctx) throws JSONException {
        // go through every date-time plan:
        for (int i = 0; i < dateTimePlans.length(); i++) {
            JSONObject currentPlan = dateTimePlans.getJSONObject(i);
            setDateTimePlan(currentPlan, config, ctx);
        }
    }
    private static void setSinceStartPlans(JSONArray sinceStartPlans, JSONObject config, Context ctx) throws JSONException {
        for (int i = 0; i < sinceStartPlans.length(); i++) {
            JSONObject currentPlan = sinceStartPlans.getJSONObject(i);

            // get the start, from where we have to count (study_start or the installation of the study on users phone, local_start)
            String startDateTimeString = "";
            if (currentPlan.optBoolean("from_study_start", false))
                startDateTimeString = config.getString("study_start");
            else
                startDateTimeString = config.getString("local_start");
            if (startDateTimeString.isEmpty()) {
                Log.e("EchoLog", "SamplingActions.setSinceStartPlans: Error while loading current Plan: Problems with loading study_start or local_start:\n\nCurrent Plan:\n" + currentPlan.toString() + "\nConfig:\n" + config.toString() + "\n=>skip Plan!");
                continue;
            }

            // Get the DateTime, where the since-datetime-event takes place
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
            LocalDateTime startDateTime = LocalDateTime.parse(startDateTimeString, formatter);
            LocalDateTime targetDateTime = startDateTime.plusSeconds(currentPlan.optInt("seconds_since_start", 0));


            if (targetDateTime.equals(startDateTime)) {
                Log.e("EchoLog", "SamplingActions.setSinceStartPlans: Error while loading seconds_since_start: seconds_since_start is not set or it's 0!\n" + currentPlan.toString() + "\nConfig:\n" + config.toString() + "\n=>skip Plan!");
                continue;
            }

            // if the target-Date is today
            if(LocalDate.now().equals(targetDateTime.toLocalDate())) {
                // if the scientist gave us the exact number of seconds with targetDateTime
                if(!currentPlan.has("on_that_day_at")) {
                    // just put the datetime in correct format in the currentPlan
                    currentPlan.put("datetime", targetDateTime.format(formatter));
                } else {
                    // we have to get the datetime of today at the time, which is set in "on_that_day_at"
                    DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
                    LocalTime newTime;
                    // try to set newTime to "on_that_day_at":
                    try {
                        newTime = LocalTime.parse(currentPlan.getString("on_that_day_at"), timeFormatter);
                    } catch (Exception e) {
                        // skip, if setting newTime failed:
                        Log.e("EchoLog", "SamplingActions.setSinceStartPlans: Error while loading on_that_day_at: it is not set or not the correct Format (hh:mm:ss)!\n" + currentPlan.toString() + "\nConfig:\n" + config.toString() + "\n=>skip Plan!", e);
                        continue;
                    }
                    // put "datetime" in the current plan => make it a datetime-event!
                    currentPlan.put("datetime", LocalDate.now().atTime(newTime).format(formatter));
                }
                setDateTimePlan(currentPlan, config, ctx);
            }
        }
    }
    private static void setPeriodicPlan(JSONObject periodicPlan, JSONObject config, Context ctx) throws JSONException {

        // Local vs Study start:
        boolean fromStudyStart = false;
        if(periodicPlan.has("from_study_start")) fromStudyStart = periodicPlan.getBoolean("from_study_start");

        LocalDate startDate;

        if(fromStudyStart) startDate = TimeOperations.getLocalDate(config.getString("study_start").substring(0,10));
        else startDate = TimeOperations.getLocalDate(config.getString("local_start").substring(0,10));


        // current Date BEFORE start date? => kill process!
        if(periodicPlan.has("start_date")) {
            if( TimeOperations.isLaterDay(periodicPlan.getString("start_date"), LocalDate.now().toString()) ) return;
        } else if(periodicPlan.has("start_day")) {
            LocalDate planStartDate = startDate;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                planStartDate = startDate.plusDays(periodicPlan.getInt("start_day"));
                if( TimeOperations.isLaterDay(planStartDate.toString(), LocalDate.now().toString()) ) return;
            } else {
                Log.e("EchoLog", "SamplingActions.setPeriodicPlan: Build.VERSION.SDK_INT >= Build.VERSION_CODES.O gave false => wrong date!");
            }
        }
        // current Date AFTER end date? => kill process!
        if(periodicPlan.has("end_date")) {
            if( TimeOperations.isLaterDay(LocalDate.now().toString(), periodicPlan.getString("end_date")) ) return;
        } else if(periodicPlan.has("end_day")) {
            LocalDate planEndDate = startDate;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                planEndDate = startDate.plusDays(periodicPlan.getInt("end_day"));
                if( TimeOperations.isLaterDay(LocalDate.now().toString(), planEndDate.toString()) ) return;
            } else {
                Log.e("EchoLog", "SamplingActions.setPeriodicPlan: Build.VERSION.SDK_INT >= Build.VERSION_CODES.O gave false => wrong date!");
            }
        }

        // 1. specific days of the week
        if(periodicPlan.has("days_of_the_week") && periodicPlan.getJSONArray("days_of_the_week").length() > 0) {
            // if the List of days of the week contains the current day => transform to datetime-style object
            if(jsonArrayContains(periodicPlan.getJSONArray("days_of_the_week"), TimeOperations.getCurrentDayOfWeek())) {
                periodicPlan = periodicPlan.put("datetime", LocalDate.now() + "T" + periodicPlan.getString("time")); // restyle to datetime!
                // Start the re-styled object:
                setDateTimePlan(periodicPlan, config, ctx);
            }
        }
        // 2. interval since study start / local start
        else if(periodicPlan.has("interval_days")) {
            long daysSinceStart = 0;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                daysSinceStart = ChronoUnit.DAYS.between(startDate, LocalDate.now());
            }
            // if the current day is target of the interval => transform to datetime-style object
            if(daysSinceStart % periodicPlan.getInt("interval_days") == 0) {
                periodicPlan = periodicPlan.put("datetime", LocalDate.now() + "T" + periodicPlan.getString("time")); // restyle to datetime!
                // Start the re-styled object:
                setDateTimePlan(periodicPlan, config, ctx);
            }
        }
        else {
            Log.e("EchoLog", "SamplingActions.setPeriodicPlan: Not from_study_start, not from_local_start and no days_of_the_week:\n" + periodicPlan.toString());
            return;
        }
    }
    private static void setPeriodicPlans(JSONArray periodicPlans, JSONObject config, Context ctx) throws JSONException {
        for (int i = 0; i < periodicPlans.length(); i++) {
            JSONObject currentPlan = periodicPlans.getJSONObject(i);
            setPeriodicPlan(currentPlan, config, ctx);
        }
    }
    // IMPORTANT: There is one important problem here: if the user restarts his phone on the same day, random periodic plans could be shown twice or not already shown periodic plans could not be shown, because they lie in the past!

    private static void setPeriodicRandomizedPlans(JSONArray periodicRandomizedPlans, JSONObject config, Context ctx) throws JSONException {
        for (int i = 0; i < periodicRandomizedPlans.length(); i++) {
            JSONObject currentPlan = periodicRandomizedPlans.getJSONObject(i);
            currentPlan.put("time", getRandomTimeBetween(currentPlan.getString("start_time"), currentPlan.getString("end_time")));
            setPeriodicPlan(currentPlan, config, ctx);
        }
    }





    public static void setSnoozeTimer(JSONObject plan, Context ctx) {
        // the timer passed to this function already has been pushed to SamplingActions.setTimerForDateTime before => it already has the correct datetime included
        try {
            if(!plan.optBoolean("allow_snoozing")) return;
            if (!plan.has("snooze")) return;

            JSONObject snoozeObj = plan.getJSONObject("snooze");

            int snoozesLeft = snoozeObj.optInt("times", 0) - 1;
            if (snoozesLeft < 0) {
                plan.put("allow_snoozing", false);
                cleanUpQuestionnaire(ctx, plan);
                return;
            }
            int snoozeTime = snoozeObj.optInt("snooze_time", 0);

            String plansDateTimeString = plan.optString("datetime");
            LocalDateTime plansDateTime = TimeOperations.getLocalDateTime(plansDateTimeString);
            if (plansDateTime == null) return;

            // we need to save the first datetime (without snoozing) in the object! we call it anchor_datetime
            if(!plan.has("anchor_datetime"))
                plan.put("anchor_datetime", plansDateTimeString); // IF this is the first snoozing-time we SET the anchor for the datetime!
            // we also need to save the all in all snooze times BEFORE the snoozing
            if(!plan.has("anchor_snooze_times"))
                plan.put("anchor_snooze_times", snoozesLeft+1); // IF this is the first snoozing-time we SET the anchor!
            // => this is also used later, to test if a plan which is to be set, is snoozing

            LocalDateTime lastPossibleTime = TimeOperations.getLocalDateTime(plan.optString("anchor_datetime")).plusSeconds(snoozeTime*plan.optInt("anchor_snooze_times"));
            if(LocalDateTime.now().isAfter(lastPossibleTime)) {
                cleanUpQuestionnaire(ctx, plan);
                return;
            }

            LocalDateTime nextTime = plansDateTime.plusSeconds(snoozeTime);
            // this while loop checks if the next-time is already passed and continues snoozing until it works:
            while(LocalDateTime.now().isAfter(nextTime)) {
                snoozesLeft -= 1;
                if (snoozesLeft < 0) {
                    plan.put("allow_snoozing", false);
                    cleanUpQuestionnaire(ctx, plan);
                    return;
                }
                nextTime = nextTime.plusSeconds(snoozeTime);
            }
            plan.put("datetime", nextTime.format(TimeOperations.getDateTimeFormatter()));
            snoozeObj.put("times", snoozesLeft);
            setTimerForDateTime(plan, ctx);
        } catch (Exception e) {
            Log.e("EchoLog", "SamplingActions.setSnoozeTimer:", e);
        }
    }









    public static boolean jsonArrayContains(JSONArray array, String value) {
        for (int i = 0; i < array.length(); i++) {
            try {
                if (value.equals(array.getString(i))) {
                    return true;
                }
            } catch (JSONException e) {
                Log.e("EchoLog", "SamplingActions.jsonArrayContains: Could not correctly work with:\n" + array.toString() + "\n", e);
            }
        }
        return false;
    }

    public static String getRandomTimeBetween(String start, String end) {
        if (start == null || end == null)
            throw new IllegalArgumentException("Start and end time must not be null.");
        if (start.equals(end))
            return start;

        try {
            // parser for "HH:mm:ss"
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");

            LocalTime startTime = LocalTime.parse(start, formatter);
            LocalTime endTime   = LocalTime.parse(end, formatter);

            if (endTime.isBefore(startTime))
                throw new IllegalArgumentException("End time must be after start time.");

            long diffSeconds = Duration.between(startTime, endTime).getSeconds();
            long randomOffset = (long) (Math.random() * diffSeconds);
            LocalTime randomTime = startTime.plusSeconds(randomOffset);

            return randomTime.format(formatter);

        } catch (Exception e) {
            throw new RuntimeException("Error generating random time between " + start + " and " + end, e);
        }
    }

    public static void setMidnightAlarm(Context ctx) {
        try {
            Log.i("EchoLog", "SamplingActions.setMidnightAlarm called.");

            LocalDateTime midnight = LocalDateTime.now();
            // if call comes between 00:00 and 00:01
            if(midnight.getHour() == 0 && midnight.getMinute() < 1) {
                midnight = midnight.withMinute(1);
            } else {
                midnight = midnight.plusDays(1).toLocalDate().atTime(0, 1);
            }
            long millis = midnight
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli();

            Intent i = new Intent(ctx, MidnightReceiver.class);

            // create pendingIntent for the AlarmManager
            PendingIntent pi = PendingIntent.getBroadcast(
                    ctx,
                    424242, /* unique ID */
                    i,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi);
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, millis, pi);
            }

            Log.i("EchoLog", "SamplingActions.setMidnightAlarm: The next MidnightAlarm is set for " + midnight.toString());
        }
        catch (Exception e) {
            Log.e("EchoLog", "SamplingActions.setMidnightAlarm: Could not set next Midnight Alarm!", e);
        }
    }

    public static void reminderEndQuestionnaire(JSONObject endPlan, JSONObject studyConfig, Context ctx) {
        try {
            Log.i("EchoLog", "SamplingActions.reminderEndQuestionnaire: Initialize End Questionnaire Reminders.");

            // setting shared prefs correctly:
            SharedPreferences prefs = ctx.getSharedPreferences("echolog", MODE_PRIVATE);
            prefs.edit().putBoolean("STUDY_ENDED_NOW", true).apply();

            // remove all pending questionnaires:
            prefs.edit().remove("pending_questionnaires").apply();

            // 5 reminders:
            int[] reminderHours = {8, 10, 13, 16, 19};
            LocalDate today = LocalDate.now();
            DateTimeFormatter formatter = TimeOperations.getDateTimeFormatter();

            for (int hour : reminderHours) {
                LocalDateTime targetTime = today.atTime(hour, 0, 0);

                if (targetTime.isBefore(LocalDateTime.now())) {
                    continue;
                }

                // clone json-object:
                JSONObject reminderConfig = new JSONObject(endPlan.toString());

                // set datetime (this is always needed, to call SamplingActions.setTimerForDateTime):
                reminderConfig.put("datetime", targetTime.format(formatter));

                // add flag, to identify it correctly in AlarmReceiver
                reminderConfig.put("is_end_reminder", true);

                // for being safe (if somebody messed up the json): NO duration or snoozing, not cancelling the questionnaire
                reminderConfig.remove("duration");
                reminderConfig.remove("allow_snoozing");
                reminderConfig.remove("snooze");

                Log.i("EchoLog", "SamplingActions.reminderEndQuestionnaire: Trying to set reminder for this JSONObject: \n" + reminderConfig.toString());
                // set timer:
                setTimerForDateTime(reminderConfig, ctx);
                Log.i("EchoLog", "SamplingActions.reminderEndQuestionnaire: Reminder set for " + targetTime.toString());
            }

        } catch (Exception e) {
            Log.e("EchoLog", "SamplingActions.reminderEndQuestionnaire: Error while setting End-Reminder!", e);
        }
    }
}






