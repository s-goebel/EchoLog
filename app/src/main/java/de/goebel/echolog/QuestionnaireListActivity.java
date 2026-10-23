package de.goebel.echolog;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.card.MaterialCardView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDateTime;

public class QuestionnaireListActivity extends AppCompatActivity {
    private LinearLayout questionnaireContainer;
    SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_questionnaire_list);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        questionnaireContainer = findViewById(R.id.questionnaire_list_container);
        prefs = getSharedPreferences("echolog", MODE_PRIVATE);
        String filepath = prefs.getString("study", "study_design.json");
        JSONObject jsonObject = JsonOperations.readJSONObject(filepath, getApplicationContext());

        // Catch END if the sampling action didn't:
        try {
            JSONObject allSamplingPlans = (JSONObject) jsonObject.get("sampling_designs");
            String endString = jsonObject.optString("end");
            LocalDateTime end = TimeOperations.getLocalDateTime(endString);
            LocalDateTime now = LocalDateTime.now();
            if(now.isAfter(end)) {
                Log.i("EchoLog", "QuestionnaireListActivity.onCreate: The study has ended already. User is recommended to fill out the final questionnaire. Study ended here: " + endString);

                SamplingActions.reminderEndQuestionnaire(allSamplingPlans.getJSONObject("end"), jsonObject, getApplicationContext());
            }
        } catch (JSONException e) {
            Log.e("EchoLog", "QuestionnaireListActivity.onCreate: Could not trigger end reminder!", e);
        }

        if(prefs.getBoolean("END_QUESTIONNAIRE_COMPLETED", false)) {
            questionnaireContainer.removeAllViews();
            FileOperations.exportAllCsvFiles(this);
            return;
        }
        else if(prefs.getBoolean("STUDY_ENDED_NOW", false)) {
            // LOAD END QUESTIONNAIRE BUTTON
            try {
                // add Questionnaire Button for the end-questionnaire by just reading it from original json!
                // important: we say it's NOT event-based, even if it will be shown EVERY time
                // practical reason: the questionnaire will be highlighted in color
                addQuestionnaireButton(jsonObject.getJSONObject("sampling_designs").getJSONObject("end"), false);
            } catch (JSONException e) {
                Log.e("EchoLog", "QuestionnaireListActivity.onCreate: Error while loading end-Questionnaire! \nCould not read from: \n" + jsonObject.toString());
                Log.e("EchoLog", "QuestionnaireListActivity.onCreate: Doing NOTHING at all instead!", e);
            }
            return; // no other questionnaires!
        }
        loadTimePendingQuestionnaires(this.getApplicationContext());
        loadEventBasedQuestionnaires(jsonObject);
    }

    private void addQuestionnaireButton(JSONObject questionnaire, boolean isEventBased) {
        // get data of event based questionnaire
        String title = questionnaire.optString("title", "");
        if(title.isEmpty())
            title = isEventBased ? "Event Questionnaire" : "Pending Questionnaire";
        String message = questionnaire.optString("message", "");

        // create card ("button" for the questionnaire)
        MaterialCardView cardView = (MaterialCardView) getLayoutInflater().inflate(
                R.layout.event_questionnaire_card,
                questionnaireContainer,
                false
        );

        // fill in TextViews
        TextView nameView = cardView.findViewById(R.id.questionnaire_name_textview);
        TextView descView = cardView.findViewById(R.id.questionnaire_description_textview);

        nameView.setText(title);
        descView.setText(message);

        // color
        if(!isEventBased)
            cardView.setCardBackgroundColor(Color.GREEN);

        // Click-Listener with complete JSON-Object given
        String questionnaireString = questionnaire.toString();
        cardView.setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra("startQuestionnaire", true);
            intent.putExtra("jsonObjectString", questionnaireString);
            startActivity(intent);
        });

        // add card to list
        questionnaireContainer.addView(cardView);

        Log.i("EchoLog", "QuestionnaireListActivity.addQuestionnaireButton: Loaded Questionnaire: " + title + "\n" + questionnaire.toString());
    }

    private void loadTimePendingQuestionnaires(Context ctx) {
        try {
            JSONArray pendingQuestionnaires = new JSONArray(prefs.getString("pending_questionnaires", "[]"));
            for(int i = 0; i < pendingQuestionnaires.length(); i++)  {
                addQuestionnaireButton(pendingQuestionnaires.getJSONObject(i), false);
            }
            Log.i("EchoLog", "QuestionnaireListActivity.loadTimePendingQuestionnaires: Loaded " + pendingQuestionnaires.length() + " pending questionnaires");
        } catch (Exception e) {
            Log.e("EchoLog", "QuestionnaireListActivity.loadTimePendingQuestionnaires: Error getting pending questionnaires", e);
        }

    }

    private void loadEventBasedQuestionnaires(JSONObject studyConfig) {
        Log.i("EchoLog", "QuestionnaireListActivity.loadEventBasedQuestionnaires was called with the following JSON-Object:\n" + studyConfig.toString());
        try {
            JSONObject samplingDesigns = studyConfig.getJSONObject("sampling_designs");
            JSONArray eventBased = samplingDesigns.getJSONArray("event_based");

            // "go through all event based questionnaires"
            for (int i = 0; i < eventBased.length(); i++) {
                addQuestionnaireButton(eventBased.getJSONObject(i), true);
            }

            Log.i("EchoLog", "QuestionnaireListActivity.loadEventBasedQuestionnaires: Loaded " + eventBased.length() + " event-based questionnaires");

        } catch (JSONException e) {
            Log.e("EchoLog", "QuestionnaireListActivity.loadEventBasedQuestionnaires: Error loading event-based questionnaires", e);
        }
    }

}