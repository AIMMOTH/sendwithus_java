package com.sendwithus;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sendwithus.exception.SendWithUsException;
import com.sendwithus.model.DeactivatedDrips;
import com.sendwithus.model.Email;
import com.sendwithus.model.RenderedTemplate;
import com.sendwithus.model.SendReceipt;

/**
 * SendWithUs API interface.
 * 
 * Reference: https://github.com/sendwithus/sendwithus_java
 */
public class SendWithUs {
    public static final String API_PROTO = "https";
    public static final String API_HOST = "api.sendwithus.com";
    public static final String API_PORT = "443";
    public static final String API_VERSION = "1";
    public static final String CLIENT_VERSION = "1.0.7";
    public static final String CLIENT_LANG = "java";
    public static final String SWU_API_HEADER = "X-SWU-API-KEY";
    public static final String SWU_CLIENT_HEADER = "X-SWU-API-CLIENT";

    private String apiKey;

    public SendWithUs(String apiKey) {
        this.apiKey = apiKey;
    }

    private static String getURLEndpoint(String resourceName) {
        return String.format("%s://%s:%s/api/v%s/%s", SendWithUs.API_PROTO,
                SendWithUs.API_HOST, SendWithUs.API_PORT,
                SendWithUs.API_VERSION, resourceName);
    }

    private static javax.net.ssl.HttpsURLConnection createConnection(
            String url, String apiKey, String method, Map<String, Object> params)
            throws IOException {

        URL connectionURL = new URL(url);
        javax.net.ssl.HttpsURLConnection connection = (javax.net.ssl.HttpsURLConnection) connectionURL
                .openConnection();
        connection.setConnectTimeout(30000); // 30 seconds
        connection.setReadTimeout(60000); // 60 seconds
        connection.setUseCaches(false);

        for (Map.Entry<String, String> header : getHeaders(apiKey).entrySet()) {
            connection.setRequestProperty(header.getKey(), header.getValue());
        }

        connection.setRequestMethod(method);

        if (method == "POST") {
            connection.setDoOutput(true); // Note: this implicitly sets method
                                          // to POST

            Gson gson = new GsonBuilder().create();
            String jsonParams = gson.toJson(params);

            OutputStream output = null;
            try {
                output = connection.getOutputStream();
                output.write(jsonParams.getBytes("UTF-8"));
            } finally {
                if (output != null) {
                    output.close();
                }
            }
        }

        return connection;
    }

    private static Map<String, String> getHeaders(String apiKey) {
        Map<String, String> headers = new HashMap<String, String>();
        String clientStub = String.format("%s-%s", SendWithUs.CLIENT_LANG,
                SendWithUs.CLIENT_VERSION);

        headers.put("Accept", "text/plain");
        headers.put("Content-Type", "application/json;charset=UTF-8");
        headers.put(SendWithUs.SWU_API_HEADER, apiKey);
        headers.put(SendWithUs.SWU_CLIENT_HEADER, clientStub);

        return headers;
    }

    private static String getResponseBody(
            javax.net.ssl.HttpsURLConnection connection) throws IOException {

        int responseCode = connection.getResponseCode();
        InputStream responseStream = null;

        if (responseCode == 200) {
            responseStream = connection.getInputStream();
        } else {
            responseStream = connection.getErrorStream();
        }

        Scanner responseScanner = new Scanner(responseStream, "UTF-8");

        String responseBody = responseScanner.useDelimiter("\\A").next();

        responseScanner.close();
        responseStream.close();

        return responseBody;
    }

