package com.app.servlet;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.servlet.GenericServlet;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.support.SpringBootServletInitializer;
import org.springframework.context.annotation.Bean;

import com.google.gson.Gson;

@SpringBootApplication
public class CallprocessApplication extends SpringBootServletInitializer {

	private static String lastPhNo;
	private static HashMap<String, Map<String, String>> uuidMap = new HashMap<>();
	private static final long serialVersionUID = 1L;
	private static String contentType = "application/json";
	private static String characterEncoding = "UTF-8";
	private static String inputEventUrl = "";
	private static String transferUrl = "";
	Properties prop = new Properties();
	InputStream input = null;

	@SuppressWarnings("serial")
	@Bean
	public Servlet dispatcherServlet() {
		return new GenericServlet() {

			protected void doPost(ServletRequest request, ServletResponse response)
					throws ServletException, IOException {
				System.out.println("Called");
				service(request, response);
				// log.info("doPost was called!!");
			}

			@Override
			public void service(ServletRequest request, ServletResponse response) throws ServletException, IOException {
				String filename = "config.properties";
				ClassLoader classLoader = getClass().getClassLoader();
				String path = getServletContext().getRealPath("config.properties");

				input = new FileInputStream(path);
				if (input == null) {
					System.out.println("Sorry, unable to find " + filename);
					return;
				}

				// load a properties file from class path, inside static method
				prop.load(input);
				inputEventUrl = prop.getProperty("inputEventUrl");
				transferUrl = prop.getProperty("transferUrl");
				JSONArray actionArray = new JSONArray();
				JSONArray endPointArray = new JSONArray();
				String callType = request.getParameter("call_type");
				Map actionParams = null;

				if (callType != null && callType.equalsIgnoreCase("event")) {
					JSONObject reqObj = getRequestJsonObject(request);
					System.out.println(reqObj.toString());
					String fromPhone = (String) reqObj.get("from");
					if (fromPhone != null) {
						setLastPhNo(fromPhone);
					}
					if (reqObj != null) {
						String uuid = (String) reqObj.get("uuid");
						String status = (String) reqObj.get("status");
						if (status.equals("answered")) {
							Map eventvalue = new HashMap<String, String>();
							eventvalue.put("from", (String) reqObj.get("from"));
							eventvalue.put("to", (String) reqObj.get("to"));
							eventvalue.put("uuid", (String) reqObj.get("uuid"));
							eventvalue.put("conversation_uuid", (String) reqObj.get("conversation_uuid"));
							eventvalue.put("status", (String) reqObj.get("status"));
							eventvalue.put("callTrack", "0");
							eventvalue.put("dtmfvalue", "");
							uuidMap.put((String) reqObj.get("uuid"), eventvalue);
						} else if (status.equals("completed")) {
							uuidMap.remove(uuid);
						}
					}
				} else if (callType != null && callType.equalsIgnoreCase("Answer")) {
					actionParams = new HashMap<String, String>();
					actionParams.put("action", "talk");
					actionParams.put("text", "Hello, Welcome to Novopath");
					actionArray.add(actionParams);

					actionParams = new HashMap<String, String>();
					actionParams.put("action", "talk");
					actionParams.put("text",
							"press one for connect with support , press two for connect with Client services , press three for connect with Billing");
					actionParams.put("bargeIn", true);
					actionArray.add(actionParams);

					actionParams = new HashMap<String, String>();
					actionParams.put("action", "talk");
					actionParams.put("text", "please choose any one of the choices");
					actionArray.add(actionParams);

					actionParams = new HashMap<String, String>();
					actionParams.put("action", "input");
					endPointArray = new JSONArray();
					endPointArray.add(inputEventUrl);
					actionParams.put("timeOut", "5");
					actionParams.put("maxDigits", "1");
					actionParams.put("eventUrl", endPointArray);
					actionArray.add(actionParams);

					response.setContentType(contentType);
					response.setCharacterEncoding(characterEncoding);
					response.getOutputStream().write(new Gson().toJson(actionArray).getBytes());
				} else if (callType != null && callType.equalsIgnoreCase("input")) {
					JSONObject reqObj = getRequestJsonObject(request);
					String convId = (String) reqObj.get("conversation_uuid");
					String dtmf = (String) reqObj.get("dtmf");

					int callRepeat = 0;

					for (Map.Entry entry : uuidMap.entrySet()) {
						Map<String, String> tempMap = uuidMap.get(entry.getKey());
						String conv = tempMap.get("conversation_uuid");
						if (conv.equalsIgnoreCase(convId)) {
							int s = Integer.parseInt(tempMap.get("callTrack"));
							tempMap.put("callTrack", String.valueOf(s + 1));
							callRepeat = Integer.parseInt(tempMap.get("callTrack"));
						}
					}

					if (callRepeat == 3 && dtmf.equals("1") || dtmf.equals("2") || dtmf.equals("3")) {
						try {
							System.out.println("call transfer start======>");
							String callUrl = transferUrl + "=" + getLastPhNo() + "&promptid=" + dtmf;
							URI lUri = new URI(callUrl);
							HttpClient lHttpClient = new DefaultHttpClient();
							HttpGet lHttppost = new HttpGet();

							lHttppost.setURI(lUri);
							HttpResponse lHttpResponse = lHttpClient.execute(lHttppost);
							int responseCode = lHttpResponse.getStatusLine().getStatusCode();
							System.out.println("responseCode========>" + responseCode);
							InputStream inputStream = lHttpResponse.getEntity().getContent();
							String stringResponse = getStringFromInputStream(inputStream);
							JSONParser parser = new JSONParser();
							JSONObject json = (JSONObject) parser.parse(stringResponse);
							String destinationPhNo = (String) json.get("destination");

							actionParams = new HashMap<String, String>();
							actionParams.put("action", "connect");
							actionParams.put("from", getLastPhNo());
							endPointArray = new JSONArray();
							Map actionParams1 = new HashMap<String, String>();
							actionParams1.put("number", "+1" + destinationPhNo);
							actionParams1.put("type", "phone");
							endPointArray.add(actionParams1);
							actionParams.put("endpoint", endPointArray);
							actionArray.add(actionParams);

							response.setContentType(contentType);
							response.setCharacterEncoding(characterEncoding);
							response.getOutputStream().write(new Gson().toJson(actionArray).getBytes());
						} catch (Exception e) {
							System.out.println("exception is===> callUrl:==>" + e.getMessage());

						}
					} else if (callRepeat < 3) {

						if (dtmf.equals("1") || dtmf.equals("2") || dtmf.equals("3")) {
							System.out.println("call transfer start======>");
							try {
								String callUrl = transferUrl + "=" + getLastPhNo() + "&promptid=" + dtmf;
								URI lUri = new URI(callUrl);
								HttpClient lHttpClient = new DefaultHttpClient();
								HttpGet lHttppost = new HttpGet();

								lHttppost.setURI(lUri);
								HttpResponse lHttpResponse = lHttpClient.execute(lHttppost);
								int responseCode = lHttpResponse.getStatusLine().getStatusCode();
								System.out.println("responseCode========>" + responseCode);

								InputStream inputStream = lHttpResponse.getEntity().getContent();
								JSONObject StringResponse = getRequestJsonObject(request);
								String destinationPhNo = (String) StringResponse.get("destination");

								actionParams = new HashMap<String, String>();
								actionParams.put("action", "connect");
								actionParams.put("from", getLastPhNo());
								endPointArray = new JSONArray();
								Map actionParams1 = new HashMap<String, String>();
								actionParams1.put("number", "+1" + destinationPhNo);
								actionParams1.put("type", "phone");
								endPointArray.add(actionParams1);
								actionParams.put("endpoint", endPointArray);
								actionArray.add(actionParams);

								response.setContentType(contentType);
								response.setCharacterEncoding(characterEncoding);
								response.getOutputStream().write(new Gson().toJson(actionArray).getBytes());
							} catch (Exception e) {
								System.out.println("exception is===>" + e.getMessage());

							}

						} else if (dtmf.equals("")) {
							actionParams = new HashMap<String, String>();
							actionParams.put("action", "talk");
							actionParams.put("text", "Nothing has entered , choose any one of the choices");
							actionArray.add(actionParams);

							actionParams = new HashMap<String, String>();
							actionParams.put("action", "talk");
							actionParams.put("text",
									"press one for connect with support , press two for connect with Client services , press three for connect with Billing");
							actionParams.put("bargeIn", true);
							actionArray.add(actionParams);

							actionParams = new HashMap<String, String>();
							actionParams.put("action", "input");
							endPointArray = new JSONArray();
							endPointArray.add(inputEventUrl);
							actionParams.put("timeOut", "5");
							actionParams.put("maxDigits", "1");
							actionParams.put("eventUrl", endPointArray);
							actionArray.add(actionParams);

							response.setContentType(contentType);
							response.setCharacterEncoding(characterEncoding);
							response.getOutputStream().write(new Gson().toJson(actionArray).getBytes());

						} else {
							actionParams = new HashMap<String, String>();
							actionParams.put("action", "talk");
							actionParams.put("text", "Choose any valid choices");
							actionArray.add(actionParams);

							actionParams = new HashMap<String, String>();
							actionParams.put("action", "talk");
							actionParams.put("text",
									"press one for connect with support , press two for connect with Client services , press three for connect with Billing");
							actionParams.put("bargeIn", true);
							actionArray.add(actionParams);

							actionParams = new HashMap<String, String>();
							actionParams.put("action", "input");
							endPointArray = new JSONArray();
							endPointArray.add(inputEventUrl);
							actionParams.put("timeOut", "5");
							actionParams.put("maxDigits", "1");
							actionParams.put("eventUrl", endPointArray);
							actionArray.add(actionParams);

							response.setContentType(contentType);
							response.setCharacterEncoding(characterEncoding);
							response.getOutputStream().write(new Gson().toJson(actionArray).getBytes());
						}
						response.setContentType(contentType);
						response.setCharacterEncoding(characterEncoding);
						response.getOutputStream().write(new Gson().toJson(actionArray).getBytes());
					} else {
						actionParams = new HashMap<String, String>();
						actionParams.put("action", "talk");
						actionParams.put("text", "Sorry try again later");
						actionArray.add(actionParams);

						response.setContentType(contentType);
						response.setCharacterEncoding(characterEncoding);
						response.getOutputStream().write(new Gson().toJson(actionArray).getBytes());
					}
				} else {
					actionParams = new HashMap<String, String>();
					actionParams.put("action", "talk");
					actionParams.put("text", "Sorry Try again later");
					actionArray.add(actionParams);
					response.setContentType(contentType);
					response.setCharacterEncoding(characterEncoding);
					response.getOutputStream().write(new Gson().toJson(actionArray).getBytes());
				}
			}
		};

	}

