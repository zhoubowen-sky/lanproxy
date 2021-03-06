package com.xbrother.lanproxy.server.config.web.routes;

import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.JsonObject;
import com.xbrother.lanproxy.server.config.web.ApiRoute;
import com.xbrother.lanproxy.server.config.web.RequestHandler;
import com.xbrother.lanproxy.server.config.web.RequestMiddleware;
import com.xbrother.lanproxy.server.config.web.ResponseInfo;
import com.xbrother.lanproxy.common.JsonUtil;
import com.xbrother.lanproxy.server.ProxyChannelManager;
import com.xbrother.lanproxy.server.config.ProxyConfig;
import com.xbrother.lanproxy.server.config.ProxyConfig.Client;
import com.xbrother.lanproxy.server.config.ProxyConfig.User;
import com.xbrother.lanproxy.server.config.web.exception.ContextException;
import com.xbrother.lanproxy.server.metrics.MetricsCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.reflect.TypeToken;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;

/**
 * API 接口实现
 */
public class RouteConfig {

    protected static final String AUTH_COOKIE_KEY = "token";

    private static Logger logger = LoggerFactory.getLogger(RouteConfig.class);

    /**
     * 所有用户都不允许多个地方登陆
     */
    private static Map<String,String> usernameTokenMap = new ConcurrentHashMap<>();

    public static void init() {
        ApiRoute.addMiddleware(preRequest());
        // 获取配置详细信息
        ApiRoute.addRoute("/config/detail", configDetail());
        // 更新配置
        ApiRoute.addRoute("/config/update", configUpdate());
        // 新增或修改某一个客户端的配置信息
        ApiRoute.addRoute("/config/updateone", configUpdateOne());
        // 删除一个客户端
        ApiRoute.addRoute("/config/deleteone", configDeleteOne());
        // 登录
        ApiRoute.addRoute("/login", login());
        // 注销
        ApiRoute.addRoute("/logout", logout());
        // 获取数据使用量
        ApiRoute.addRoute("/metrics/get", metricsGet());
        // 获取数据使用量并重置
        ApiRoute.addRoute("/metrics/getandreset", metricsGetAndReset());
        // 处理更新用户信息
        ApiRoute.addRoute("/user/update", userUpdate());
        // 获取所有用户的列表以及详情
        ApiRoute.addRoute("/user/detail", userDetail());
        // 新增用户
        ApiRoute.addRoute("/user/add", userAdd());
        // 删除用户
        ApiRoute.addRoute("/user/delete", userDelete());
        // TODO 获取版本信息
        ApiRoute.addRoute("/version", version());
        // 暴露给客户端用于检测网络连通状态的接口
        ApiRoute.addRoute("/checknetwork", checkNetwork());

    }

    private static RequestHandler checkNetwork(){
        return new RequestHandler() {
            @Override
            public ResponseInfo request(FullHttpRequest request) {
                return ResponseInfo.build(ResponseInfo.CODE_OK, "check network success");
            }
        };
    }

    private static RequestHandler configDeleteOne(){
        return new RequestHandler() {
            @Override
            public ResponseInfo request(FullHttpRequest request) {
                byte[] buf = new byte[request.content().readableBytes()];
                request.content().readBytes(buf);
                String config = new String(buf, Charset.forName("UTF-8"));
                logger.info("delete config params:{}", config);
                Client client = JsonUtil.json2object(config, new TypeToken<Client>(){});
                if (client == null){
                    logger.error("参数错误");
                    return ResponseInfo.build(ResponseInfo.CODE_INVILID_PARAMS, "Error json config");
                }

                try {
                    // 开始更新客户端配置信息
                    Channel channel = ProxyChannelManager.getCmdChannel(client.getClientKey());
                    if (channel != null){
                        ProxyChannelManager.removeCmdChannel(channel);
                    }

                    ProxyConfig.getInstance().deleteOneClient(client);

                }catch (Exception e){
                    logger.error("config delete error", e);
                    return ResponseInfo.build(ResponseInfo.CODE_INVILID_PARAMS, e.getMessage());
                }

                return ResponseInfo.build(ResponseInfo.CODE_OK, "success");
            }
        };
    }

