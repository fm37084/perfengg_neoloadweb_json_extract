package neoload_api.runTest;

import org.json.JSONArray;
import org.json.JSONObject;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import java.time.Instant;
import java.time.OffsetTime;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static neoload_api.runTest.Utilities.*;

public class Main {

    public static void main(String[] args) throws Exception {

        String neoloadWebUrl = "neoloadweb-api.prftst-prod.kpsazc.dgtl.kroger.com";
        String accountToken = "D86O9IjIq8d6MccXcWoQ0Af8";
        String fileSuffix = "240202.json";
        String pullStartTimeString = "2024-02-02T00:00:00Z";
        String pullEndTimeString = "2024-02-03T00:00:00Z";

        SSLContext ctx = SSLContext.getInstance("TLS");
        ///if (ignoreInvalidCertificate){
        ctx.init(null, new TrustManager[] { new InvalidCertificateTrustManager() }, null);
        //}
        SSLContext.setDefault(ctx);

        System.out.println("v1.1");

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-server":
                    neoloadWebUrl = args[++i];
                    break;
                case "-token":
                    accountToken = args[++i];
                    break;
                case "-filesuffix":
                    fileSuffix = args[++i];
                    break;
                case "-pullstarttime":
                    pullStartTimeString = args[++i];
                    break;
                case "-pullendtime":
                    pullEndTimeString = args[++i];
                    break;
                case "-h":
                    System.out.println("v1.1 -server <servername:port> -token <neoload token> -filesuffix <file suffix of the output files> -pulltime < time to start pull>");
                    return;
            }
        }

        long pullStartTIme = convertStringDateToMillis_1 (pullStartTimeString);
        long pullEndTIme = convertStringDateToMillis_1 (pullEndTimeString);

        Map<String, Map<String, Object>> fileMap = Utilities.createFilesMap(fileSuffix);

        JSONArray workspaceDefinitionList = getWorkspaceArray (neoloadWebUrl, accountToken);

        doit(neoloadWebUrl, accountToken, fileMap, workspaceDefinitionList, pullStartTIme, pullEndTIme);

        Utilities.closeFileMap ( fileMap);

    }

    private static JSONArray getWorkspaceArray (String neoloadWebUrl, String token) {

        JSONArray workspaceDefinitionList;
        System.out.println("--- Start getWorkspaceArray");
        try {
            workspaceDefinitionList = makeRestApiCall (neoloadWebUrl, token, "/v3/workspaces?limit=200");
        } catch (Exception ex) {
            System.out.println("Error - could not obtain workspacelist  " + ex);
            return null;
        }

        System.out.println("--- end getWorkspaceArray");

        return workspaceDefinitionList;
    }

    public static void doit(String neoloadWebUrl, String accountToken, Map<String, Map<String, Object>> fileMap,  JSONArray workspaceDefinitionList, long pullStartTime, long pullEndTime) {

        int count = 0;

        for (Object workspaceDefinitionObject : workspaceDefinitionList) {


            //if (count > 10 )
            //    break;

            JSONObject workspaceDefinition = (JSONObject) workspaceDefinitionObject;
            String workspaceId = workspaceDefinition.getString("id");
            String workspaceName = workspaceDefinition.getString("name");

            System.out.println("*********** Start processing workspace  [" + workspaceName + "]");

            if (workspaceName.equals("H&W")) {
                System.out.println("-------- Throw out workspace  [" + workspaceName + "]");
                continue;
            }

            if (workspaceName.equals("Loyalty")) {
                System.out.println("-------- Throw out workspace  [" + workspaceName + "]");
                continue;
            }

            System.out.println("-------- Start call to get  test results for   [" + workspaceName + "]");

            JSONArray testResultArray = getTestResultDefinitionArray (neoloadWebUrl, accountToken, workspaceId);

            System.out.println("-------- End call to get  test results for   [" + workspaceName + "]");

            if (testResultArray == null) {
                System.out.println("-------- No test results for   [" + workspaceName + "]. go to next workspace");
                continue;
            } else {
                System.out.println("-------- Results for   [" + workspaceName + "]  number of results returned [" + testResultArray.length() + "]");
            }

            for (Object  testResultDefinitionObject : testResultArray) {

                JSONObject testResultDefinition = (JSONObject) testResultDefinitionObject;

                long  testStartTimestamp = testResultDefinition.getLong("startDate");
                String  testName = testResultDefinition.getString("name");


                long testStartTimestampTemp = testStartTimestamp - (5*60*60 * 1000);

                String testStartTimestampString = Instant.ofEpochMilli(testStartTimestampTemp).toString();
                String xxxx = Instant.ofEpochMilli(testStartTimestamp).toString();

                if (testStartTimestamp < pullStartTime) {
                    System.out.println("----- Throw out test  [" + testName + "]. because time is before startTime [" + testStartTimestampString + "]");
                    continue;
                }

                if (testStartTimestamp > pullEndTime) {
                    System.out.println("----- Throw out test  [" + testName + "]. because time is greater than the endTime [" + testStartTimestampString + "]");
                    continue;
                }

                String resultId = testResultDefinition.getString("id");
                UUID uuid = UUID.randomUUID();
                String extractionToken = uuid.toString();

                System.out.println("----- Start processing   [" + testName + "]. extractionToken [" + extractionToken + "]");

                try {

                    System.out.println("---- Start process_test_results logic [" + testName + "].");

                    boolean result = process_test_results(neoloadWebUrl, accountToken, fileMap, workspaceId, workspaceName, resultId,  testResultDefinition, extractionToken);

                    System.out.println("---- End process_test_results logic [" + testName + "].");

                    if (!result) {
                        System.out.println("---- Problem process_test_results logic [" + testName + "]. go to next test");
                        continue;
                    }

                    count= count +1;

                    //if (count> 10)
                    //    break;

                    String timestamp = testResultDefinition.getString("timestamp");

                    System.out.println("---- Start process_elements logic [" + testName + "].");
                    process_elements(neoloadWebUrl, accountToken, fileMap, workspaceId, resultId, extractionToken, timestamp);
                    System.out.println("---- Stop process_elements logic [" + testName + "].");

                    System.out.println("---- Start process_monitors logic [" + testName + "].");

                    process_monitors(neoloadWebUrl, accountToken, fileMap, workspaceId, resultId, extractionToken, timestamp, testStartTimestamp);

                    System.out.println("---- Stop process_monitors logic [" + testName + "].");

                } catch (Exception ex){
                    System.out.println("Error making call to rest api for list of elements. " + ex);
                    return;
                }

                System.out.println("----- End   [" + testName + "]. extractionToken [" + extractionToken + "]");
            }

            System.out.println("********** Finish processing workspace  [" + workspaceName + "]");
        }
    }

    private static JSONArray getTestResultDefinitionArray( String neoloadWebUrl, String token, String workspaceId) {

        JSONArray testResultsArray = null;

        try {
            // arrayOfTestResultDefinition = resultsApi.getTestResultList(workspaceDefinition.getId(), "TERMINATED", null, null, 1000, 0, "-startDate", null, true);
           testResultsArray = makeRestApiCall (neoloadWebUrl, token, "/v3/workspaces/" + workspaceId + "/test-results?status=TERMINATED&limit=200&sort=-startDate&pretty=false");
        } catch (Exception ex) {
           System.out.println("Error - could not obtain  getTestResultsArray  " + ex);
           return null;
        }

        System.out.println("end getTestResultsArray");

        return testResultsArray;
    }

    public static boolean process_test_results(String neoloadWebUrl, String accountToken, Map<String, Map<String, Object>> fileMap, String workspaceId, String workspaceName, String resultId,  JSONObject testResultDefinition, String extractionToken ) throws Exception {

        boolean result = true;

        JSONArray  testResultStatisticsArray = null;
        try {
            //testResultStatistics = resultsApi.getTestResultStatistics(workspaceDefinition.getId(), testResultDefinition.getId());
            testResultStatisticsArray = makeRestApiCall (neoloadWebUrl, accountToken, "/v3/workspaces/" + workspaceId + "/test-results/"+resultId+"/statistics");

        } catch (Exception ex) {
            System.out.println("Error making call to rest api for test results. " + ex);
            return false;
        }

        long startDateLong = testResultDefinition.getLong("startDate");
        Instant startTimeInstant = Instant.ofEpochMilli(startDateLong);
        testResultDefinition.put("timestamp", startTimeInstant.toString());


        testResultDefinition.remove("startDate");
        testResultDefinition.put("extractionStartTime", startDateLong);

        long endTime = changeLabelLong(testResultDefinition, "endDate", "endTime");
        String resultName = changeLabelString (testResultDefinition, "name", "resultName");

        testResultDefinition.put("extractionToken", extractionToken);
        testResultDefinition.put("workspace", workspaceName);

        String application = Utilities.getDataInResultName (resultName, 0);
        testResultDefinition.put("application", application);

        String testType = Utilities.getDataInResultName (resultName, 1);
        testResultDefinition.put("testType", testType);

        String environment = Utilities.getDataInResultName (resultName, 2);
        testResultDefinition.put("environment", environment);

        if (testResultStatisticsArray != null && testResultStatisticsArray.length() > 0) {
            JSONObject testResultStatistics = testResultStatisticsArray.getJSONObject(0);

            if (testResultStatistics.length() > 0) {
                for (String key : JSONObject.getNames(testResultStatistics)) {
                    Object value = testResultStatistics.get(key);
                    testResultDefinition.put(key, value);
                }

                int totalTransactionCountSuccess = testResultDefinition.getInt("totalTransactionCountSuccess");
                int totalTransactionCountFailure = testResultDefinition.getInt("totalTransactionCountFailure");

                if (totalTransactionCountSuccess + totalTransactionCountFailure > 750000)
                    return false;
            }else {
                System.out.println("--- Statisitics not found for test - empty response");
                result = false;
                return result;
            }
        } else {
            System.out.println("--- Statisitics not found for test at all");
            result = false;
            return result;
        }

        String testResultString = testResultDefinition.toString();

        System.out.println("--- Write test results to file");

        Utilities.writeLogs ( Utilities.TEST, fileMap,  testResultString);

        return result;
    }

    public static void process_elements(String neoloadWebUrl, String accountToken, Map<String, Map<String, Object>> fileMap, String workspaceId, String resultId, String extractionToken, String timestamp) throws Exception {

        System.out.println("--- Start process_elements for Transaction");

        process_elements(neoloadWebUrl, accountToken, fileMap, workspaceId, resultId, extractionToken, "TRANSACTION", timestamp);

        System.out.println("--- Start process_elements for Request");

        process_elements(neoloadWebUrl, accountToken, fileMap, workspaceId, resultId, extractionToken, "REQUEST", timestamp);

        System.out.println("--- Start process_elements for Page");

        process_elements(neoloadWebUrl, accountToken, fileMap, workspaceId, resultId, extractionToken, "PAGE", timestamp);

        System.out.println("--- Finish process_elements");
    }

    public static void process_elements(String neoloadWebUrl, String accountToken, Map<String, Map<String, Object>> fileMap, String workspaceId, String resultId, String extractionToken, String category, String timestamp) throws Exception {

        JSONArray elementArray = null;

        try {
            elementArray = makeRestApiCall(neoloadWebUrl, accountToken, "/v3/workspaces/"+workspaceId +"/test-results/" + resultId + "/elements?category=" + category);
        } catch (Exception ex) {
            System.out.println("Error making call to rest api for test events. " + ex);
            return;
        }

        if (elementArray != null) {
            for (int j = 0; j < elementArray.length(); j++) {
                JSONObject jsonObject = elementArray.getJSONObject(j);
                String path = createPath (jsonObject);
                String scriptName = createScriptName (jsonObject);

                UUID uuid = UUID.randomUUID();
                String elementToken = uuid.toString();

                process_elements_statCategory(neoloadWebUrl, accountToken, fileMap, workspaceId, resultId, path, scriptName, jsonObject,  category, extractionToken, elementToken, "values", timestamp);

                if (category.equals("TRANSACTION")) {
                     if (jsonObject.getString("name").equals("<all transactions>"));
                    else
                        process_elements_statCategory_raw(neoloadWebUrl, accountToken, fileMap, workspaceId, resultId, jsonObject.getString("id"), jsonObject.getString("name"), extractionToken, elementToken);
                }
            }
        }
    }

    public static void process_elements_statCategory(String neoloadWebUrl, String accountToken, Map<String, Map<String, Object>> fileMap, String workspaceId, String resultId, String path, String scriptName, JSONObject elementJsonObject, String category, String extractionToken, String elementToken, String statCategory, String timestamp) throws Exception {
        JSONArray jsonArray = null;

        String elementId = elementJsonObject.getString("id");
        String elementName = elementJsonObject.getString ("name");

        try {
            jsonArray = makeRestApiCall(neoloadWebUrl, accountToken, "/v3/workspaces/"+workspaceId+"/test-results/" + resultId + "/elements/" + elementId + "/" + statCategory);
        } catch (Exception ex) {
            System.out.println("-Error process_elements_statCategory. elementName [" + elementName +"]. category  ["+category +"] .statcategory["+statCategory+"]- Error making call to rest api for test element values. " + ex);
            return;
        }

        if (jsonArray != null && jsonArray.length() > 0) {
            JSONObject jsonObject = jsonArray.getJSONObject(0);

            jsonObject.put("extractionToken", extractionToken);
            jsonObject.put("elementToken", elementToken);
            jsonObject.put("scriptName", scriptName);
            jsonObject.put("userPath", path);
            jsonObject.put("category", category);
            jsonObject.put("elementName", elementName);
            jsonObject.put("elementId", elementId);
            jsonObject.put("timestamp",timestamp);

            String outputString = jsonObject.toString();

            Utilities.writeLogs ( Utilities.ELEMENT, fileMap,  outputString);
        }
     }

    public static void process_elements_statCategory_raw(String neoloadWebUrl, String accountToken, Map<String, Map<String, Object>> fileMap, String workspaceId, String resultId, String elementId, String elementName, String extractionToken, String elementToken) throws Exception {
        JSONArray elementRawArray = null;

        try {
            elementRawArray = makeRestApiCall(neoloadWebUrl, accountToken, "/v3/workspaces/"+workspaceId+"/test-results/" + resultId + "/elements/" + elementId + "/raw?format=JSON");
        } catch (Exception ex) {
            System.out.println("-Error process_elements_statCategory. elementName [" + elementName +"] .statcategory[raw]- Error making call to rest api for test element values. " + ex);
            return;
        }

        if (elementRawArray != null && elementRawArray.length() > 0) {

            for (Object elementRawObject : elementRawArray) {
                JSONObject elementRaw = (JSONObject) elementRawObject;
                elementRaw.put("extractionToken", extractionToken);
                elementRaw.put("elementToken", elementToken);

                changeLabelLong(elementRaw, "Response time", "response");
                changeLabelString(elementRaw, "Success", "success");
                changeLabelString(elementRaw, "Virtual User ID", "userId");
                changeLabelString(elementRaw, "Population", "population");
                changeLabelString(elementRaw, "Zone", "zone");
                changeLabelLong(elementRaw, "Elapsed", "offset");

                elementRaw.remove("Parent");
                elementRaw.remove("Element");
                elementRaw.remove("UserPath");

                String timeString = changeLabelString(elementRaw, "Time", "timestamp");

                long time = Utilities.convertStringDateToMillis_2(timeString);
                elementRaw.put("time", time);

                String outputString = elementRaw.toString();

                Utilities.writeLogs ( ELEMENT_RAW, fileMap,  outputString);
            }

        }
    }
    public static void process_monitors(String neoloadWebUrl, String accountToken, Map<String, Map<String, Object>> fileMap, String workspaceId, String resultId, String extractionToken, String timestamp, long startTime) throws Exception {
        JSONArray jsonArray = null;

        try {
            jsonArray = makeRestApiCall(neoloadWebUrl, accountToken, "/v3/workspaces/"+workspaceId+"/test-results/" + resultId + "/monitors");
        } catch (Exception ex) {
            System.out.println("Error making call to rest api for test monitors. " + ex);
            return;
        }

        if (jsonArray != null) {

            for (int j = 0; j < jsonArray.length(); j++) {
                JSONObject jsonObject = jsonArray.getJSONObject(j);
                String path = createPath (jsonObject);

                process_monitor_values(neoloadWebUrl, accountToken, fileMap, workspaceId, resultId, jsonObject.getString("id"), jsonObject.getString("name"), path, extractionToken, timestamp);
                process_monitor_points(neoloadWebUrl, accountToken, fileMap, workspaceId, resultId, jsonObject.getString("id"),  extractionToken, startTime);
            }
        }
    }

    public static void process_monitor_values(String neoloadWebUrl, String accountToken, Map<String, Map<String, Object>> fileMap, String workspaceId, String resultId, String monitorId, String monitorName, String path,  String extractionToken, String timestamp) throws Exception {
        JSONArray jsonArray = null;

        try {
            jsonArray = makeRestApiCall(neoloadWebUrl, accountToken, "/v3/workspaces/"+workspaceId+"/test-results/" + resultId + "/monitors/" + monitorId + "/values");
        } catch (Exception ex) {
            System.out.println("Error making call to rest api for test monitor values. " + ex);
            return;
        }

        if (jsonArray != null ) {

            for (Object jsonObjectObject : jsonArray) {
                JSONObject jsonObject = (JSONObject) jsonObjectObject;
                jsonObject.put("extractionToken", extractionToken);
                jsonObject.put("userPath", path);
                jsonObject.put("monitorId", monitorId);
                jsonObject.put("name", monitorName);
                jsonObject.put("timestamp", timestamp);

                String outputString = jsonObject.toString();
                Utilities.writeLogs ( MONITOR_VALUES, fileMap,  outputString);
            }
        }

        //if (jsonArray != null)
        //     printOutCsv(csvMap, String.valueOf(labelPrefix) + "monitor" + statCategory, jsonArray, "apiType,path,elementId,elementName", "monitor" + statCategory + "," + path + "," + elementId + "," + elementName);
    }

    public static void process_monitor_points(String neoloadWebUrl, String accountToken, Map<String, Map<String,Object>> fileMap, String workspaceId, String resultId, String monitorId, String extractionToken, long startTime) throws Exception {
        JSONArray jsonArray = null;

        try {
            jsonArray = makeRestApiCall(neoloadWebUrl, accountToken, "/v3/workspaces/"+workspaceId+"/test-results/" + resultId + "/monitors/" + monitorId + "/points");
        } catch (Exception ex) {
            System.out.println("Error making call to rest api for test monitor values. " + ex);
            return;
        }

        if (jsonArray != null ) {

            for (Object jsonObjectObject : jsonArray) {
                JSONObject jsonObject = (JSONObject) jsonObjectObject;
                jsonObject.put("extractionToken", extractionToken);
                jsonObject.put("monitorId", monitorId);

                long from = jsonObject.getLong("from");

                long fromTime = startTime + from;
                Instant fromTimeInstant = Instant.ofEpochMilli(fromTime);
                String fromTimeInstantString = fromTimeInstant.toString();

                jsonObject.put("fromTime", fromTime);
                jsonObject.put("fromTimestamp", fromTimeInstantString);

                String outputString = jsonObject.toString();

                Utilities.writeLogs ( MONITOR_POINTS, fileMap,  outputString);
            }
        }

    }


}
