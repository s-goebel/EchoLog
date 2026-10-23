
package de.goebel.echolog;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public class FileOperations {

    public static void exportAllCsvFiles(Context context) { // use boolean return type, to catch, if user didnt answer!
        try {
            File dir = context.getFilesDir();
            File[] files = dir.listFiles();
            ArrayList<Uri> csvUris = new ArrayList<>();

            if (files != null) {
                for (File file : files) {
                    if (file.isFile() && file.getName().endsWith(".csv")) {
                        // use FileProvider to push internal file correctly
                        Uri fileUri = FileProvider.getUriForFile(
                                context,
                                context.getPackageName() + ".fileprovider",
                                file
                        );
                        csvUris.add(fileUri);
                        Log.i("EchoLog", "FileOperations.exportAllCsvFiles: Added the File " + file.getName());
                    }
                }
            }

            if (csvUris.isEmpty()) {
                Toast.makeText(context, "No CSV Files found!", Toast.LENGTH_LONG).show();
                return;
            }

            // create SHARING-Dialog for multiple files:
            Intent shareIntent = new Intent(Intent.ACTION_SEND_MULTIPLE);
            shareIntent.setType("text/csv");
            shareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, csvUris);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); // allow other apps to read it!

            // Force it:
            Intent chooser = Intent.createChooser(shareIntent, "Export study data");
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(chooser);

        } catch (Exception e) {
            Log.e("EchoLog", "FileOperations.exportAllCsvFiles: Could not export CSV-Data!", e);
            Toast.makeText(context, "Couldn't export!", Toast.LENGTH_SHORT).show();
        }
    }

    private static final String TAG = "EchoFile";

    public static String read(String filePath, Context context) {
        File file = new File(context.getFilesDir(), filePath);
        if(!file.exists()) {
            Log.w(TAG, "FileOperations.read: The File " + filePath + " doesn't exist.");
            return "";
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {

            String wholeText = "";
            String line;

            while((line = reader.readLine()) != null) {
                wholeText += line + "\n";
            }
            Log.i(TAG, "FileOperations.read: Successfully read the file " + filePath);
            return wholeText;
        } catch (Exception e) {
            Log.e(TAG, "FileOperations.read: Could not read the file " + filePath, e);
            return "";
        }
    }

    public static boolean write(String filePath, String content, Context context) {
        File file = new File(context.getFilesDir(), filePath);
        if(file.exists()) file.delete();
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file, false), StandardCharsets.UTF_8))) {
            if(content == null) {
                content = "";
            }
            writer.write(content);
            if(!content.endsWith("\n")) {
                writer.newLine();
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "FileOperations.write: Could not write \"" + content + "\" to the file " + filePath);
            return false;
        }
    }

    // appends content to the File and returns the new total content of the file:
    public static String append(String filePath, String content, Context context) {
        File file = new File(context.getFilesDir(), filePath);
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file, true), StandardCharsets.UTF_8))) {
            if(content == null) {
                content = "";
            }
            writer.write(content);
            if(!content.endsWith("\n")) {
                writer.newLine();
            }
            return FileOperations.read(filePath, context);
        } catch (Exception e) {
            Log.e(TAG, "FileOperations.append: Could not append \"" + content + "\" to the file " + filePath);
            return "";
        }
    }
}