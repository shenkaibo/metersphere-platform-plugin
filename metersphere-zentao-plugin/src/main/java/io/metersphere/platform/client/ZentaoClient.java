package io.metersphere.platform.client;

import im.metersphere.plugin.exception.MSPluginException;
import im.metersphere.plugin.utils.JSON;
import im.metersphere.plugin.utils.LogUtil;
import io.metersphere.platform.api.BaseClient;
import io.metersphere.platform.domain.*;
import io.metersphere.platform.utils.UnicodeConvertUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.util.Map;
import java.util.Set;

public abstract class ZentaoClient extends BaseClient {

    protected String ENDPOINT;

    protected String USER_NAME;

    protected String PASSWD;

    public RequestUrl requestUrl;

    public ZentaoClient(String url) {
        ENDPOINT = url;
    }

    public String login() {
        GetUserResponse getUserResponse = new GetUserResponse();
        String sessionId = "";
        try {
            sessionId = getSessionId();
            String loginUrl = requestUrl.getLogin();
            MultiValueMap<String, String> paramMap = new LinkedMultiValueMap<>();
            paramMap.add("account", USER_NAME);
            paramMap.add("password", PASSWD);
            HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(paramMap, new HttpHeaders());
            ResponseEntity<String> response = restTemplate.exchange(loginUrl + sessionId, HttpMethod.POST, requestEntity, String.class);
            getUserResponse = (GetUserResponse) getResultForObject(GetUserResponse.class, response);
        } catch (Exception e) {
            LogUtil.error(e);
            MSPluginException.throwException(e.getMessage());
        }
        GetUserResponse.User user = getUserResponse.getUser();
        if (user == null) {
            LogUtil.error(JSON.toJSONString(getUserResponse));
            // 登录失败，获取的session无效，置空session
            MSPluginException.throwException("zentao login fail, user null");
        }
        if (!StringUtils.equals(user.getAccount(), USER_NAME)) {
            LogUtil.error("login fail，inconsistent users");
            MSPluginException.throwException("zentao login fail, inconsistent user");
        }
        return sessionId;
    }

    public String getSessionId() {
        String getSessionUrl = requestUrl.getSessionGet();
        ResponseEntity<String> response = restTemplate.exchange(getSessionUrl,
                HttpMethod.GET, null, String.class);
        GetSessionResponse getSessionResponse = (GetSessionResponse) getResultForObject(GetSessionResponse.class, response);
        return JSON.parseObject(getSessionResponse.getData(), GetSessionResponse.Session.class).getSessionID();
    }

    public AddIssueResponse.Issue addIssue(MultiValueMap<String, Object> paramMap) {
        String sessionId = login();
        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(paramMap, new HttpHeaders());
        ResponseEntity<String> response = null;
        try {
            String bugCreate = requestUrl.getBugCreate();
            response = restTemplate.exchange(bugCreate + sessionId,
                    HttpMethod.POST, requestEntity, String.class);
        } catch (Exception e) {
            LogUtil.error(e.getMessage(), e);
            MSPluginException.throwException(e.getMessage());
        }
        AddIssueResponse addIssueResponse = (AddIssueResponse) getResultForObject(AddIssueResponse.class, response);
        AddIssueResponse.Issue issue = JSON.parseObject(addIssueResponse.getData(), AddIssueResponse.Issue.class);
        if (issue == null) {
            MSPluginException.throwException(UnicodeConvertUtils.unicodeToCn(response.getBody()));
        }
        return issue;
    }

    public void updateIssue(String id, MultiValueMap<String, Object> paramMap) {
        String sessionId = login();
        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(paramMap, new HttpHeaders());
        try {
            restTemplate.exchange(requestUrl.getBugUpdate(),
                    HttpMethod.POST, requestEntity, String.class, id, sessionId);
        } catch (Exception e) {
            LogUtil.error(e.getMessage(), e);
            MSPluginException.throwException(e.getMessage());
        }
    }