    private static String makeURLRequest(String url, String apiKey,
            String method, Map<String, Object> params)
            throws SendWithUsException {
        javax.net.ssl.HttpsURLConnection connection = null;
        try {
            connection = createConnection(url, apiKey, method, params);
        } catch (IOException e) {
            throw new SendWithUsException("Connection error");
        }

        try {
            int responseCode = connection.getResponseCode();

            if (responseCode < 200 || responseCode >= 300) {
                switch (responseCode) {
                case 400:
                    throw new SendWithUsException("Bad request: "
                            + getResponseBody(connection));
                case 403:
                    throw new SendWithUsException("Authentication error");
                case 404:
                    throw new SendWithUsException("Resource not found");
                default:
                    throw new SendWithUsException(String.format(
                            "Unknown error %d, contact api@sendwithus.com",
                            responseCode));
                }
            }
        } catch (IOException e) {
            throw new SendWithUsException("Caught IOException");
        }

        String response = "";
        try {
            response = getResponseBody(connection);
        } catch (IOException e) {
            throw new SendWithUsException("Caught IOException in response");
        }

        return response;
    }

    /**
     * PUBLIC METHODS
     */

    /**
     * Fetches all available Email templates.
     * 
     * @return Array of Email IDs and names
     * @throws SendWithUsException
     */
    public Email[] emails() throws SendWithUsException {
        String url = getURLEndpoint("emails");

        String response = makeURLRequest(url, this.apiKey, "GET", null);

        Gson gson = new Gson();
        return gson.fromJson(response, Email[].class);
    }

    /**
     * Sends an Email. Represents the minimum required arguments for sending an
     * Email.
     * 
     * @param emailId
     *            The Email template's ID
     * @param recipient
     *            Map defining the Recipient
     * @param emailData
     *            Map defining the Email's variable substitutions
     * @return The receipt ID
     * @throws SendWithUsException
     */
    public SendReceipt send(String emailId, Map<String, Object> recipient,
            Map<String, Object> emailData) throws SendWithUsException {
        return this.send(emailId, recipient, null, emailData);
    }

    /**
     * Sends an Email. Includes Sender as a parameter.
     * 
     * @param emailId
     *            The Email template's ID
     * @param recipient
     *            Map defining the Recipient
     * @param sender
     *            Map defining the Sender
     * @param emailData
     *            Map defining the Email's variable substitutions
     * @return The receipt ID
     * @throws SendWithUsException
     */
    public SendReceipt send(String emailId, Map<String, Object> recipient,
            Map<String, Object> sender, Map<String, Object> emailData)
            throws SendWithUsException {
        return this.send(emailId, recipient, sender, emailData, null);
    }

    /**
     * Sends an Email. Includes CC Recipients as a parameter.
     * 
     * @param emailId
     *            The Email template's ID
     * @param recipient
     *            Map defining the Recipient
     * @param sender
     *            Map defining the Sender
     * @param emailData
     *            Map defining the Email's variable substitutions
     * @param cc
     *            Array of maps defining CC recipients
     * @return The receipt ID
     * @throws SendWithUsException
     */
    public SendReceipt send(String emailId, Map<String, Object> recipient,
            Map<String, Object> sender, Map<String, Object> emailData,
            Map<String, Object>[] cc) throws SendWithUsException {
        return this.send(emailId, recipient, sender, emailData, cc, null);
    }

    /**
     * Sends an Email. Includes BCC Recipients as a parameter.
     * 
     * @param emailId
     *            The Email template's ID
     * @param recipient
     *            Map defining the Recipient
     * @param sender
     *            Map defining the Sender
     * @param emailData
     *            Map defining the Email's variable substitutions
     * @param cc
     *            Array of maps defining CC recipients
     * @param bcc
     *            Array of maps defining BCC recipients
     * @return The receipt ID
     * @throws SendWithUsException
     */
    public SendReceipt send(String emailId, Map<String, Object> recipient,
            Map<String, Object> sender, Map<String, Object> emailData,
            Map<String, Object>[] cc, Map<String, Object>[] bcc)
            throws SendWithUsException {
        return this.send(emailId, recipient, sender, emailData, cc, bcc, null);
    }

