package it.smartcommunitylabdhub.runtime.hpcdl.framework.infrastructure;

import java.io.Serializable;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileUrlResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.runtime.hpcdl.framework.infrastructure.objects.HPCDLJob;

@Component
public class HPCDLAPIConnector implements HPCDLConnector, InitializingBean {


    private String hpcdlURL;
    private String hpcdlToken;
    private Map<String, Object> hpcConfig;
    private Map<String, Object> hpcServerConfig;

    private final static Set<String> METRIC_KEYS = new HashSet<>(    
        Arrays.asList(new String[] {
        "ALLOCCPUS", "ALLOCTRES", 
        "AVECPU", "AVECPUFREQ", "AVEDISKREAD", "AVEDISKWRITE", "AVEPAGES", "AVERSS", 
        "AVEVMSIZE", 
        "CONSUMEDENERGY", 
        "ELAPSED", 
        "MAXDISKREAD", "MAXDISKREADNODE", "MAXDISKREADTASK", "MAXDISKWRITE", "MAXDISKWRITENODE", "MAXDISKWRITETASK",
        "MAXPAGES", "MAXPAGESNODE", "MAXPAGESTASK", "MAXRSS", "MAXRSSNODE", "MAXRSSTASK", "MAXVMSIZE", "MAXVMSIZENODE", "MAXVMSIZETASK",
        "MINCPU", "MINCPUNODE", "MINCPUTASK", "NTASKS",
        "REQCPUFREQGOV", "REQCPUFREQMAX", "REQCPUFREQMIN", "REQMEM", "REQTRES",
        "TRESUSAGEINAVE", "TRESUSAGEINMAX", "TRESUSAGEINMAXNODE", "TRESUSAGEINMAXTASK", "TRESUSAGEINMIN", "TRESUSAGEINMINNODE", "TRESUSAGEINMINTASK", "TRESUSAGEINTOT", "TRESUSAGEOUTAVE",
        "TRESUSAGEOUTMAX", "TRESUSAGEOUTMAXNODE", "TRESUSAGEOUTMAXTASK", "TRESUSAGEOUTTOT"

    }));

    private final static Logger logger = LoggerFactory.getLogger(HPCDLAPIConnector.class);

    @Autowired
    public void setHpcdlURL(@Value("${hpcdl.url:}") String hpcdlURL) {
        this.hpcdlURL = hpcdlURL;
    }

    @Autowired
    public void setHpcdlToken(@Value("${hpcdl.token:}") String hpcdlToken) {
        this.hpcdlToken = hpcdlToken;
    }
    @SuppressWarnings("unchecked")
    @Autowired
    public void setHpcConfig(@Value("${hpcdl.config:}") String hpcConfig) throws JsonMappingException, JsonProcessingException {
        this.hpcConfig = (Map<String,Object>)new ObjectMapper().readValue(hpcConfig, Map.class);
    }

    @SuppressWarnings("unchecked")
    @Autowired
    public void setHpcServerConfig(@Value("${hpcdl.serverconfig:}") String hpcServerConfig) throws JsonMappingException, JsonProcessingException {
        this.hpcServerConfig = (Map<String,Object>)new ObjectMapper().readValue(hpcServerConfig, Map.class);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        Assert.hasText(hpcdlToken, "HPC DL API Token is required");
        Assert.hasText(hpcdlURL, "HPC DL API URL is required");
        Assert.notNull(hpcConfig, "HPC Config is required");
        Assert.notNull(hpcServerConfig, "HPC Server Config is required");
    }


