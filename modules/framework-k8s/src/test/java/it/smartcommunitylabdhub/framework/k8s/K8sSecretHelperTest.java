package it.smartcommunitylabdhub.framework.k8s;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.kubernetes.client.openapi.ApiException;
import it.smartcommunitylabdhub.framework.k8s.annotations.ConditionalOnKubernetes;
import it.smartcommunitylabdhub.framework.k8s.kubernetes.K8sSecretHelper;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

// @SpringBootTest
public class K8sSecretHelperTest {

    @Autowired(required = false)
    private K8sSecretHelper helper;

    // @Test
    @ConditionalOnKubernetes
    void readNamespacedSecret() throws ApiException {
        try {
            helper.deleteSecret("test");
        } catch (ApiException e) {}

        Map<String, String> data = null;
        try {
            data = helper.getSecretData("test");
        } catch (ApiException e) {
            e.printStackTrace();
        }
        assertEquals(data, null);
    }

    // @Test
    @ConditionalOnKubernetes
    void createSecret() {
        try {
            helper.deleteSecret("test");
        } catch (ApiException e) {}

        // create
        Map<String, String> data = new HashMap<>();
        data.put("mykey", "myvalue");
        try {
            helper.storeSecretData("test", data);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // read created
        Map<String, String> readData = null;
        try {
            readData = helper.getSecretData("test");
        } catch (ApiException e) {
            e.printStackTrace();
        }
        assertEquals(data, readData);

        data.put("mykey2", "myvalue2");
        try {
            helper.storeSecretData("test", data);
            readData = helper.getSecretData("test");
        } catch (Exception e) {
            e.printStackTrace();
        }
        assertEquals(data.get("mykey2"), readData.get("mykey2"));
        assertEquals(data.get("mykey"), readData.get("mykey"));

        data.put("mykey", "myvalue3");
        try {
            helper.storeSecretData("test", data);
            readData = helper.getSecretData("test");
        } catch (Exception e) {
            e.printStackTrace();
        }
        assertEquals(data.get("mykey2"), readData.get("mykey2"));
        assertEquals(data.get("mykey"), readData.get("mykey"));

        try {
            helper.deleteSecretKeys("test", Collections.singleton("mykey2"));
            readData = helper.getSecretData("test");
        } catch (Exception e) {
            e.printStackTrace();
        }
        assertEquals(null, readData.get("mykey2"));
    }

    // @AfterEach
    @ConditionalOnKubernetes
    public void cleanUp() throws ApiException {
        try {
            helper.deleteSecret("test");
        } catch (ApiException e) {}
    }
}
