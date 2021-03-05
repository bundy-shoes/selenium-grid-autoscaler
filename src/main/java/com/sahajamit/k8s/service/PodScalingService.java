package com.sahajamit.k8s.service;

import com.sahajamit.k8s.domain.GridConsoleStatus;
import com.squareup.okhttp.*;
import com.squareup.okhttp.Request.Builder;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;

@Service
public class PodScalingService {
    private static final TrustManager[] UNQUESTIONING_TRUST_MANAGER = new TrustManager[] { new X509TrustManager() {
        public X509Certificate[] getAcceptedIssuers() {
            return null;
        }

        public void checkClientTrusted(X509Certificate[] certs, String authType) {
        }

        public void checkServerTrusted(X509Certificate[] certs, String authType) {
        }
    } };
    private static final Logger logger = LoggerFactory.getLogger(PodScalingService.class);
    @Value("${gridUrl}")
    private String gridUrl;
    @Value("${k8s_api_url}")
    private String k8sApiUrl;
    @Value("${node_chrome_max_scale_limit}")
    private int maxScaleLimit;
    @Value("${node_chrome_min_scale_limit}")
    private int minScaleLimit;
    @Value("${k8s_token}")
    private String k8sToken;

    @Value("${scale_up_timeout}")
    private int scaleUpTimeout;

    private int upCounter = 0;
    private int downCounter = 0;

    @Value("${scale_down_timeout}")
    private int scaleDownTimeout;

    private OkHttpClient httpClient;

    @PostConstruct
    private void init() throws NoSuchAlgorithmException, KeyManagementException {
        logger.info("Grid Console URL: {}", gridUrl);
        logger.info("K8s API URL: {}", k8sApiUrl);
        logger.info("K8s API Token: {}", k8sToken);

        httpClient = new OkHttpClient();
        SSLContext sc = SSLContext.getInstance("SSL");
        sc.init(null, UNQUESTIONING_TRUST_MANAGER, null);
        HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        httpClient.setSslSocketFactory(sc.getSocketFactory());
    }

    private void updateScale(int scaledValue) throws IOException, InterruptedException {
        if (scaledValue > maxScaleLimit)
            logger.warn(
                    "Scale required {} which is more than the max scale limit of {}. Hence no auto-scaling is performed.",
                    scaledValue, maxScaleLimit);
        else if (scaledValue < minScaleLimit)
            logger.warn(
                    "Scale required {} which is less than the min scale limit of {}. Hence no auto-scaling is performed.",
                    scaledValue, minScaleLimit);
        else {
            scale(scaledValue);
        }
    }

    private void scale(int scaledValue) throws IOException, InterruptedException {
        MediaType JSON = MediaType.parse("application/strategic-merge-patch+json");
        String payload = String.format("{ \"spec\": { \"replicas\": %s } }", scaledValue);

        logger.info(String.format("url: %s", k8sApiUrl));
        RequestBody body = RequestBody.create(JSON, payload);

        Builder requestBuilder = new Request.Builder().url(k8sApiUrl).header("Authorization", "Bearer " + k8sToken)
                .header("Accept", "application/json").header("Content-Type", "application/strategic-merge-patch+json");

        Request patchScale = requestBuilder.patch(body).build();
        Request getScale = requestBuilder.get().build();
        Call call = httpClient.newCall(patchScale);
        Response response = call.execute();
        if (response.code() != 200)
            throw new RuntimeException("Error while scaling the grid: " + response.body().string());
        String responseString = response.body().string();
        JSONObject jsonObject = new JSONObject(responseString);
        int updatedScale;

        JSONObject spec = jsonObject.getJSONObject("spec");
        if (spec.has("replicas"))
            updatedScale = spec.getInt("replicas");
        else
            updatedScale = 0;

        if (updatedScale != scaledValue)
            throw new RuntimeException(
                    String.format("Error in scaling. Here is the json response: %s", responseString));
        else
            waitForScaleToHappen(getScale, scaledValue);
    }

    public void adjustScale(long cycle_time, GridConsoleStatus gridStatus) throws IOException, InterruptedException {
        logger.debug("Let's check if auto-scaling is required...");
        int maxSession = gridStatus.getMaxSession();
        int sessionCount = gridStatus.getSessionCount();
        int sessionQueueSize = gridStatus.getSessionQueueSize();
        int maxUpTimeIterations = Math.round(scaleUpTimeout / cycle_time);
        int maxDownTimeIterations = Math.round(scaleDownTimeout / cycle_time);

        if (maxSession < minScaleLimit) {
            updateScale(minScaleLimit);
            return;
        }

        // no demand so supply minimum or downgrade
        if (sessionQueueSize == 0) { // no requests are waiting in queue
            if ((maxSession - 1 >= minScaleLimit) && (maxSession - sessionCount > 0)) {
                upCounter = 0; // init upscale count
                downCounter = downCounter + 1;
                logger.info("down counter {}", downCounter);
                if (downCounter >= maxDownTimeIterations) {
                    downCounter = 0;
                    logger.info("Scaling Down");
                    updateScale(Math.max(minScaleLimit, maxSession - 1));
                }
            }
            return;
        }
        // demand is higher then supply
        if (maxSession - sessionCount == 0) {
            downCounter = 0; // init downscale count
            if (sessionQueueSize > 0) { // requests are waiting in queue
                upCounter = upCounter + 1;
                logger.info("up counter {}", downCounter);
                if (upCounter >= maxUpTimeIterations) {
                    upCounter = 0;
                    logger.info("Scaling Up");
                    updateScale(maxSession + 1);
                    return;
                }
            }
        }
    }

    public void cleanUp() throws IOException, InterruptedException {
        logger.info("Cleaning up the Grid by re-starting all the nodes");
        scale(0);
        scale(minScaleLimit);
    }

    private void waitForScaleToHappen(Request getScale, int scale) throws IOException, InterruptedException {
        int existingScale = scale - 1;
        Response response = null;
        int pollingTime = 5;
        int counter = 20;
        do {
            TimeUnit.SECONDS.sleep(pollingTime);
            Call call = httpClient.newCall(getScale);
            response = call.execute();
            if (response != null && response.code() != 200) {
                String message = response.body().string();
                throw new RuntimeException(String.format("Error while fetching current scale from grid: %s", message));
            }
            String responseString = response.body().string();
            JSONObject jsonObject = new JSONObject(responseString);
            JSONObject status = jsonObject.getJSONObject("status");
            existingScale = status.getInt("replicas");
            logger.info("Sleeping {} seconds for scaling to happen. Current scale: {} and required scale: {}",
                    pollingTime, existingScale, scale);
        } while ((existingScale != scale) && (--counter > 0));
        boolean verify = (existingScale == scale);
        String message = verify ? "Selenium Grid is successfully scaled from {} to {}"
                : "Selenium Grid failed scaling from {} to {}";
        logger.info(message, existingScale, scale);
    }

}