    public void deleteIssue(String id) {
        String sessionId = login();
        try {
            restTemplate.exchange(requestUrl.getBugDelete(),
                    HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), String.class, id, sessionId);
        } catch (Exception e) {
            LogUtil.error(e.getMessage(), e);
            MSPluginException.throwException(e.getMessage());
        }
    }

    public Map getBugById(String id) {
        String sessionId = login();
        String bugGet = requestUrl.getBugGet();
        ResponseEntity<String> response = restTemplate.exchange(bugGet,
                HttpMethod.GET, null, String.class, id, sessionId);
        GetIssueResponse getIssueResponse = (GetIssueResponse) getResultForObject(GetIssueResponse.class, response);
        if(StringUtils.equalsIgnoreCase(getIssueResponse.getStatus(),"fail")){
            GetIssueResponse.Issue issue = new GetIssueResponse.Issue();
            issue.setId(id);
            issue.setSteps(StringUtils.SPACE);
            issue.setTitle(StringUtils.SPACE);
            issue.setStatus("closed");
            issue.setDeleted("1");
            issue.setOpenedBy(StringUtils.SPACE);
            getIssueResponse.setData(JSON.toJSONString(issue));
        }
        return JSON.parseMap(getIssueResponse.getData());
    }

    public GetCreateMetaDataResponse.MetaData getCreateMetaData(String productID) {
        String sessionId = login();
        ResponseEntity<String> response = restTemplate.exchange(requestUrl.getCreateMetaData(),
                HttpMethod.GET, null, String.class, productID, sessionId);
        GetCreateMetaDataResponse getCreateMetaDataResponse = (GetCreateMetaDataResponse) getResultForObject(GetCreateMetaDataResponse.class, response);
        return JSON.parseObject(getCreateMetaDataResponse.getData(), GetCreateMetaDataResponse.MetaData.class);
    }

    public Map getCustomFields(String productID) {
        return getCreateMetaData(productID).getCustomFields();
    }

    public Map<String, Object> getBuildsByCreateMetaData(String projectId) {
        return getCreateMetaData(projectId).getBuilds();
    }

    public Map<String, Object> getBuilds(String projectId) {
        String sessionId = login();
        ResponseEntity<String> response = restTemplate.exchange(requestUrl.getBuildsGet(),
                HttpMethod.GET, null, String.class, projectId, sessionId);
        return (Map<String, Object>) JSON.parseMap(response.getBody()).get("data");
    }

    public Map<String, Object> getBuildsV17(String projectId) {
        String sessionId = login();
        ResponseEntity<String> response = restTemplate.exchange(requestUrl.getBuildsGetV17(),
                HttpMethod.GET, null, String.class, projectId, sessionId);
        return (Map<String, Object>) JSON.parseMap(response.getBody()).get("data");
    }

    public String uploadFile(File file) {
        String id = "";
        String sessionId = login();
        HttpHeaders httpHeaders = new HttpHeaders();
        MultiValueMap<String, Object> paramMap = new LinkedMultiValueMap<>();
        paramMap.add("files", new FileSystemResource(file));
        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(paramMap, httpHeaders);
        RestTemplate restTemplate = new RestTemplate();
        try {
            ResponseEntity<String> responseEntity = restTemplate.exchange(requestUrl.getFileUpload(), HttpMethod.POST, requestEntity,
                    String.class, null, sessionId);
            String body = responseEntity.getBody();
            Map obj = JSON.parseMap(body);
            Map data = (Map) JSON.parseObject(obj.get("data").toString());
            Set<String> set = data.keySet();
            if (!set.isEmpty()) {
                id = (String) set.toArray()[0];
            }
        } catch (Exception e) {
            LogUtil.error(e, e.getMessage());
        }
        LogUtil.info("upload file id: " + id);
        return id;
    }

    public Map getBugsByProjectId(String projectId, Integer pageNum, Integer pageSize) {
        String sessionId = login();
        ResponseEntity<String> response = restTemplate.exchange(requestUrl.getBugList(),
                HttpMethod.GET, null, String.class, projectId, 9999999, pageSize, pageNum, sessionId);
        try {
            return JSON.parseMap(JSON.parseMap(response.getBody()).get("data").toString());
        } catch (Exception e) {
            LogUtil.error(e);
            MSPluginException.throwException("请检查配置信息是否填写正确！");
        }
        return null;
    }

    public String getBaseUrl() {
        if (ENDPOINT.endsWith("/")) {
            return ENDPOINT.substring(0, ENDPOINT.length() - 1);
        }
        return ENDPOINT;
    }

    public void setConfig(ZentaoConfig config) {
        if (config == null) {
            MSPluginException.throwException("config is null");
        }
        USER_NAME = config.getAccount();
        PASSWD = config.getPassword();
        ENDPOINT = config.getUrl();
    }


    public String getReplaceImgUrl(String replaceImgUrl) {
        String baseUrl = getBaseUrl();
        String[] split = baseUrl.split("/");
        String suffix = split[split.length - 1];
        if (StringUtils.equals("biz", suffix)) {
            suffix = baseUrl;
        } else if (!StringUtils.equalsAny(suffix, "zentao", "pro", "zentaopms", "zentaopro", "zentaobiz")) {
            suffix = "";
        } else {
            suffix = "/" + suffix;
        }
        return String.format(replaceImgUrl, suffix);
    }

    public void checkProjectExist(String relateId) {
        String sessionId = login();
        ResponseEntity<String> response = restTemplate.exchange(requestUrl.getProductGet(),
                HttpMethod.GET, null, String.class, relateId, sessionId);
        try {
            Map data = ((Map) JSON.parseObject(JSON.parseMap(response.getBody()).get("data").toString()));
            if (StringUtils.isNotBlank(((Map) data.get("product")).get("id").toString())) {
                return;
            }
        } catch (Exception e) {
            LogUtil.error("checkProjectExist error: " + response.getBody());
        }
        MSPluginException.throwException("验证失败");
    }

    public void uploadAttachment(String objectType, String objectId, File file) {
        String sessionId = login();
        HttpHeaders authHeader = new HttpHeaders();
        authHeader.setContentType(MediaType.parseMediaType("multipart/form-data; charset=UTF-8"));

        MultiValueMap<String, Object> paramMap = new LinkedMultiValueMap<>();
        FileSystemResource fileResource = new FileSystemResource(file);
        paramMap.add("files", fileResource);
        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(paramMap, authHeader);

        try {
            restTemplate.exchange(requestUrl.getFileUpload(), HttpMethod.POST, requestEntity,
                    String.class, objectId, sessionId);
        } catch (Exception e) {
            LogUtil.info("upload zentao attachment error");
        }
    }

    public void deleteAttachment(String fileId) {
        String sessionId = login();
        try {
            restTemplate.exchange(requestUrl.getFileDelete(), HttpMethod.GET, null, String.class, fileId, sessionId);
        } catch (Exception e) {
            LogUtil.info("delete zentao attachment error");
        }
    }

    public byte[] getAttachmentBytes(String fileId) {
        String sessionId = login();
        ResponseEntity<byte[]> response = restTemplate.exchange(requestUrl.getFileDownload(), HttpMethod.GET,
                null, byte[].class, fileId, sessionId);
        return response.getBody();
    }

    public ResponseEntity proxyForGet(String path, Class responseEntityClazz) {
        im.metersphere.plugin.utils.LogUtil.info("zentao proxyForGet: " + path);
        String url = this.ENDPOINT + path;
        validateProxyUrl(url, "/index.php", "/file-read-");
        return restTemplate.exchange(url, HttpMethod.GET, null, responseEntityClazz);
    }
}
