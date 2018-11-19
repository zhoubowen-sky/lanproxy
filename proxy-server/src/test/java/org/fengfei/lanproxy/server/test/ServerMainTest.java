package org.fengfei.lanproxy.server.test;

import org.fengfei.lanproxy.protocol.Compress;
import org.fengfei.lanproxy.server.ProxyServerContainer;

public class ServerMainTest {

    public static void main(String[] args) {
//        ProxyServerContainer.main(args);

        try {
            testGzipCompress();
        }catch (Exception e){

        }

    }

    public static void testGzipCompress() throws Exception{

        byte[] bytes = ("周波好烦追软包追不追bcdjhcbcbcewyevfvfvbvbebvdfvfdfvfdvfv周波好烦追软包" +
                "追不追bcdjhcbcbcewyevfvfvbvbebvdfvfdfvfdvfv周波好烦追软包追不追bcdjhcbcbcew" +
                "yevfvfvbvbebvdfvfdfvfdvfv周波好烦追软包追不追bcdjhcbcbcewyevfvfvbvbebvdfvfdf" +
                "vfdvfv周波好烦追软包追不追bcdjhcbcbcewyevfvfvbvbebvdfvfdfvfdvfv周波好烦追软包追不追b" +
                "cdjhcbcbcewyevfvfvbvbebvdfvfdfvfdvfv周波好烦追软包追不追bcdjhcbcbcewyevfv" +
                "fvbvbebvdfvfdfvfdvfv周波好烦追软包追不追bcdjhcbcbcewyevfvfvbvbebvdfvfdfvfdvfv周波好烦追软" +
                "包追不追bcdjhcbcbcewyevfvfvbvbebvdfvfdfvfdvfv周波好烦追软包追不追bcdjhcbcbcewyevfvfvbvbebvdfvfdfvfdvf" +
                "v周波好烦追软包追不追bcdjhcbcbcewyevfvfvbvbebvdfvfdfvfdvfv周波好烦追软包追不追bcdjhcbcbcewyevfvfvbvbebv" +
                "dfvfdfvfdvfv周波好烦追软包追不追bcdjhcbcbcewyevfvfvbvbebvdfvfdfvfdvfv周波" +
                "好烦追软包追不追bcdjhcbcbcewyevfvfvbvbebvdfvfdfvfdvfv周波好烦追软包追不追bcdjhcbcbcewyevfvfvbvbebvdfvfdfvfdvfv" +
                "追不追bcdjhcbcbcewyevfvfvbvbebvdfvfdfvfdvfv周波好烦追软包追不追bcdjhcbcbcew" +
                "yevfvfvbvbebvdfvfdfvfdvfv周波好烦追软包追不追bcdjhcbcbcewyevfvfvbvbebvdfvfdf" +
                "vfdvfv周波好烦追软包追不追bcdjhcbcbcewyevfvfvbvbebvdfvfdfvfdvfv周波好烦追软包追不追b" +
                "cdjhcbcbcewyevfvfvbvbebvdfvfdfvfdvfv周波好烦追软包追不追bcdjhcbcbcewyevfv" +
                "fvbvbebvdfvfdfvfdvfv周波好烦追软包追不追bcdjhcbcbcewyevfvfvbvbebvdfvfdfvfdvfv周波好烦追软" +
                "包追不追bcdjhcbcbcewyevfvfvbvbebvdfvfdfvfdvfv周波好烦追软包追不追bcdjhcbcbcewyevfvfvbvbebvdfvfdfvfdvf" +
                "v周波好烦追软包追不追bcdjhcbcbcewyevfvfvbvbebvdfvfdfvfdvfv周波好烦追软包追不追bcdjhcbcbcewyevfvfvbvbebv" +
                "dfvfdfvfdvfv周波好烦追软包追不追bcdjhcbcbcewyevfvfvbvbebvdfvfdfvfdvfv周波" +
                "好烦追软包追不追bcdjhcbcbcewyevfvfvbvbebvdfvfdfvfdvfv周波好烦追软包追不追bcdjhcbcbcewyevfvfvbvbebvdfvfdfvfdvfv" +
                "追不追bcdjhcbcbcewyevfvfvbvbebvdfvfdfvfdvfv周波好烦追软包追不追bcdjhcbcbcew" +
                "yevfvfvbvbebvdfvfdfvfdvfv周波好烦追软包追不追bcdjhcbcbcewyevfvfvbvbebvdfvfdf" +
                "vfdvfv周波好烦追软包追不追bcdjhcbcbcewyevfvfvbvbebvdfvfdfvfdvfv周波好烦追软包追不追b" +
                "cdjhcbcbcewyevfvfvbvbebvdfvfdfvfdvfv周波好烦追软包追不追bcdjhcbcbcewyevfv" +
                "fvbvbebvdfvfdfvfdvfv周波好烦追软包追不追bcdjhcbcbcewyevfvfvbvbebvdfvfdfvfdvfv周波好烦追软" +
                "包追不追bcdjhcbcbcewyevfvfvbvbebvdfvfdfvfdvfv周波好烦追软包追不追bcdjhcbcbcewyevfvfvbvbebvdfvfdfvfdvf" +
                "v周波好烦追软包追不追bcdjhcbcbcewyevfvfvbvbebvdfvfdfvfdvfv周波好烦追软包追不追bcdjhcbcbcewyevfvfvbvbebv" +
                "dfvfdfvfdvfv周波好烦追软包追不追bcdjhcbcbcewyevfvfvbvbebvdfvfdfvfdvfv周波" +
                "好烦追软包追不追bcdjhcbcbcewyevfvfvbvbebvdfvfdfvfdvfv周波好烦追软包追不追bcdjhcbcbcewyevfvfvbvbebvdfvfdfvfdvfv" +
                "dfvf周波好烦追软包追不追bcdjhcbcbcewyevfvfvbvbebvdfvfdfvfdvfvdfvf周波好烦追软" +
                "包追不追bcdjhcbcbcewyevfvfvbvbebvdfvfdfvfdvfvdfvf").getBytes();

        byte[] newBytes = Compress.zlibCompress(bytes);


        System.out.println("压缩前大小:" + bytes.length + "\n压缩后大小:" + newBytes.length);

        byte[] deCompress  = Compress.zlibDeCompress(newBytes);

        System.out.println("解压后长度:" + deCompress.length );


    }










}
