package com.xbrother.lanproxy.server.config;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.*;
import com.xbrother.lanproxy.common.Config;
import com.xbrother.lanproxy.common.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.reflect.TypeToken;

/**
 * 用于管理客户端所有的 IP Port Key 的配置信息
 * 负责解析配置文件 更新配置文件
 */
public class ProxyConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    private static Logger logger = LoggerFactory.getLogger(ProxyConfig.class);

    /**
     * 客户端配置文件 config.json
     */
    public static final String CONFIG_FILE;

    /**
     * 用户信息的配置文件 user.json 普通用户
     */
    public static final String USER_CONFIG_FILE;

    /**
     * 客户端分组配置文件 group.json
     */
    public static final String CLIENT_GROUP_FILE;


    static {
        // 代理配置信息存放在用户根目录下
        String dataPath = System.getProperty("user.home") + "/" + ".lanproxy/";
        File file = new File(dataPath);
        if (!file.isDirectory()) {
            file.mkdir();
        }
        // 业务配置信息
        CONFIG_FILE = dataPath + "/config.json";
        // 用户账户配置信息
        USER_CONFIG_FILE = dataPath + "/user.json";
        // 客户端分组配置信息
        CLIENT_GROUP_FILE = dataPath + "/group.json";
    }

    /**
     * 运行模式 debug release
     */
    private String runningMode;

    /**
     * 前端页面的路径
     */
    private String webPath;

    /**
     * 代理服务器绑定主机host
     */
    private String serverBind;

    /**
     * 代理服务器与代理客户端通信端口
     */
    private Integer serverPort;

    /**
     * 配置服务绑定主机host
     */
    private String configServerBind;

    /**
     * 配置服务端口
     */
    private Integer configServerPort;

    /**
     * 配置服务管理员用户名
     */
    private String configAdminUsername;

    /**
     * 配置服务管理员密码
     */
    private String configAdminPassword;

    /**
     * 代理客户端，支持多个客户端
     */
    private List<Client> clients;

    /**
     * 更新配置后保证在其他线程即时生效
     */
    private static ProxyConfig instance = new ProxyConfig();

    /**
     * 代理服务器为各个代理客户端（key）开启对应的端口列表（value）
     */
    private volatile Map<String, List<Integer>> clientInetPortMapping = new HashMap<String, List<Integer>>();

    /**
     * 代理服务器上的每个对外端口（key）对应的代理客户端背后的真实服务器信息（value）
     */
    private volatile Map<Integer, String> inetPortLanInfoMapping = new HashMap<Integer, String>();

    /**
     * 配置变化监听器
     */
    private List<ConfigChangedListener> configChangedListeners = new ArrayList<ConfigChangedListener>();

    /**
     * 所有用户列表
     */
    private List<User> users;

    private ProxyConfig() {

        // 运行模式
        this.runningMode = Config.getInstance().getStringValue("running.mode", "release");
        this.webPath = Config.getInstance().getStringValue("web.path");

        // 代理服务器主机和端口配置初始化
        this.serverPort = Config.getInstance().getIntValue("server.port");
        this.serverBind = Config.getInstance().getStringValue("server.bind", "0.0.0.0");

        // 配置服务器主机和端口配置初始化
        this.configServerPort = Config.getInstance().getIntValue("config.server.port");
        this.configServerBind = Config.getInstance().getStringValue("config.server.bind", "0.0.0.0");

        // 配置服务器管理员登录认证信息
        this.configAdminUsername = Config.getInstance().getStringValue("config.admin.username");
        this.configAdminPassword = Config.getInstance().getStringValue("config.admin.password");

        logger.info("config init serverBind {}, serverPort {}, configServerBind {}, configServerPort {}, configAdminUsername {}, configAdminPassword {}", serverBind, serverPort, configServerBind, configServerPort, configAdminUsername, configAdminPassword);

        try {
            update(null);
            updateUserInfo(null);
        } catch (Exception e) {
            logger.error("config update error", e.getMessage());
        }
    }


    public String getWebPath() {
        return webPath;
    }

    public void setWebPath(String webPath) {
        this.webPath = webPath;
    }

    public String getRunningMode() {
        return runningMode;
    }

    public void setRunningMode(String runningMode) {
        this.runningMode = runningMode;
    }

    public Integer getServerPort() {
        return this.serverPort;
    }

    public String getServerBind() {
        return serverBind;
    }

    public void setServerBind(String serverBind) {
        this.serverBind = serverBind;
    }

    public String getConfigServerBind() {
        return configServerBind;
    }

    public void setConfigServerBind(String configServerBind) {
        this.configServerBind = configServerBind;
    }

    public Integer getConfigServerPort() {
        return configServerPort;
    }

    public void setConfigServerPort(Integer configServerPort) {
        this.configServerPort = configServerPort;
    }

    public String getConfigAdminUsername() {
        return configAdminUsername;
    }

    public void setConfigAdminUsername(String configAdminUsername) {
        this.configAdminUsername = configAdminUsername;
    }

    public String getConfigAdminPassword() {
        return configAdminPassword;
    }

    public void setConfigAdminPassword(String configAdminPassword) {
        this.configAdminPassword = configAdminPassword;
    }

    public void setServerPort(Integer serverPort) {
        this.serverPort = serverPort;
    }

    public List<Client> getClients() {
        return clients;
    }

    public List<User> getUsers() {
        return users;
    }

    /**
     * 解析用户配置文件
     *
     * @param userInfoJson 传入整个配置文件的内容 jsonStr 格式
     */
    public void updateUserInfo(String userInfoJson) {
        File file = new File(USER_CONFIG_FILE);
        try {
            if (userInfoJson == null && file.exists()) {
                userInfoJson = fileToString(file);
            }
        } catch (Exception e) {
            logger.error("打开用户数据文件失败:{}", e);
            throw new RuntimeException(e);
        }

        logger.debug("用户对象转换为JSON数据后:{}", userInfoJson);
        List<User> users = JsonUtil.json2object(userInfoJson, new TypeToken<List<User>>() {
        });
        if (users == null) {
            logger.warn("用户配置文件为 null");
            users = new ArrayList<User>();
        }

        this.users = users;

        if (userInfoJson != null) {
            try {
                FileOutputStream out = new FileOutputStream(file);

                JsonParser jsonParser = new JsonParser();
                JsonArray jsonArray = jsonParser.parse(userInfoJson).getAsJsonArray();

                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                String prettyJson = gson.toJson(jsonArray);

                out.write(prettyJson.getBytes(Charset.forName("UTF-8")));
                out.flush();
                out.close();
            } catch (Exception e) {
                logger.error("用户信息更新到配置文件错误:", e.getMessage());
                throw new RuntimeException(e);
            }
        }

    }


    /**
     * 解析客户端的配置文件
     */
    public void update(String proxyMappingConfigJson) {

        File file = new File(CONFIG_FILE);
        try {
            if (proxyMappingConfigJson == null && file.exists()) {
                // 此时传入为空 且客户端配置文件存在
                proxyMappingConfigJson = fileToString(file);
            }
        } catch (Exception e) {
            logger.error("打开客户端配置文件错误:", e.getMessage());
            throw new RuntimeException(e);
        }

        List<Client> clients = JsonUtil.json2object(proxyMappingConfigJson, new TypeToken<List<Client>>() {});

        if (clients == null) {
            logger.warn("客户端配置文件 clients is null");
            clients = new ArrayList<Client>();
        }
        // 代理服务器客户端 key 与端口映射关系 clientKey1 -> 50001
        Map<String, List<Integer>> clientInetPortMapping = new HashMap<String, List<Integer>>();
        // 代理服务器对外端口 与客户端后台真实IP映射关系  50001 -> 127.0.0.1:22
        Map<Integer, String> inetPortLanInfoMapping = new HashMap<Integer, String>();

        // 构造端口映射关系
        for (Client client : clients) {
            String clientKey = client.getClientKey();

            if (clientInetPortMapping.containsKey(clientKey)) {
                logger.warn("客户端密钥重复:" + clientKey + " 请更换其他的密钥");
                throw new IllegalArgumentException("客户端密钥重复:" + clientKey + " 请更换其他的密钥");
            }

            List<ClientProxyMapping> mappings = client.getProxyMappings();
            List<Integer> ports = new ArrayList<Integer>();
            clientInetPortMapping.put(clientKey, ports);

            for (ClientProxyMapping mapping : mappings) {
                // 将处于禁用状态的代理跳过
                if (!mapping.getStatus().equals("1")){
                    logger.warn("端口状态:" + mapping.getStatus() + "端口未启用，直接跳过，端口:" + mapping.getInetPort());
                    continue;
                }
                Integer port = mapping.getInetPort();
                ports.add(port);
                if (inetPortLanInfoMapping.containsKey(port)) {
                    logger.warn("代理中心服务器端口重复:" + port + " 请更换端口");
                    throw new IllegalArgumentException("代理中心服务器端口重复:" + port + " 请更换其他未使用的端口");
                }

                inetPortLanInfoMapping.put(port, mapping.getLan());
            }
        }

        // 替换之前的配置关系
        this.clientInetPortMapping = clientInetPortMapping;
        this.inetPortLanInfoMapping = inetPortLanInfoMapping;
        this.clients = clients;

        // 更新完后 写入到配置文件
        if (proxyMappingConfigJson != null) {
            try {
                FileOutputStream out = new FileOutputStream(file);

                JsonParser jsonParser = new JsonParser();
                JsonArray jsonArray = jsonParser.parse(proxyMappingConfigJson).getAsJsonArray();

                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                String prettyJson = gson.toJson(jsonArray);

                out.write(prettyJson.getBytes(Charset.forName("UTF-8")));
                out.flush();
                out.close();
            } catch (Exception e) {
                logger.error("客户端信息更新到配置文件错误:", e.getMessage());
                throw new RuntimeException(e);
            }
        }

        notifyconfigChangedListeners();
    }

    /**
     * 配置更新通知
     */
    private void notifyconfigChangedListeners() {
        List<ConfigChangedListener> changedListeners = new ArrayList<ConfigChangedListener>(configChangedListeners);
        for (ConfigChangedListener changedListener : changedListeners) {
            changedListener.onChanged();
        }
    }

    /**
     * 添加配置变化监听器
     *
     * @param configChangedListener
     */
    public void addConfigChangedListener(ConfigChangedListener configChangedListener) {
        configChangedListeners.add(configChangedListener);
    }

    /**
     * 移除配置变化监听器
     *
     * @param configChangedListener
     */
    public void removeConfigChangedListener(ConfigChangedListener configChangedListener) {
        configChangedListeners.remove(configChangedListener);
    }

    /**
     * 获取代理客户端对应的代理服务器端口
     * 该 client 所占用的所有公网端口列表
     *
     * @param clientKey
     * @return
     */
    public List<Integer> getClientInetPorts(String clientKey) {
        return clientInetPortMapping.get(clientKey);
    }

    /**
     * 获取所有的clientKey
     *
     * @return
     */
    public Set<String> getClientKeySet() {
        return clientInetPortMapping.keySet();
    }

    /**
     * 根据代理服务器端口获取后端服务器代理信息
     *
     * @param port
     * @return
     */
    public String getLanInfo(Integer port) {
        return inetPortLanInfoMapping.get(port);
    }

    /**
     * 返回需要绑定在代理服务器的端口（用于用户请求）
     *
     * @return
     */
    public List<Integer> getUserPorts() {
        List<Integer> ports = new ArrayList<Integer>();
        Iterator<Integer> ite = inetPortLanInfoMapping.keySet().iterator();
        // 此处需要判断 代理启用状态 只有启用的端口才允许绑定
        while (ite.hasNext()) {
            ports.add(ite.next());
        }

        return ports;
    }

    public static ProxyConfig getInstance() {
        return instance;
    }

    /**
     * 代理客户端
     *
     */
    public static class Client implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 客户端备注名称
         */
        private String name;

        /**
         * 代理客户端唯一标识key
         */
        private String clientKey;

        /**
         * 代理客户端与其后面的真实服务器映射关系
         */
        private List<ClientProxyMapping> proxyMappings;

        /**
         * 客户端当前的连接状态 1 为在线 0 为离线
         */
        private int status;

        /**
         * 客户端的 tag 标签 用以分组展示
         */
        private String tag;

        public String getTag() {
            return tag;
        }

        public void setTag(String tag) {
            this.tag = tag;
        }

        public String getClientKey() {
            return clientKey;
        }

        public void setClientKey(String clientKey) {
            this.clientKey = clientKey;
        }

        public List<ClientProxyMapping> getProxyMappings() {
            return proxyMappings;
        }

        public void setProxyMappings(List<ClientProxyMapping> proxyMappings) {
            this.proxyMappings = proxyMappings;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getStatus() {
            return status;
        }

        public void setStatus(int status) {
            this.status = status;
        }

    }


    /**
     * 用户信息的描述
     *
     * @author zhoubowen
     */
    public static class User implements Serializable {

        /**
         * 用户名 唯一 字母数字表示 不支持特殊字符
         */
        private String username;

        /**
         * 用户密码
         */
        private String password;

        /**
         * 用户账户当前的状态 启用 禁用
         */
        private int status;

        /**
         * 当前用户所拥有的客户端的权限
         */
        private List<String> clientKeys;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public int getStatus() {
            return status;
        }

        public void setStatus(int status) {
            this.status = status;
        }

        public List<String> getClientKeys() {
            return clientKeys;
        }

        public void setClientKeys(List<String> clientKeys) {
            this.clientKeys = clientKeys;
        }

    }

    /**
     * 代理客户端与其后面真实服务器映射关系
     */
    public static class ClientProxyMapping {

//        public ClientProxyMapping(){
//            this.status = "1"; // 默认启用
//        }

        /**
         * 代理服务器端口 即公网端口
         */
        private Integer inetPort;

        /**
         * 被代理的网络信息（代理客户端能够访问）格式 192.168.1.99:80 (必须带端口)
         */
        private String lan;

        /**
         * 本代理的备注名称
         */
        private String name;

        /**
         * 代理端口的启用状态 0 禁用 1 启用
         */
        private String status;

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }


        public Integer getInetPort() {
            return inetPort;
        }

        public void setInetPort(Integer inetPort) {
            this.inetPort = inetPort;
        }

        public String getLan() {
            return lan;
        }

        public void setLan(String lan) {
            this.lan = lan;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

    }

    /**
     * 配置更新回调 使用接口实现回调
     */
    public static interface ConfigChangedListener {
        void onChanged();
    }

    /**
     * 文件对象转换为字符串
     *
     * @param file 打开的文件句柄
     * @return
     */
    private String fileToString(File file) {
        String s = "";
        try {
            InputStream in = new FileInputStream(file);
            byte[] buf = new byte[1024];
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            int readIndex;
            while ((readIndex = in.read(buf)) != -1) {
                out.write(buf, 0, readIndex);
            }
            in.close();
            s = new String(out.toByteArray(), Charset.forName("UTF-8"));
            return s;
        } catch (Exception e) {
            logger.error("打开文件错误:{}", e);
            return s;
        }
    }


}
