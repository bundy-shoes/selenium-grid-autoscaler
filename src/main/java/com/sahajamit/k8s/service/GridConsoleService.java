package com.sahajamit.k8s.service;

import com.sahajamit.k8s.domain.GridConsoleStatus;
import com.squareup.okhttp.*;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.TimeZone;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;


@Service
public class GridConsoleService {

    @Value("${gridUrl}")
    private String gridUrl;
     
    @Value("${graphql}")
    private String graphql;
    
    @Value("${gridPath}")
    private String gridPath;
    
    private OkHttpClient httpClient = new OkHttpClient();

    private static final Logger logger = LoggerFactory.getLogger(PodScalingService.class);
    
    @Value("${max_session_timeout}")
    private Long MAX_SESSION_TIMEOUT;

    public Response drain(String nodeUri, String nodeId) throws IOException {
        Response response = null;
        try {
            MediaType JSON = MediaType.parse("application/json");
            RequestBody body = RequestBody.create(JSON, "");
            String gridUrl = String.format("%s/%s/distributor/node/%s/drain", nodeUri, this.gridPath, nodeId);
            Request r = new Request.Builder()
                    .url(gridUrl)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .header("X-REGISTRATION-SECRET", "")
                    .post(body)
                    .build();
            response = httpClient.newCall(r).execute();
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
        return response;
    }

    public Response deleteSession(String nodeUri, String sessionId) throws IOException {
        Response response = null;
        try {
            String gridUrl = String.format("%s/%s/node/session/%s", nodeUri, this.gridPath, sessionId);
            Request r = new Request.Builder()
                    .url(gridUrl)
                    .header("X-REGISTRATION-SECRET", "")
                    .delete()
                    .build();
            response = httpClient.newCall(r).execute();
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
        return response;
    }
    
    public GridConsoleStatus getStatus() throws IOException {
        GridConsoleStatus status = new GridConsoleStatus();
        try {
            JSONObject json = new JSONObject();
            String grapql = "query GetNodes {    nodesInfo {      nodes {        id        uri        status        maxSession        slotCount        sessions {          id          capabilities          startTime          uri          nodeId          nodeUri          sessionDurationMillis          slot {            id            stereotype            lastStarted          }        }        sessionCount        stereotypes        version        osInfo {          arch          name          version        }      }      __typename    }    __typename  }";
            json.put("query", grapql);
            json.put("variables", new JSONObject());
            RequestBody body = RequestBody.create(null, json.toString());
            Request r = new Request.Builder()
                    .url(this.graphql)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .post(body)
                    .build();
            Call call = httpClient.newCall(r);
            Response response = call.execute();
            String jsonString = response.body().string();
            JSONObject jsonObject = new JSONObject(jsonString);
            JSONArray nodes = jsonObject.getJSONObject("data")
                                        .getJSONObject("nodesInfo")
                                        .getJSONArray("nodes");
            int sessionCount = 0;
            int maxSession = 0;
            for (Object nodeIter : nodes) {
                if (nodeIter instanceof JSONObject) {
                    JSONObject node = (JSONObject) nodeIter;
                    JSONArray sessions = node.getJSONArray("sessions");
                    sessions.forEach(sessionIter -> {
                        JSONObject session = (JSONObject) sessionIter;
                        String sessionId = session.get("id").toString();
                        String nodeUri = session.get("nodeUri").toString();
                        String startTimeStr = session.get("startTime").toString();
                        try {
                            SimpleDateFormat isoFormat = new SimpleDateFormat("dd/MM/yyyy hh:mm:ss");
                            Calendar c = Calendar.getInstance();
                            LocalDateTime dt = LocalDateTime.now();
                            TimeZone tz = c.getTimeZone();
                            ZoneId zone = tz.toZoneId();
                            ZonedDateTime zdt = dt.atZone(zone);
                            ZoneOffset offset = zdt.getOffset();
                            int totalMilliSeconds = 1000 * offset.getTotalSeconds();                            
                            Date startTime = isoFormat.parse(startTimeStr);                    
                            Date now = new Date();       
                            Long diff=Math.abs(now.getTime() - startTime.getTime())-totalMilliSeconds;
                            if(diff > MAX_SESSION_TIMEOUT){
                               deleteSession(nodeUri, sessionId);
                            }
                            logger.info("diff", diff);
                        } catch (ParseException e) {
                            e.printStackTrace();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                    });                    
                    sessionCount +=  (int)node.get("sessionCount");
                    maxSession +=  (int)node.get("maxSession");                    
                }
            }            
            status.setSessionCount(sessionCount);
            status.setMaxSession(maxSession);
        }
        catch (Exception e)
        {
            logger.error(e.getMessage());
        }
        return status;
    }

}

 