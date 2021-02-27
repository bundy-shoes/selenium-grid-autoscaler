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
import java.util.List;
import java.util.TimeZone;
import java.util.Map.Entry;

import javax.annotation.PostConstruct;

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

    @Value("${selenium_grid_host}")
    private String seleniumGridHost;

    @Value("${selenium_grid_port}")
    private String seleniumGridPort;

    @Value("${graphql}")
    private String graphql;

    @Value("${gridPath}")
    private String gridPath;

    private OkHttpClient httpClient;

    private static final Logger logger = LoggerFactory.getLogger(PodScalingService.class);

    @Value("${max_session_timeout}")
    private Long MAX_SESSION_TIMEOUT;

    @PostConstruct
    private void init() {
        logger.info("Grid Console URL: {}", gridUrl);
        logger.info("Grid Graphql URL: {}", graphql);
        logger.info("Grid Host: {}", seleniumGridHost);
        logger.info("Grid Port: {}", seleniumGridPort);
        logger.info("Grid Graphql URL: {}", graphql);
        logger.info("Grid API Path", gridPath);
        httpClient = new OkHttpClient();
    }

    public Response deleteNode(String nodeId) throws IOException {
        Response response = null;
        try {
            String gridUrl = String.format("%s:%s/%s/distributor/node/%s", seleniumGridHost, seleniumGridPort, gridPath,
                    nodeId);
            Request r = new Request.Builder().url(gridUrl).header("X-REGISTRATION-SECRET", "").delete().build();
            logger.info(String.format("terminating node, %s", nodeId));
            response = httpClient.newCall(r).execute();
            logger.info(String.format("node %s terminated", nodeId));
        } catch (Exception e) {
            logger.error(String.format("#GridConsoleStatus, deleteNode %s", e.getMessage()));
        }
        return response;
    }

    public Response drainNode(String nodeId) throws IOException {
        Response response = null;
        try {
            MediaType JSON = MediaType.parse("application/json");
            RequestBody body = RequestBody.create(JSON, "");
            String gridUrl = String.format("%s:%s/%s/distributor/node/%s/drain", seleniumGridHost, seleniumGridPort,
                    gridPath, nodeId);
            Request r = new Request.Builder().url(gridUrl).header("Accept", "application/json")
                    .header("Content-Type", "application/json").header("X-REGISTRATION-SECRET", "").post(body).build();
            response = httpClient.newCall(r).execute();
        } catch (Exception e) {
            logger.error(String.format("#GridConsoleStatus, drainNode %s", e.getMessage()));
        }
        return response;
    }

    public Response checkNodeStatus(String nodeUri) throws IOException {
        Response response = null;
        try {
            String gridUrl = String.format("%s/status", nodeUri);
            Request r = new Request.Builder().url(gridUrl).get().build();
            response = httpClient.newCall(r).execute();
        } catch (Exception e) {
            logger.error(String.format("#GridConsoleStatus, checkNodeStatus %s", e.getMessage()));
        }
        return response;
    }

    public Response deleteSession(String nodeUri, String sessionId) throws IOException {
        Response response = null;
        try {
            String gridUrl = String.format("%s/%s/node/session/%s", nodeUri, this.gridPath, sessionId);
            Request r = new Request.Builder().url(gridUrl).header("X-REGISTRATION-SECRET", "").delete().build();
            logger.info(String.format("terminating %s for node %s", sessionId, nodeUri));
            response = httpClient.newCall(r).execute();
            logger.info(String.format("%s terminated for node %s", sessionId, nodeUri));
        } catch (Exception e) {
            logger.error(String.format("#GridConsoleStatus, deleteSession %s", e.getMessage()));
        }
        return response;
    }

    public JSONArray getNodes(JSONObject obj) {
        JSONArray nodes = new JSONArray();
        try {
            nodes = obj.getJSONObject("data").getJSONObject("nodesInfo").getJSONArray("nodes");
        } catch (Exception e) {
            logger.error(String.format("#GridConsoleStatus, getNodes %s", e.getMessage()));
        }
        return nodes;
    }

    public GridConsoleStatus getStatus() throws IOException {
        GridConsoleStatus status = new GridConsoleStatus();
        try {
            JSONObject json = new JSONObject();
            String grapql = "query GetNodes {    nodesInfo {      nodes {        id        uri        status        maxSession        slotCount        sessions {          id          capabilities          startTime          uri          nodeId          nodeUri          sessionDurationMillis          slot {            id            stereotype            lastStarted          }        }        sessionCount        stereotypes        version        osInfo {          arch          name          version        }      }      __typename    }    __typename  }";
            json.put("query", grapql);
            json.put("variables", new JSONObject());
            RequestBody body = RequestBody.create(null, json.toString());
            Request r = new Request.Builder().url(this.graphql).header("Accept", "application/json")
                    .header("Content-Type", "application/json").post(body).build();
            Call call = httpClient.newCall(r);
            Response response = call.execute();
            String jsonString = response.body().string();
            JSONObject jsonObject = new JSONObject(jsonString);
            JSONArray nodes = getNodes(jsonObject);
            int sessionCount = 0;
            int maxSession = 0;
            int slotCount = 0;
            for (Object nodeIter : nodes) {
                if (nodeIter instanceof JSONObject) {
                    JSONObject node = (JSONObject) nodeIter;
                    String nodeUri = node.get("uri").toString();
                    String nodeId = node.get("id").toString();
                    Response nodeHealthCheck = checkNodeStatus(nodeUri);
                    if (nodeHealthCheck == null) {
                        status.addDeadNode(nodeId);
                    }
                    JSONArray sessions = node.getJSONArray("sessions");
                    sessions.forEach(sessionIter -> {
                        JSONObject session = (JSONObject) sessionIter;
                        String sessionId = session.get("id").toString();
                        String startTimeStr = session.get("startTime").toString();
                        try {
                            SimpleDateFormat isoFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
                            Calendar c = Calendar.getInstance();
                            LocalDateTime dt = LocalDateTime.now();
                            TimeZone tz = c.getTimeZone();
                            ZoneId zone = tz.toZoneId();
                            ZonedDateTime zdt = dt.atZone(zone);
                            ZoneOffset offset = zdt.getOffset();
                            int totalMilliSeconds = 1000 * offset.getTotalSeconds();
                            Date startTime = isoFormat.parse(startTimeStr);
                            Date now = new Date();
                            Long diff = Math.abs(now.getTime() - startTime.getTime()) - totalMilliSeconds;
                            if (diff > MAX_SESSION_TIMEOUT) {
                                status.addTimeoutSessions(nodeUri, sessionId);
                            }
                            logger.info(String.format("diff: %s", diff / 1000 / 60));
                        } catch (ParseException e) {
                            e.printStackTrace();
                        }
                    });
                    sessionCount += (int) node.get("sessionCount");
                    slotCount += (int) node.get("slotCount");
                    maxSession += (int) node.get("maxSession");
                }
            }

            status.setSessionCount(sessionCount);
            status.setMaxSession(maxSession);
            status.setSlotCount(slotCount);
        } catch (Exception e) {
            logger.error(String.format("#GridConsoleStatus, getStatus %s", e.getMessage()));
        }
        return status;
    }

    public void utilize(GridConsoleStatus status) {
        status.getDeadNodes().forEach(node -> {
            try {
                deleteNode(node);
            } catch (IOException e) {
                logger.error(String.format("#GridConsoleStatus, utilize failed for (nodeId=%s), %s", node, e.getMessage()));
            }
        });
        for (Entry<String, List<String>> entry : status.getTimeoutSessions().entrySet()) {
            String nodeUri = entry.getKey();
            List<String> sessions = entry.getValue();
            sessions.forEach(sessionId -> {
                try {
                    deleteSession(nodeUri, sessionId);
                } catch (Exception e) {
                    logger.error(String.format("#GridConsoleStatus, utilize failed for (nodeUri=%s, sessionId=%s), %s", nodeUri, sessionId, e.getMessage()));
                }
            });                        
        }
	}
}

 