    private static RequestHandler configUpdateOne(){
        return new RequestHandler() {
            @Override
            public ResponseInfo request(FullHttpRequest request) {
                byte[] buf = new byte[request.content().readableBytes()];
                request.content().readBytes(buf);
                String config = new String(buf, Charset.forName("UTF-8"));
                logger.info("update one config params:{}", config);
                Client client = JsonUtil.json2object(config, new TypeToken<Client>(){});
                if (client == null){
                    logger.error("前端参数错误");
                    return ResponseInfo.build(ResponseInfo.CODE_INVILID_PARAMS, "Error json config");
                }

                try {
                    // 开始更新客户端配置信息
                    ProxyConfig.getInstance().updateAddOneClient(client);

                }catch (Exception e){
                    logger.error("config update error", e);
                    return ResponseInfo.build(ResponseInfo.CODE_INVILID_PARAMS, e.getMessage());
                }

                return ResponseInfo.build(ResponseInfo.CODE_OK, "success");
            }
        };
    }

    private static RequestMiddleware preRequest(){
        return new RequestMiddleware() {
            @Override
            public void preRequest(FullHttpRequest request) {
                String cookieHeader = request.headers().get(HttpHeaders.Names.COOKIE);
                logger.debug("cookieHeader:{}", cookieHeader);
                boolean authenticated = false;

                if (cookieHeader != null) {
                    String[] cookies = cookieHeader.split(";");
                    for (String cookie : cookies) {
                        // token=bb2fe6e893284d2dbe079fc798c13d1d
                        String[] cookieArr = cookie.split("=");
                        // 判断 token 合法性
                        if (AUTH_COOKIE_KEY.equals(cookieArr[0].trim())) {
                            if (cookieArr.length == 2 ) {
                                Iterator<Map.Entry<String, String>> it = usernameTokenMap.entrySet().iterator();
                                while (it.hasNext()){
                                    Map.Entry<String, String> entry = it.next();
                                    String token = entry.getValue();
                                    if (cookieArr[1].equals(token)){
                                        authenticated = true;
                                    }
                                }
                            }
                        }
                    }
                }

                String auth = request.headers().get(HttpHeaders.Names.AUTHORIZATION);
                if (!authenticated && auth != null) {
                    String[] authArr = auth.split(" ");
                    if (authArr.length == 2 && authArr[0].equals(ProxyConfig.getInstance().getConfigAdminUsername()) && authArr[1].equals(ProxyConfig.getInstance().getConfigAdminPassword())) {
                        authenticated = true;
                    }
                }

                // 过滤掉 login 和 checknetwork
                if (!(request.getUri().equals("/login") || request.getUri().equals("/checknetwork")) && !authenticated){
//                    if (!request.getUri().equals("/login") && !authenticated){
                    throw new ContextException(ResponseInfo.CODE_UNAUTHORIZED);
                }

                logger.info("handle request for api {}", request.getUri());
            }
        };
    }

    private static RequestHandler configDetail(){
        return new RequestHandler() {
            @Override
            public ResponseInfo request(FullHttpRequest request) {
                List<Client> clients = ProxyConfig.getInstance().getClients();
                for (Client client : clients) {
                    Channel channel = ProxyChannelManager.getCmdChannel(client.getClientKey());
                    if (channel != null) {
                        client.setStatus(1);// 客户端在线
                    } else {
                        client.setStatus(0);// 客户端离线
                    }
                }
                return ResponseInfo.build(ProxyConfig.getInstance().getClients());
            }
        };
    }

    private static RequestHandler configUpdate(){
        return new RequestHandler() {
            @Override
            public ResponseInfo request(FullHttpRequest request) {
                byte[] buf = new byte[request.content().readableBytes()];
                request.content().readBytes(buf);
                String config = new String(buf, Charset.forName("UTF-8"));
                logger.info("update config params:{}", config);

                List<Client> clients = JsonUtil.json2object(config, new TypeToken<List<Client>>() {});
                if (clients == null) {
                    logger.error("前端参数错误");
                    return ResponseInfo.build(ResponseInfo.CODE_INVILID_PARAMS, "Error json config");
                }

                try {
                    ProxyConfig.getInstance().update(config);
                } catch (Exception ex) {
                    logger.error("config update error", ex);
                    return ResponseInfo.build(ResponseInfo.CODE_INVILID_PARAMS, ex.getMessage());
                }

                return ResponseInfo.build(ResponseInfo.CODE_OK, "success");
            }
        };
    }

