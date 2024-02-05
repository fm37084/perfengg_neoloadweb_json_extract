package neoload_api.runTest;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class Utilities {

    static final  public String TEST = "test";
    static final  public String ELEMENT = "element";
    static final  public String ELEMENT_RAW = "element_raw";
    static final  public String MONITOR_VALUES = "monitor_values";
    static final  public String MONITOR_POINTS = "monitor_points";
    static final  public String FILE_NAME = "fileName";
    static final  public String FILE = "file";
    static final  public String FILE_WRITER = "fileWriter";
    static final  public String BUFFER_WRITER = "bufferedWriter";

    public final static SimpleDateFormat SDF_1 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
    public final static SimpleDateFormat SDF_2 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    static public Map<String, Map<String, Object>> createFilesMap(String suffix) {

        Map<String, Map<String, Object>> fileMap = new HashMap();

        Map<String, Object> testMap = createBufferedWriter(TEST, suffix);
        if (testMap == null)
            return null;

        fileMap.put("test", testMap);

        Map<String, Object> element = createBufferedWriter(ELEMENT, suffix);
        if (element == null)
            return null;

        fileMap.put(ELEMENT, element);


        Map<String, Object> element_raw = createBufferedWriter(ELEMENT_RAW, suffix);
        if (element_raw == null)
            return null;

        fileMap.put(ELEMENT_RAW, element_raw);


        Map<String, Object> monitor_values = createBufferedWriter(MONITOR_VALUES, suffix);
        if (monitor_values == null)
            return null;

        fileMap.put(MONITOR_VALUES, monitor_values);

        Map<String, Object> monitor_points = createBufferedWriter(MONITOR_POINTS, suffix);
        if (monitor_points == null)
            return null;

        fileMap.put(MONITOR_POINTS, monitor_points);

        return fileMap;

    }

    static public void  writeLogs ( String label ,Map<String, Map<String, Object>> fileMap,  String outputString) {

        Map<String, Object> fileEntryMap = fileMap.get(label);

        if (fileEntryMap == null)
            return;

        BufferedWriter bw1 = (BufferedWriter) fileEntryMap.get(BUFFER_WRITER);

        try {
            bw1.write(outputString.toString() + "\n");
        } catch (Exception ex) {
            System.out.println("Error getting the workspace definitionList message [" + ex.getMessage() + "] Cause [" + ex.getCause() + "]");
            return;
        }
    }

    static public void  closeFileMap ( Map<String, Map<String, Object>> fileMap) {

        closeFileEntry (TEST, fileMap);
        closeFileEntry (ELEMENT, fileMap);
        closeFileEntry (ELEMENT_RAW, fileMap);
        closeFileEntry (MONITOR_VALUES, fileMap);
        closeFileEntry (MONITOR_POINTS, fileMap);
    }

    static private void closeFileEntry (String label, Map<String, Map<String, Object>> fileMap) {
        Map<String, Object> fileEntry = fileMap.get(label);

        if (fileEntry == null)
            return;

        try {
            BufferedWriter bw1 = (BufferedWriter)  fileEntry.get(BUFFER_WRITER);
            if (bw1 != null) {
                bw1.flush();
                bw1.close();
            }

            FileWriter fw1 = (FileWriter) fileEntry.get(FILE_WRITER) ;

            if (fw1 != null)
                fw1.close();
        } catch (Exception ex) {
            int m = 0;
        }
    }

    static private Map<String, Object> createBufferedWriter(String type, String suffix) {

        String outFileName = type + "_" + suffix;

        File file = new File(outFileName);

        boolean fileExists = file.exists();

        try {
            if (fileExists) {
                file.delete();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        BufferedWriter bw1 = null;
        File file1 = null;
        FileWriter fw1 = null;

        Map<String, Object> fileTypeMap = new HashMap<String, Object>();

        try {
            file1 = new File(outFileName);
            fw1 = new FileWriter(file1, true);
            bw1 = new BufferedWriter(fw1);

            fileTypeMap.put(FILE_NAME, outFileName);
            fileTypeMap.put(FILE, file1);
            fileTypeMap.put(FILE_WRITER, fw1);
            fileTypeMap.put(BUFFER_WRITER, bw1);

        } catch (Exception ex) {
            System.out.println("Error getting the workspace definitionList message [" + ex.getMessage() + "] Cause [" + ex.getCause() + "]");
            return null;
        }

        System.out.println("End writeOutResults");
        return fileTypeMap;
    }

    static public JSONArray makeRestApiCall (String neoloadWebUrl, String token, String requestString) {

        StringBuilder jsonString = new StringBuilder();

        boolean found = false;
        for (int i=0;   i < 10; i++) {
            try {
                //String url_txt = "https://"+neoloadWebUrl+"/v3/workspaces";
                String url_txt = "https://" + neoloadWebUrl + requestString;
                URL url = new URL(url_txt);
                //URL url = new URL("http://"+neoloadWebUrl+requestString);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();

                conn.setDoOutput(true);
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("accountToken", token);

                if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {

                    int xxx = conn.getResponseCode();
                    String xxx1 = conn.getResponseMessage();

                    System.out.println("error on web  url (there is an error with this call- " + requestString);
                    System.out.println("there is an error with this call- " + requestString);
                    throw new RuntimeException("Failed : HTTP error code : "
                            + conn.getResponseCode());

                }

                BufferedReader br = new BufferedReader(new InputStreamReader(
                        (conn.getInputStream())));

                String output;

                //	System.out.println("Output from Server .... \n");
                while ((output = br.readLine()) != null) {
                    jsonString.append(output);
                    //		System.out.println(output);
                }

                conn.disconnect();
                found = true;

                break;

            } catch (MalformedURLException e) {
                System.out.println("Malform execption : " + e.getMessage());
            } catch (IOException e) {
                System.out.println("IOexecption : " + e.getMessage());
            } catch (Exception ex) {
                System.out.println("Execption : " + ex.getMessage());
            }
        }

        if (!found)
            return null;

        String jsonResultString = jsonString.toString();

        if (jsonResultString.charAt(0) == '[');
        else
            jsonResultString = '[' + jsonResultString + ']';

        JSONArray ja = new JSONArray (jsonResultString);

        return ja;

    }

    public static long convertStringDateToMillis_1 (String dateTimeString) {

        long  returnMillis = 0;

        try {
            Date date = SDF_1.parse(dateTimeString);
            returnMillis =  date.getTime();
        } catch (Exception ex) {
            int g = 0;
        }

        return returnMillis;
    }

    public static long convertStringDateToMillis_2 (String dateTimeString) {

        long  returnMillis = 0;

        try {
            Date date = SDF_2.parse(dateTimeString);
            returnMillis =  date.getTime();
        } catch (Exception ex) {
            int g = 0;
        }

        return returnMillis;
    }

    static public String  getDataInResultName (String resultName, int number) {
        String[]  resultNameArray = resultName.split("_", 7);

        String resultValue = " ";

        if (resultNameArray.length > 3) {
            resultValue = resultNameArray[number];
        }

        return resultValue;
    }

    static public long changeLabelLong (JSONObject jsonObject, String oldLabel, String newLabel) {
        long value = jsonObject.getLong(oldLabel);
        jsonObject.put(newLabel, value);
        jsonObject.remove(oldLabel);

        return value;
    }
    static public String  changeLabelString (JSONObject jsonObject, String oldLabel, String newLabel) {
        String value = jsonObject.getString(oldLabel);
        jsonObject.put(newLabel, value);
        jsonObject.remove(oldLabel);

        return value;
    }

    static public String createPath (JSONObject jsonObject) {

        String pathString = "";

        if (jsonObject.has("path")) {
            JSONArray pathArray =   jsonObject.getJSONArray("path");

            if (pathArray != null) {
                boolean firstTime = true;
                for (Object pathObjectObject : pathArray) {
                    String path = (String) pathObjectObject;

                    if (firstTime)
                        firstTime = false;
                    else
                        pathString = pathString + "/";

                    //String pathTemp = pathObject.getString("path");
                    pathString = pathString + path;
                }
            }
        }

        return pathString;
    }

    static public String createScriptName (JSONObject jsonObject) {

        String scriptName = "";

        if (jsonObject.has("path")) {
            JSONArray pathArray =   jsonObject.getJSONArray("path");

            if (pathArray != null) {
                boolean firstTime = true;
                for (Object pathObjectObject : pathArray) {
                    scriptName = (String) pathObjectObject;

                    return scriptName;
                }
            }
        }

        return scriptName;
    }

    static public String changeYesNo (String value) {
        if (value == null);
        else if (value.toLowerCase().equals("yes"))
            return "true";

        return "false";
    }
}