    private FileUrlResource stringToFileResource(String content, String fileName) {
        try {
            java.nio.file.Path tempFile = Files.createTempFile(fileName, ".tmp");
            Files.write(tempFile, content.getBytes());
            return new FileUrlResource(tempFile.toFile().getAbsolutePath());
        } catch (Exception e) {
            throw new RuntimeException("Error creating file resource from string", e);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public HPCDLJob createJob(HPCDLJob job) {
        String path = "/v1/launch_container";
        Map<String, Object> config = new HashMap<>();
        config.put("config_hpc", new HashMap<>(hpcConfig));
        config.put("config_server", new HashMap<>(hpcServerConfig));
        if (job.getConfig() != null) {
            ((Map<String,Serializable>)config.get("config_server")).putAll(job.getConfig());
        }
        String configStr = null;
        try {
            configStr = new ObjectMapper().writeValueAsString(config);
        } catch (JsonProcessingException e) {
            job.setStatus(State.ERROR.name());
            job.setMessage("Error creating job: failed to parse config spec");
            return job;
        }

        String inputJson = "{}";
        String outputJson = "{}";
        try {
            if (job.getInputs() != null) inputJson = new ObjectMapper().writeValueAsString(job.getInputs());
        } catch (JsonProcessingException e) {
            job.setStatus(State.ERROR.name());
            job.setMessage("Error creating job: failed to parse inputs");
            return job;
        }
        try {
            if (job.getOutputs() != null) outputJson = new ObjectMapper().writeValueAsString(job.getOutputs());
        } catch (JsonProcessingException e) {
            job.setStatus(State.ERROR.name());
            job.setMessage("Error creating job: failed to parse outputs");
            return job;
        }

        Map<String, String> data = new HashMap<>();
        data.put("container_url", "docker://"+ job.getImage());
        String command = job.getCommand() != null ? job.getCommand() : "";
        if (job.getArgs() != null && job.getArgs().length > 0) {
            command += " " + String.join(" ", job.getArgs());
        }        
        data.put("exec_command", command);
        data.put("config_json", configStr);

        logger.info(path + " request with data");
        data.entrySet().forEach(e -> logger.info("data {}: {}", e.getKey(), e.getValue()));
        logger.info("file input_json: {}", inputJson);
        logger.info("file output_json: {}", outputJson);
        logger.info("file query_file: {}", "SELECT * FROM metadata WHERE field = none");

        Map<String, Object> files = new HashMap<>();
        files.put("output_json", stringToFileResource(outputJson, "output_json"));
        files.put("input_json",stringToFileResource(inputJson, "input_json"));
        // placeholder
        files.put("query_file", stringToFileResource("SELECT * FROM metadata WHERE field = none", "query_file"));


        String res = makeMPPost(data, files, path);
        if (res == null || res.isEmpty()) {
            job.setStatus(State.ERROR.name());
            job.setMessage("Error creating job: empty response from HPC DL API");
            return job;
        }
        String id = res.substring(res.indexOf("ID: ") + 4).trim();
        job.setId(id);
        job.setStatus(State.PENDING.name());
        return job;
    }


    private String makeMPPost(Map<String, String> data, Map<String, Object> files, String path) {
        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        // ContentType
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.add("Authorization", "Bearer " + hpcdlToken);
        MultipartBodyBuilder multipartBodyBuilder = new MultipartBodyBuilder();

        // Common form parameters.
        for (Map.Entry<String, String> entry : data.entrySet()) {
            multipartBodyBuilder.part(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, Object> entry : files.entrySet()) {
            multipartBodyBuilder.part(entry.getKey(), entry.getValue(), MediaType.APPLICATION_OCTET_STREAM);
        }

        // multipart/form-data request body
        MultiValueMap<String, HttpEntity<?>> multipartBody = multipartBodyBuilder.build();

        // The complete http request body.
        HttpEntity<MultiValueMap<String, HttpEntity<?>>> httpEntity = new HttpEntity<>(multipartBody, headers);

        ResponseEntity<String> responseEntity = restTemplate.postForEntity(hpcdlURL + path, httpEntity, String.class);        
        return responseEntity.getBody();
    }

    @SuppressWarnings("unchecked")
    @Override
    public HPCDLJob getJob(String jobId, Collection<String> hpcIds) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Bearer " + hpcdlToken);
        HttpEntity<String> entity = new HttpEntity<>(headers);
        String path = "/v1/job_status";
        String response = restTemplate.exchange(hpcdlURL + path, HttpMethod.GET, entity, String.class).getBody();
        try {
            Map<String, Object> responseMap = new ObjectMapper().readValue(response, Map.class);
            Map<String, Object> jobs = responseMap.get("jobs") != null ? (Map<String, Object>) responseMap.get("jobs") : Collections.emptyMap();

            HPCDLJob job = new HPCDLJob();
            job.setId(jobId);
            job.setHpcIds(hpcIds != null ? new LinkedList<>(hpcIds) : new LinkedList<>());

            for (String key: jobs.keySet()) {
                Map<String, Object> jobData = (Map<String, Object>) jobs.get(key);
                logger.debug("response jobData: {}, {}, {}", jobData.get("JOBID"), jobData.get("DATA_LAKE_JOBID"), jobData.get("STATE"));

                String dataHpcId = key;
                if (dataHpcId.indexOf('.') > 0) {
                    dataHpcId = dataHpcId.substring(0, dataHpcId.indexOf('.'));
                }
                if (hpcIds != null && hpcIds.contains(dataHpcId)) {
                    // batch: get state and metrics
                    if (key.endsWith(".batch")) {
                        job.setStatus(mapState((String) jobData.get("STATE")));
                        job.setMetrics(new HashMap<>());
                        for (String metricKey: jobData.keySet()) {
                            if (isMetricKey(metricKey)) {
                                job.getMetrics().put(metricKey, (String)jobData.get(metricKey));    
                            }
                        }
                    }
                } else {
                    // Pending or starting, status is PENDING
                    if (jobData.get("DATA_LAKE_JOBID") != null && jobData.get("DATA_LAKE_JOBID").equals(jobId)) {
                        job.getHpcIds().add(key);
                        job.setStatus(State.PENDING.name());
                    }
                    else if (jobData.get("WORK_DIR") != null && jobData.get("WORK_DIR").toString().endsWith("/"+jobId)) {
                        job.getHpcIds().add(key);
                        job.setStatus(State.PENDING.name());
                    }

                }
            }
            if (job.getStatus() == null) {
                logger.warn("Job not found: {}", jobId);
                job.setStatus(State.ERROR.name());
                job.setMessage("Job not found");
            }
            logger.debug("response job: {}, {}, {}, {}", job.getId(), job.getHpcIds(), job.getStatus(), job.getMetrics());
            return job;
        } catch (Exception e) {
            logger.error("Error retrieving job status", e);
            return null;
        }
    }

    private boolean isMetricKey(String metricKey) {
        return METRIC_KEYS.contains(metricKey);
    }

    private String mapState(String st) {
        switch (st) {
            case "BOOT_FAIL":
            case "DEADLINE":
            case "FAILED":
            case "NODE_FAIL":
            case "OUT_OF_MEMORY":
            case "PREEMPTED":
            case "TIMEOUT":
            case "CANCELLED":
            case "SPECIAL_EXIT":
            case "SUSPENDED":
            case "REVOKED":
                return State.ERROR.name();
            case "COMPLETED":
                return State.COMPLETED.name();
            case "STOPPED":
                return State.STOPPED.name();
            case "PENDING":
            default:
                return State.RUNNING.name();
        }
    }

    @Override
    public HPCDLJob stopJob(String jobId) {
            HPCDLJob job = new HPCDLJob();
            job.setId(jobId);
            job.setStatus(State.DELETED.name());
            job.setMessage("Job stopped by user");
            return job;
    }
}
