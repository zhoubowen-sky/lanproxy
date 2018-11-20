package com.xbrother.lanparoxy.client.test;

import java.util.Arrays;

import com.xbrother.lanproxy.client.ProxyClientContainer;
import com.xbrother.lanproxy.common.container.Container;
import com.xbrother.lanproxy.common.container.ContainerHelper;

public class TestMain {

    public static void main(String[] args) {
        ContainerHelper.start(Arrays.asList(new Container[] { new ProxyClientContainer() }));
    }

}