	public static JSONObject getRequestJsonObject(ServletRequest request) throws IOException {
		StringBuffer jb = new StringBuffer();
		String line = null;
		Object obj = null;
		JSONParser parser = null;
		BufferedReader br = request.getReader();

		if (br != null) {
			while ((line = br.readLine()) != null) {
				jb.append(line);
			}
			if (jb.length() > 0) {
				parser = new JSONParser();
				try {
					obj = parser.parse(jb.toString());
				} catch (ParseException e) {
					e.printStackTrace();
				}
			}
		}
		JSONObject reqObj = (JSONObject) obj;
		return reqObj;
	}

	// convert InputStream to String
	private static String getStringFromInputStream(InputStream is) {

		BufferedReader br = null;
		StringBuilder sb = new StringBuilder();

		String line;
		try {

			br = new BufferedReader(new InputStreamReader(is));
			while ((line = br.readLine()) != null) {
				sb.append(line);
			}

		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (br != null) {
				try {
					br.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

		return sb.toString();

	}

	public static void main(String[] args) {
		SpringApplication.run(CallprocessApplication.class, args);
	}

	public static String getLastPhNo() {
		return lastPhNo;
	}

	public static void setLastPhNo(String lastPhNo) {
		CallprocessApplication.lastPhNo = lastPhNo;
	}

}