    /**
     * Sends an Email. Includes attachment filepaths as a parameter.
     * 
     * @param emailId
     *            The Email template's ID
     * @param recipient
     *            Map defining the Recipient
     * @param sender
     *            Map defining the Sender
     * @param emailData
     *            Map defining the Email's variable substitutions
     * @param cc
     *            Array of maps defining CC recipients
     * @param bcc
     *            Array of maps defining BCC recipients
     * @param attachment_paths
     *            Array of filepaths for attachments
     * @return The receipt ID
     * @throws SendWithUsException
     */
    public SendReceipt send(String emailId, Map<String, Object> recipient,
            Map<String, Object> sender, Map<String, Object> emailData,
            Map<String, Object>[] cc, Map<String, Object>[] bcc,
            String[] attachment_paths) throws SendWithUsException {
        return this.send(emailId, recipient, sender, emailData, cc, bcc,
                attachment_paths, null);
    }

    /**
     * Sends an Email. Includes ESP account as a parameter.
     * 
     * @param emailId
     *            The Email template's ID
     * @param recipient
     *            Map defining the Recipient
     * @param sender
     *            Map defining the Sender
     * @param emailData
     *            Map defining the Email's variable substitutions
     * @param cc
     *            Array of maps defining CC recipients
     * @param bcc
     *            Array of maps defining BCC recipients
     * @param attachment_paths
     *            Array of filepaths for attachments
     * @param espAccount
     *            ID specifying the ESP account to use
     * @return The receipt ID
     * @throws SendWithUsException
     */
    public SendReceipt send(String emailId, Map<String, Object> recipient,
            Map<String, Object> sender, Map<String, Object> emailData,
            Map<String, Object>[] cc, Map<String, Object>[] bcc,
            String[] attachment_paths, String espAccount)
            throws SendWithUsException {
        SendWithUsSendRequest request = new SendWithUsSendRequest();
        request.setEmailId(emailId).setRecipient(recipient)
                .setEmailData(emailData).setSender(sender).setCcRecipients(cc)
                .setBccRecipients(bcc).setAttachmentPaths(attachment_paths)
                .setEspAccount(espAccount);

        return this.send(request);
    }

    /**
     * Sends an Email defined by a request object.
     * 
     * @param request
     *            The "send" request parameters
     * @return The receipt ID
     * @throws SendWithUsException
     */
    public SendReceipt send(SendWithUsSendRequest request)
            throws SendWithUsException {
        Map<String, Object> sendParams = request.asMap();

        String url = getURLEndpoint("send");

        String response = makeURLRequest(url, this.apiKey, "POST", sendParams);

        Gson gson = new Gson();
        return gson.fromJson(response, SendReceipt.class);
    }

    /**
     * Renders a template with the given data.
     * 
     * @param templateId
     *            The Email template ID
     * @param templateData
     *            The template data
     * @return The rendered template
     * @throws SendWithUsException
     */
    public RenderedTemplate render(String templateId,
            Map<String, Object> templateData) throws SendWithUsException {
        Map<String, Object> sendParams = new HashMap<String, Object>();
        sendParams.put("template_id", templateId);
        sendParams.put("template_data", templateData);

        String url = getURLEndpoint("render");

        String response = makeURLRequest(url, this.apiKey, "POST", sendParams);

        Gson gson = new Gson();
        return gson.fromJson(response, RenderedTemplate.class);
    }

    /**
     * Deactivate drip campaigns for a customer.
     * 
     * @param customerEmailAddress
     *            The customer's Email address
     * @return Response details
     * @throws SendWithUsException
     */
    public DeactivatedDrips deactivateDrips(String customerEmailAddress)
            throws SendWithUsException {
        Map<String, Object> sendParams = new HashMap<String, Object>();
        sendParams.put("email_address", customerEmailAddress);

        String url = getURLEndpoint("drips/deactivate");

        String response = makeURLRequest(url, this.apiKey, "POST", sendParams);

        Gson gson = new Gson();
        return gson.fromJson(response, DeactivatedDrips.class);
    }
}