    private static RequestHandler login(){
        return new RequestHandler() {
            @Override
            public ResponseInfo request(FullHttpRequest request) {
                byte[] buf = new byte[request.content().readableBytes()];
                request.content().readBytes(buf);
                String config = new String(buf);
                Map<String, String> loginParams = JsonUtil.json2object(config, new TypeToken<Map<String, String>>() {});

                if (loginParams == null) {
                    return ResponseInfo.build(ResponseInfo.CODE_INVILID_PARAMS, "参数异常");
                }

                String username = loginParams.get("username");
                String password = loginParams.get("password");

                logger.debug("登陆接口请求参数 username:{}  password:{}", username, password);
                if (username == null || password == null) {
                    return ResponseInfo.build(ResponseInfo.CODE_INVILID_PARAMS, "用户名或密码错误");
                }

                TwoTuple res = checkUserAuthority(username, password);

                if (res.getFirst()) {
                    String token = UUID.randomUUID().toString().replace("-", "");
                    logger.debug("{} 用户登录的 token:{}", username, token);
                    usernameTokenMap.put(username, token);

                    JsonObject object = new JsonObject();
                    object.addProperty("token", token);
                    object.addProperty("username", username);
                    object.addProperty("status", res.getSecond().getStatus());
                    return ResponseInfo.build(object);
                }

                return ResponseInfo.build(ResponseInfo.CODE_INVILID_PARAMS, "用户名或密码错误");
            }
        };
    }

    private static RequestHandler logout(){
        return new RequestHandler() {
            @Override
            public ResponseInfo request(FullHttpRequest request) {
                byte[] buf = new byte[request.content().readableBytes()];
                request.content().readBytes(buf);
                String logoutParams = new String(buf, Charset.forName("UTF-8"));
                logger.info("注销接口调用成功, 前端传入的参数:{}", logoutParams);
                User user = JsonUtil.json2object(logoutParams, new TypeToken<User>(){});
                String username = user.getUsername();
                if (username == null){
                    logger.error("注销接口，前端传入的参数异常");
                    return ResponseInfo.build(ResponseInfo.CODE_INVILID_PARAMS, "参数异常");
                }else {
                    if (usernameTokenMap.containsKey(username)){
                        usernameTokenMap.remove(username);
                        logger.debug("删除用户 {} 的 token {}", username, usernameTokenMap.get(username));
                        return ResponseInfo.build(ResponseInfo.CODE_OK, "success");
                    }else {
                        logger.warn("用户{}的token不存在", username);
                        return ResponseInfo.build(ResponseInfo.CODE_OK, "success");
                    }
                }
            }
        };
    }

    private static RequestHandler metricsGet(){
        return new RequestHandler() {
            @Override
            public ResponseInfo request(FullHttpRequest request) {
                return ResponseInfo.build(MetricsCollector.getAllMetrics());
            }
        };
    }

    private static RequestHandler metricsGetAndReset(){
        return new RequestHandler() {
            @Override
            public ResponseInfo request(FullHttpRequest request) {
                return ResponseInfo.build(MetricsCollector.getAndResetAllMetrics());
            }
        };
    }

    private static RequestHandler userUpdate(){
        return new RequestHandler() {
            @Override
            public ResponseInfo request(FullHttpRequest request) {
                byte[] buf = new byte[request.content().readableBytes()];
                request.content().readBytes(buf);
                String config = new String(buf, Charset.forName("UTF-8"));
                List<User> users = JsonUtil.json2object(config, new TypeToken<List<User>>(){});
                logger.debug("前端传入的更新用户信息的参数:{}", config);
                if (users == null){
                    return ResponseInfo.build(ResponseInfo.CODE_INVILID_PARAMS, "Error user json config");
                }
                try {
                    ProxyConfig.getInstance().updateUserInfo(config);

                }catch (Exception e){
                    logger.error("更新用户配置文件失败:{}",e);
                    return ResponseInfo.build(ResponseInfo.CODE_INVILID_PARAMS, e.getMessage());
                }

                return ResponseInfo.build(ResponseInfo.CODE_OK, "success");
            }
        };
    }

    private static RequestHandler userDetail(){
        return new RequestHandler() {
            @Override
            public ResponseInfo request(FullHttpRequest request) {
                List<User> users = ProxyConfig.getInstance().getUsers();
                return ResponseInfo.build(users);
            }
        };
    }

