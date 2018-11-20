package com.xbrother.lanproxy.protocol;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.zip.Deflater;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.Inflater;

/**
 * @Author: 周博文
 * @Date: 2018/11/17 14:01
 */
public class Compress {

    public static byte[] zlibCompress(byte[] data) throws Exception{

        byte[] out = new byte[0];

        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);

        deflater.reset();
        deflater.setInput(data);
        deflater.finish();

        ByteArrayOutputStream bos = new ByteArrayOutputStream(data.length);
        byte[] buf = new byte[128];
        while (!deflater.finished()){
            int i = deflater.deflate(buf);
            bos.write(buf, 0, i);
        }
        out = bos.toByteArray();
        bos.close();
        deflater.end();
        return out;
    }

    public static byte[] zlibDeCompress(byte[] data) throws Exception{

        if (data == null){
            return null;
        }

        byte[] out = new byte[0];

        Inflater inflater = new Inflater();
        inflater.reset();
        inflater.setInput(data);

        ByteArrayOutputStream bos = new ByteArrayOutputStream(data.length);
        byte[] buf = new byte[128];
        while (!inflater.finished()){
            int i = inflater.inflate(buf);
            bos.write(buf,0,i);
        }
        out = bos.toByteArray();
        bos.close();
        inflater.end();

        return out;
    }



    public static byte[] gzipCompress(byte[] data) throws Exception {

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        GZIPOutputStream gzip = new GZIPOutputStream(bos);
        gzip.write(data);
        gzip.finish();
        gzip.close();

        byte[] ret = bos.toByteArray();
        bos.close();
        return ret;
    }

    public static byte[] gzipDeCompress(byte[] data) throws Exception {
        ByteArrayInputStream bis = new ByteArrayInputStream(data);
        GZIPInputStream gzip = new GZIPInputStream(bis);
        byte[] buf = new byte[128];
        int num = -1;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        while ((num = gzip.read(buf, 0, buf.length)) != -1) {
            bos.write(buf, 0, num);
        }
        gzip.close();
        bis.close();
        byte[] ret = bos.toByteArray();
        bos.flush();
        bos.close();
        return ret;
    }


}
