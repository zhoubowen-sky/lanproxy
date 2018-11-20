package com.xbrother.lanproxy.server.config.web;

import io.netty.handler.codec.http.FullHttpRequest;

/**
 * 接口请求处理
 *
 */
public interface RequestHandler {

    /**
     * 请求处理
     *
     * @param request
     * @return
     */
    ResponseInfo request(FullHttpRequest request);
}