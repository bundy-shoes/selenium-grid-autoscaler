package com.sahajamit.k8s.service;

import com.sahajamit.k8s.domain.GridConsoleStatus;
import com.squareup.okhttp.*;

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

    private OkHttpClient httpClient;

    private static final Logger logger = LoggerFactory.getLogger(PodScalingService.class);

   
    public GridConsoleStatus getStatus() throws IOException {
        GridConsoleStatus status = new GridConsoleStatus();
        try
        {
            httpClient = new OkHttpClient();
            JSONObject json = new JSONObject();

            String grapql="query GetNodes {  nodesInfo {    nodes {      id      uri      status      maxSession      slotCount      stereotypes      version      sessionCount      osInfo {        version        name        arch        __typename      }      __typename    }    __typename  }}";
            
            json.put("query", grapql);
            json.put("variables", new JSONObject());
 
            RequestBody body = RequestBody.create(null, json.toString());
            gridUrl="http://bundy-grid.com:30000/graphql";
            Request r = new Request.Builder()
                    .url(gridUrl)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .post(body)                    
                    .build();
            Call call = httpClient.newCall(r);
            Response response = call.execute();
            String jsonString = response.body().string();
            JSONObject jsonObject = new JSONObject(jsonString);                        
            JSONArray nodes = jsonObject.getJSONObject("data").getJSONObject("nodesInfo").getJSONArray("nodes");                                            
            int sessionCount = 0;
            int maxSession = 0;
            for(Object node: nodes){
                if (node instanceof JSONObject ) {
                    JSONObject x = (JSONObject) node;
                    sessionCount +=  (int)x.get("sessionCount");
                    maxSession +=  (int)x.get("maxSession");                    
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
 
         
   
     


        // int busyNodesCount = StringUtils.countOccurrencesOf(htmlContent, busyNode);
        // status.setBusyNodesCount(busyNodesCount);


        // int waitingRequestsCount = 0;
        // Matcher matcher = pendingRequests.matcher(htmlContent);
        // if (matcher.find()) {
        //     waitingRequestsCount = Integer.parseInt(matcher.group().split(" ")[0]);
        // }
        // status.setWaitingRequestsCount(waitingRequestsCount);

        // return status;
    }

}

 