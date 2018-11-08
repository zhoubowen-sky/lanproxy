package org.fengfei.lanproxy.server.config.web.routes;

import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.google.gson.JsonObject;
import org.fengfei.lanproxy.common.JsonUtil;
import org.fengfei.lanproxy.server.ProxyChannelManager;
import org.fengfei.lanproxy.server.config.ProxyConfig;
import org.fengfei.lanproxy.server.config.ProxyConfig.Client;
import org.fengfei.lanproxy.server.config.ProxyConfig.User;
import org.fengfei.lanproxy.server.config.web.ApiRoute;
import org.fengfei.lanproxy.server.config.web.RequestHandler;
import org.fengfei.lanproxy.server.config.web.RequestMiddleware;
import org.fengfei.lanproxy.server.config.web.ResponseInfo;
import org.fengfei.lanproxy.server.config.web.exception.ContextException;
import org.fengfei.lanproxy.server.metrics.MetricsCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.reflect.TypeToken;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;

/**
 * API 接口实现
 *
 * @author fengfei
 */
public class RouteConfig {

    protected static final String AUTH_COOKIE_KEY = "token";

    private static Logger logger = LoggerFactory.getLogger(RouteConfig.class);
    /**
     * 管理员不能同时在多个地方登录
     */
    private static String token;

    public static void init() {

        ApiRoute.addMiddleware(new RequestMiddleware() {
            @Override
            public void preRequest(FullHttpRequest request) {
                String cookieHeader = request.headers().get(HttpHeaders.Names.COOKIE);
                boolean authenticated = false;

                if (cookieHeader != null) {
                    String[] cookies = cookieHeader.split(";");
                    for (String cookie : cookies) {
                        String[] cookieArr = cookie.split("=");
                        if (AUTH_COOKIE_KEY.equals(cookieArr[0].trim())) {
                            if (cookieArr.length == 2 && cookieArr[1].equals(token)) {
                                authenticated = true;
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

                if (!request.getUri().equals("/login") && !authenticated) {
                    throw new ContextException(ResponseInfo.CODE_UNAUTHORIZED);
                }

                logger.info("handle request for api {}", request.getUri());
            }
        });

        // 获取配置详细信息
        ApiRoute.addRoute("/config/detail", new RequestHandler() {
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
        });

        // 更新配置
        ApiRoute.addRoute("/config/update", new RequestHandler() {
            @Override
            public ResponseInfo request(FullHttpRequest request) {
                byte[] buf = new byte[request.content().readableBytes()];
                request.content().readBytes(buf);
                String config = new String(buf, Charset.forName("UTF-8"));

                List<Client> clients = JsonUtil.json2object(config, new TypeToken<List<Client>>() {});


                if (clients == null) {
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
        });

        // 登录
        ApiRoute.addRoute("/login", new RequestHandler() {
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

                if (username.equals(ProxyConfig.getInstance().getConfigAdminUsername()) && password.equals(ProxyConfig.getInstance().getConfigAdminPassword())) {
                    token = UUID.randomUUID().toString().replace("-", "");
                    logger.debug("token:{}", token);
                    JsonObject object = new JsonObject();
                    object.addProperty("token", token);
                    object.addProperty("username", username);
                    return ResponseInfo.build(object);
                }

                return ResponseInfo.build(ResponseInfo.CODE_INVILID_PARAMS, "用户名或密码错误");
            }
        });

        ApiRoute.addRoute("/logout", new RequestHandler() {
            @Override
            public ResponseInfo request(FullHttpRequest request) {
                token = null;
                return ResponseInfo.build(ResponseInfo.CODE_OK, "success");
            }
        });

        ApiRoute.addRoute("/metrics/get", new RequestHandler() {
            @Override
            public ResponseInfo request(FullHttpRequest request) {
                return ResponseInfo.build(MetricsCollector.getAllMetrics());
            }
        });

        ApiRoute.addRoute("/metrics/getandreset", new RequestHandler() {
            @Override
            public ResponseInfo request(FullHttpRequest request) {
                return ResponseInfo.build(MetricsCollector.getAndResetAllMetrics());
            }
        });

        ApiRoute.addRoute("/user/update", new RequestHandler() {
            @Override
            public ResponseInfo request(FullHttpRequest request) {
                // TODO 处理更新用户信息
                byte[] buf = new byte[request.content().readableBytes()];
                request.content().readBytes(buf);
                String config = new String(buf, Charset.forName("UTF-8"));
                List<User> users = JsonUtil.json2object(config, new TypeToken<List<User>>(){});

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
        });

        // 获取所有用户的列表以及详情
        ApiRoute.addRoute("/user/detail", new RequestHandler() {
            @Override
            public ResponseInfo request(FullHttpRequest request) {
                List<User> users = ProxyConfig.getInstance().getUsers();
                return ResponseInfo.build(users);
            }
        });

        // 新增用户
        ApiRoute.addRoute("/user/add", new RequestHandler() {
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
                Boolean hasTheUser = false;
                Iterator<User> iterator = users.iterator();
                while (iterator.hasNext()){
                    User u = iterator.next();
                    if (u.getUsername().equals(user.getUsername())){
                        hasTheUser = true;
                    }
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
        });

        // 删除用户
        ApiRoute.addRoute("/user/delete", new RequestHandler() {
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
        });



        // TODO 获取程序版本

        // TODO 获取协议版本

        // TODO 获取用户信息 及其相关权限
    }

}
