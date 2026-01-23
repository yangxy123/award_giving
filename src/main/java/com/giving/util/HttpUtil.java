package com.giving.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.ObjectUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * @author zzby
 * @version 创建时间： 2026/1/23 上午11:25
 */
@Component
public class HttpUtil {
    @Autowired
    private RestTemplate restTemplate;

    /**
     * 发送get请求
     *
     * @author yangxy
     * @version 创建时间：2023年9月13日 上午9:26:25
     * @return
     */
    public ResponseEntity<String> doGet(String url, Map<String,String> headerMap) {
        HttpHeaders headers = new HttpHeaders();
        if(!ObjectUtils.isEmpty(headerMap) && !headerMap.isEmpty()) {
            headers.setAll(headerMap);
        }
        HttpEntity entity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        return response;
    }

    /**
     * 发送json参数post请求
     * @author yangxy
     * @version 创建时间：2023年9月13日 上午9:29:27
     * @param url 请求地址
     * @param jsonStr json格式字符串请求参数
     * @return
     */
    public ResponseEntity<String> doJsonPost(String url, String jsonStr,Map<String,String> headerMap) {
        // 请求头中设备传递的数据类型未json
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        if(!ObjectUtils.isEmpty(headerMap) && !headerMap.isEmpty()) {
            headers.setAll(headerMap);
        }

        // 将json数据及请求头封装到请求体中
        HttpEntity<String> formEntity = new HttpEntity<String>(jsonStr, headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, formEntity, String.class);
        return response;
    }


    public ResponseEntity<String> sendFile(String url, Map<String, String> headerMap, MultipartFile file) throws IOException {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        if (!ObjectUtils.isEmpty(headerMap)) {
            headers.setAll(headerMap);
        }
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", file.getResource());
        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
        return restTemplate.postForEntity(url, requestEntity, String.class);
    }

    public ResponseEntity<String> sendMultipleFiles(String url, Map<String, String> headerMap, MultipartFile[] files) throws IOException {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        if (!ObjectUtils.isEmpty(headerMap)) {
            headers.setAll(headerMap);
        }
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        for (MultipartFile file : files) {
            body.add("files", file.getResource());
        }
        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
        return restTemplate.postForEntity(url, requestEntity, String.class);
    }

    public ResponseEntity<String> sendDeleteRequest(String url, Map<String, String> headerMap) {
        HttpHeaders headers = new HttpHeaders();
        if (!ObjectUtils.isEmpty(headerMap)) {
            headers.setAll(headerMap);
        }
        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);
        return restTemplate.exchange(url, HttpMethod.DELETE, requestEntity, String.class);
    }
}
