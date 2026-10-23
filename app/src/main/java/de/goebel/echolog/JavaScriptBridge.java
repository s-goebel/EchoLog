
package de.goebel.echolog;

import static android.content.Context.MODE_PRIVATE;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import android.webkit.JavascriptInterface;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class JavaScriptBridge {
    private final Context context;

    public JavaScriptBridge(Context context) {
        this.context = context.getApplicationContext();
    }

    @JavascriptInterface
    public void closeQuestionnaire() {
        // the first filled out questionnaire (=start-questionnaire) starts the study:
        SharedPreferences prefs = context.getSharedPreferences("echolog", MODE_PRIVATE);

        if(!prefs.getBoolean("studyStarted", false)) {
            prefs.edit().putBoolean("studyStarted", true).apply();
            // Sampling Actions only AFTER answering the first Questionnaire:
            SamplingActions.setMidnightAlarm(context);
            SamplingActions.setDailyTimers(prefs.getString("study", "study_design.json"), context);
        }
        else {
            // we delete the active questionnaire with a total cleanup:
            String activeQuestionnaireString = prefs.getString("active_questionnaire", null);
            if (activeQuestionnaireString != null) {
                try {
                    JSONObject activeObj = new JSONObject(activeQuestionnaireString);
                    if (studyEndedProcedure(context, activeObj, prefs)) return;

                    SamplingActions.cleanUpQuestionnaire(context, activeObj);
                } catch (Exception e) {
                    Log.e("EchoLog", "JavaScriptBridge.closeQuestionnaire: Could not parse active questionnaire", e);
                }
                prefs.edit().remove("active_questionnaire").apply();
            }
        }

        Intent intent = new Intent(context, QuestionnaireListActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(intent);
    }


    @JavascriptInterface
    public boolean saveInCSV(String csvName, String header, String data) {
        // Exit, if there is no csvName:
        if (csvName == null || csvName.trim().isEmpty()) {
            Log.e("EchoLog", "JavaScriptBridge.saveInCSV: csvName is null or Empty, can not save data");
            return false;
        }
        // replace "/", "." and "\" in CSV-Name with _: => no filepath-hacking
        if(csvName.contains("/") || csvName.contains("\\")) {
            Log.w("EchoLog", "JavaScriptBridge.saveInCSV: csvName contains \"\\\" or \"/\" => they will be replaced with _.");
            csvName = csvName.replaceAll("[/\\\\]", "_");
            Log.w("EchoLog", "JavaScriptBridge.saveInCSV: new csvName-String = " + csvName);
        }

        // replace \n in header => only oneliners!
        if (header == null) header = "";
        if(header.contains("\n")) {
            Log.w("EchoLog", "JavaScriptBridge.saveInCSV: header contains \"\\n's. They will be replaced by \"|\"");
            header = header.replace("\n", "|");
            Log.w("EchoLog", "JavaScriptBridge.saveInCSV: new header-String = " + header);
        }

        // replace \n in data => only oneliners!
        if (data == null) data = "";
        if(data.contains("\n")) {
            Log.w("EchoLog", "JavaScriptBridge.saveInCSV: data contains \"\\n's. They will be replaced by \"|\"");
            data = data.replace("\n", "|");
            Log.w("EchoLog", "JavaScriptBridge.saveInCSV: new data-String = " + data);
        }
        // IMPORTANT: maybe it's better to Exit if data = "" but this should not break the program

        try {
            File file = new File(context.getFilesDir(), csvName);

            if(!file.exists()) {
                FileOperations.append(csvName, header + "\n", context);
            }
            FileOperations.append(csvName, data + "\n", context);
            Log.i("EchoLog", "JavaScriptBridge.saveInCSV: \nheader:" + header + "\ndata:" + data + "\ncsvName: " + csvName + "\nIs now saved here: " + file.getAbsolutePath());
            return true;
        } catch (Exception e) {
            Log.e("EchoLog", "JavaScriptBridge.saveInCSV: the File \"" + csvName + "\" could not be created/changed.", e);
            return false;
        }
    }


    private boolean studyEndedProcedure(Context ctx, JSONObject activeObj, SharedPreferences prefs) {
        if (!prefs.getBoolean("STUDY_ENDED_NOW", false)) {
            return false;
        }

        try {
            JSONObject studyConfig = JsonOperations.readJSONObject(prefs.getString("study", "study_design.json"), ctx);
            JSONObject endPlan = studyConfig.getJSONObject("sampling_designs").getJSONObject("end");

            String activeTitle = activeObj.optString("title", "");
            String endTitle = endPlan.optString("title", "");

            boolean isEndQuestionnaire = false;

            if (activeTitle.equals(endTitle) &&
                    activeObj.optString("filepath_html", "").equals(endPlan.optString("filepath_html", "")) &&
                    activeObj.optString("filepath_json", "").equals(endPlan.optString("filepath_json", ""))) {
                isEndQuestionnaire = true;
            }

            if (isEndQuestionnaire) {
                Log.i("EchoLog", "JavaScriptBridge.studyEndedProcedure: End-Questionnaire successfully filled out!");

                // set shared prefs to stop future timers:
                prefs.edit().putBoolean("END_QUESTIONNAIRE_COMPLETED", true).apply();

                // clean up everything:
                SamplingActions.cleanUpQuestionnaire(ctx, activeObj);
                prefs.edit().remove("active_questionnaire").apply();

                Intent intent = new Intent(ctx, QuestionnaireListActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                ctx.startActivity(intent);

                return true;
            }
        } catch (Exception e) {
            Log.e("EchoLog", "JavaScriptBridge.studyEndedProcedure: Error while validating the end questionnaire.", e);
        }
        return false;
    }

    public static void saveSkippedCsvRow(Context context, JSONObject jsonObject) {
        if (jsonObject == null) {
            Log.e("EchoLog", "JavaScriptBridge.saveSkippedCsvRow: jsonObject is null");
            return;
        }

        try {
            // 1. read basic information from json
            String csvName = jsonObject.optString("csv_savefile", "questionnaire_results.csv");
            String cancelledValue = "CANCELLED_QUESTIONNAIRE";

            List<String> headers = new ArrayList<>();
            List<String> data = new ArrayList<>();

            // 2. put basic information in json
            headers.add("datetime_of_start");
            headers.add("datetime_of_completion");
            headers.add("errors");

            data.add("");
            data.add("");
            data.add("CANCELLED_BY_USER"); // information in column 'errors'

            // 3.build up header (IMPORTANT: newer versions of this prototype should build a function for that and exclude it from the js and use the function in saveCSV as well => but it's too time consuming in the final production stress)
            JSONArray pages = jsonObject.optJSONArray("pages");
            if (pages != null) {
                Set<String> seenVars = new HashSet<>();

                for (int i = 0; i < pages.length(); i++) {
                    JSONObject page = pages.optJSONObject(i);
                    if (page == null) continue;

                    String pageId = page.optString("page_id", "page_" + i);
                    JSONArray items = page.optJSONArray("items");
                    if (items == null) continue;

                    for (int j = 0; j < items.length(); j++) {
                        JSONObject item = items.optJSONObject(j);
                        if (item == null) continue;

                        String varName = item.optString("variable_name", "var_" + j);
                        String fullVar = pageId + "::" + varName;

                        if (seenVars.contains(fullVar)) continue;
                        seenVars.add(fullVar);

                        String itemType = item.optString("item_type", "");

                        // multiple choice
                        if ("multiple_choice".equals(itemType)) {
                            JSONArray options = item.optJSONArray("options");
                            Set<String> seenOpts = new HashSet<>();

                            if (options != null) {
                                for (int k = 0; k < options.length(); k++) {
                                    JSONObject opt = options.optJSONObject(k);
                                    if (opt != null) {
                                        String optId = opt.optString("option_id", "opt_" + k);
                                        if (!seenOpts.contains(optId)) {
                                            seenOpts.add(optId);
                                            headers.add(fullVar + "::" + optId);
                                            data.add(cancelledValue);
                                        }
                                    }
                                }
                            }
                            if (item.optBoolean("allow_other", false)) {
                                headers.add(fullVar + "::other");
                                data.add(cancelledValue);
                            }
                        } else {
                            // other than mc
                            headers.add(fullVar);
                            data.add(cancelledValue);
                        }
                    }
                }
            }

            // 4. Lists => Strings + escaping
            StringBuilder headerBuilder = new StringBuilder();
            StringBuilder dataBuilder = new StringBuilder();

            for (int i = 0; i < headers.size(); i++) {
                headerBuilder.append(escapeCsv(headers.get(i)));
                dataBuilder.append(escapeCsv(data.get(i)));

                if (i < headers.size() - 1) {
                    headerBuilder.append(",");
                    dataBuilder.append(",");
                }
            }

            // 5. save in file (classic)
            JavaScriptBridge bridge = new JavaScriptBridge(context);
            bridge.saveInCSV(csvName, headerBuilder.toString(), dataBuilder.toString());

        } catch (Exception e) {
            Log.e("EchoLog", "JavaScriptBridge.saveSkippedCsvRow: Failed to process skipped row", e);
        }
    }

    // helper method for saveSkippedCsvRow
    private static String escapeCsv(String value) {
        if (value == null) return "\"\"";
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
 
 
 
 
 
