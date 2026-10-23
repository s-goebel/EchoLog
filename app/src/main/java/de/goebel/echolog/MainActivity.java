
package de.goebel.echolog;

import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends AppCompatActivity {
    public static final String defaultHTMLName = "questionnaire_view.html";

    private WebView webView;
    private WebSettings webSettings;
    private String currentJSON, currentHTML;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);


        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        webView = findViewById(R.id.web_view);

        webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setAllowFileAccessFromFileURLs(true);
        webSettings.setAllowUniversalAccessFromFileURLs(true);

        webView.addJavascriptInterface(new JavaScriptBridge(this), "EchoNative");

        // new, NO startQuestionnaire-Procedure:
        if(getIntent().getBooleanExtra("startQuestionnaire", false)) {
            String jsonObjectString = this.getIntent().getStringExtra("jsonObjectString");
            try {
                JSONObject jsonObject = new JSONObject(jsonObjectString);

                // adding the active questionnaire into shared prefs, to access it from the js bridge class (close questionnaire methode)
                getSharedPreferences("echolog", MODE_PRIVATE)
                        .edit()
                        .putString("active_questionnaire", jsonObjectString)
                        .apply();

                // the background process always passes the filepaths of the html & json, so we blindly use them:
                showQuestionnaire(jsonObject.getString("filepath_html"), jsonObject.getString("filepath_json"), jsonObjectString);

            } catch (JSONException e) {
                Log.e("EchoLog", "MainActivity.onCreate: The following String could not be interpreted as a JSONObject:\n" + jsonObjectString);
                android.widget.Toast.makeText(this, "Could not load Questionnaire!", android.widget.Toast.LENGTH_LONG).show();
                finish();
                return;
            }
        }

        // FROM USER ******************************************************************************************************************************************************
        // if the user started the activity:
        else {
            SharedPreferences prefs = getSharedPreferences("echolog", MODE_PRIVATE);

            // if this is the first time, the study started:
            if(!prefs.getBoolean("studyStarted", false)) {

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    android.os.PowerManager pm = (android.os.PowerManager) getSystemService(Context.POWER_SERVICE);
                    if (!pm.isIgnoringBatteryOptimizations(getPackageName())) {
                        Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                        intent.setData(Uri.parse("package:" + getPackageName()));
                        startActivity(intent);
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
                    if (am != null && !am.canScheduleExactAlarms()) {
                        Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                        intent.setData(Uri.parse("package:" + getPackageName()));
                        startActivity(intent);
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1001);
                    }
                }
                prefs.edit()
                        .putString("study", "study_design.json")
                        .apply();

                // Problem: all files are stored in assets for the prototype but in future the study is to be downloaded => we need to read them from the normal storage
                // to build a bridge from assets to the normal storage, we walk through all json & html Files in the assets and write them into normal storage in the beginning
                AssetManager assetManager = getApplicationContext().getAssets();

                try {
                    String[] assetFiles = assetManager.list("");

                    if (assetFiles != null) {
                        for (String fileName : assetFiles) {
                            if (fileName.endsWith(".json") || fileName.endsWith(".html")) {
                                String content = getAssetText(fileName);
                                FileOperations.write(fileName, content, getApplicationContext());
                                Log.i("EchoLog", "MainActivity.onCreate: The file \"" + fileName + "\" was added from assets to normal storage!");
                            }
                        }
                    }
                } catch (IOException e) {
                    Log.e("EchoLog", "MainActivity.onCreate: Couldn't write Asset-Files!", e);
                    return;
                }

                Log.i("EchoLog", "MainActivity.onCreate: Trying to start the first Questionnaire");
                try {
                    // get object with all plans:
                    JSONObject studyDesign = JsonOperations.readJSONObject(prefs.getString("study", ""), getApplicationContext());
                    JSONObject startObject = studyDesign.optJSONObject("sampling_designs").optJSONObject("start");
                    String filepathHtml = startObject.optString("filepath_html", defaultHTMLName);
                    String filepathJson = startObject.optString("filepath_json", "");
                    if(filepathJson.isEmpty() && filepathHtml.equals(defaultHTMLName)) throw new Exception("filepath_json does not exist, but it is needed, because filepath_html = " + defaultHTMLName);

                    showQuestionnaire(filepathHtml, filepathJson, studyDesign.toString());
                }
                catch (Exception e) {
                    Log.e("EchoLog", "MainActivity.onCreate: Could not start the study! Good luck....", e);
                }
            }
            // if this is a normal start of the user:
            else {
                Intent intent = new Intent(MainActivity.this, QuestionnaireListActivity.class);
                startActivity(intent);
            }
        }

    }


    protected void showQuestionnaire(String htmlFilename, String jsonFilename, String samplingJson) {
        if(htmlFilename.isEmpty()) htmlFilename = defaultHTMLName;
        // Prevent Errors:
        if((!htmlFilename.contains(".html") && !htmlFilename.contains(".htm"))) {
            Log.e("EchoLog", "MainActivity.showQuestionnaire: htmlFilename doesn't contain \".html\" or \".htm\": " + htmlFilename);
            return;
        }
        // get previously locally saved code
        String html = FileOperations.read(htmlFilename, this.getApplicationContext());
        String json = FileOperations.read(jsonFilename, this.getApplicationContext());

        // Log Problems:
        if(html.isEmpty())
            Log.e("EchoLog", "MainActivity.showQuestionnaire: htmlFilename was \"" + htmlFilename + "\". The File existed, but it has no code in it or doesn't exist. => webView will present \"\".");
        if(json.isEmpty())
            Log.e("EchoLog", "MainActivity.showQuestionnaire: jsonFilename was \"" + jsonFilename + "\". The File does not exist or it has no code in it.");


        // escaping JSON (=> error prevention for JS)
        String escapedJson = json
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "");
        String escapedSamplingJson = samplingJson
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "");

        // HTML-File => Webview
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);

                // provide the JSON-File to JS
                view.evaluateJavascript("window.currentJsonText = '" + escapedJson + "';", null);
                view.evaluateJavascript("window.currentSamplingJSON = '" + escapedSamplingJson + "';", null);
            }
        });

        if (!html.isEmpty()) {
            // create virtual HTML-File, to load it
            webView.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "UTF-8", null);
        } else {
            // use standard HTML-File for presenting normal questionnaires
            webView.loadUrl("file:///android_asset/" + defaultHTMLName);
        }
    }

    protected String getAssetText(String file) {
        String txt = "";
        try {
            InputStream inputStream = getAssets().open(file);
            int size = inputStream.available();
            byte[] buffer = new byte[size];
            inputStream.read(buffer);
            inputStream.close();

            txt = new String(buffer, StandardCharsets.UTF_8);
        } catch (IOException e) {
            Log.e("EchoLog", "MainActivity.getAssetText: Error reading asset file", e);
        }
        return txt;
    }
}
 
 
 
 
 
