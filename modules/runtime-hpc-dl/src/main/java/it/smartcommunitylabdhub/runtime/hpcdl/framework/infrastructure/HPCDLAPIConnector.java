package it.smartcommunitylabdhub.runtime.hpcdl.framework.infrastructure;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.runtime.hpcdl.framework.infrastructure.objects.HPCDLJob;

// @Component
public class HPCDLAPIConnector implements HPCDLConnector, InitializingBean {


    private String hpcdlURL;
    private String hpcdlToken;


    @Autowired
    public void setHpcdlURL(@Value("${hpcdl.url:}") String hpcdlURL) {
        this.hpcdlURL = hpcdlURL;
    }

    public void setHpcdlToken(@Value("${hpcdl.token:}") String hpcdlToken) {
        this.hpcdlToken = hpcdlToken;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        Assert.hasText(hpcdlToken, "HPC DL API Token is required");
        Assert.hasText(hpcdlURL, "HPC DL API URL is required");
    }

    @Override
    public HPCDLJob createJob(HPCDLJob job) {
        throw new UnsupportedOperationException("Not implemented");        
    }


    private String makeMPPost(Map<String, String> data, String path) {
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
        // multipart/form-data request body
        MultiValueMap<String, HttpEntity<?>> multipartBody = multipartBodyBuilder.build();

        // The complete http request body.
        HttpEntity<MultiValueMap<String, HttpEntity<?>>> httpEntity = new HttpEntity<>(multipartBody, headers);

        ResponseEntity<String> responseEntity = restTemplate.postForEntity(hpcdlURL + path, httpEntity, String.class);        
        return responseEntity.getBody();
    }

    @Override
    public HPCDLJob getJob(String jobId) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public HPCDLJob stopJob(String jobId) {
        throw new UnsupportedOperationException("Not implemented");
    }

}