    private static RequestHandler userAdd(){
        return new RequestHandler() {
            @Override
            public ResponseInfo request(FullHttpRequest request) {
                byte[] buf = new byte[request.content().readableBytes()];
                request.content().readBytes(buf);
                String config = new String(buf, Charset.forName("UTF-8"));
                logger.debug("新增用户前端传入的参数:{}", config);
                User user = JsonUtil.json2object(config, new TypeToken<User>(){});
                logger.debug("user:{}, {}", user.getUsername(), user.getPassword());
                user.setStatus(1);
                List<User> users = ProxyConfig.getInstance().getUsers();
                // 校验是否已经有此用户
                boolean hasTheUser = false;
                Iterator<User> iterator = users.iterator();
                while (iterator.hasNext()){
                    User u = iterator.next();
                    if (u.getUsername().equals(user.getUsername())){
                        hasTheUser = true;
                    }
                }
                // 超级管理员用户名不允许添加
                if (user.getUsername().equals(ProxyConfig.getInstance().getConfigAdminUsername())){
                    hasTheUser = true;
                }

                if (hasTheUser){
                    logger.warn("用户名与系统已有用户重复，请更换其他用户名");
                    return ResponseInfo.build(ResponseInfo.CODE_INVILID_PARAMS, "用户名与系统已有用户重复，请更换其他用户名");
                }else {
                    users.add(user);
                    String s = JsonUtil.object2json(users);
                    ProxyConfig.getInstance().updateUserInfo(s);
                    return ResponseInfo.build(ResponseInfo.CODE_OK, "success");
                }
            }
        };
    }

    private static RequestHandler userDelete(){
        return new RequestHandler() {
            @Override
            public ResponseInfo request(FullHttpRequest request) {
                byte[] buf = new byte[request.content().readableBytes()];
                request.content().readBytes(buf);
                String config = new String(buf, Charset.forName("UTF-8"));
                logger.debug("删除用户前端传入的参数:{}", config);

                User u = JsonUtil.json2object(config, new TypeToken<User>(){});

                List<User> users = ProxyConfig.getInstance().getUsers();
                Iterator<User> iterator = users.iterator();
                while (iterator.hasNext()){
                    User uu = iterator.next();
                    if (uu.getUsername().equals(u.getUsername())){
                        iterator.remove();
                        logger.debug("删除的用户为：{}", uu.getUsername());
                    }
                }
                String s = JsonUtil.object2json(users);
                logger.debug("删除用户后的所有用户信息:{}", s);
                ProxyConfig.getInstance().updateUserInfo(s);
                return ResponseInfo.build(ResponseInfo.CODE_OK, "success");
            }
        };
    }

    private static RequestHandler version(){
        return new RequestHandler() {
            @Override
            public ResponseInfo request(FullHttpRequest request) {
                return ResponseInfo.build(ResponseInfo.CODE_OK, "success");
            }
        };
    }

    public static TwoTuple checkUserAuthority(String username, String password) {
        // admin 用户特殊处理
        TwoTuple r = new TwoTuple();
        if (username.equals(ProxyConfig.getInstance().getConfigAdminUsername())
                && password.equals(ProxyConfig.getInstance().getConfigAdminPassword())) {
            User adminUser = new User();
            adminUser.setUsername(ProxyConfig.getInstance().getConfigAdminUsername());
            adminUser.setPassword(ProxyConfig.getInstance().getConfigAdminPassword());
            // 3 代表超级管理员
            adminUser.setStatus(3);
            r = new TwoTuple(true, adminUser);
            return r;
        }
        List<User> users = ProxyConfig.getInstance().getUsers();
        Iterator<User> iterator = users.iterator();
        boolean passCheck = false;
        User user = null;
        while (iterator.hasNext()) {
            user = iterator.next();
            if (user.getUsername().equals(username) && user.getPassword().equals(password)) {
                passCheck = true;
                break;
            }
        }

        if (passCheck) {
            r.first = true;
            r.second = user;
            return r;
        }
        r.first = false;
        r.second = null;
        return r;
    }

    public static class TwoTuple{

        private  boolean first;

        private  User second;

        public TwoTuple() {

        }

        public TwoTuple(boolean first, User second) {
            this.first = first;
            this.second = second;
        }

        public boolean getFirst() {
            return first;
        }

        public void setFirst(boolean first) {
            this.first = first;
        }

        public User getSecond() {
            return second;
        }

        public void setSecond(User second) {
            this.second = second;
        }
    }


}
