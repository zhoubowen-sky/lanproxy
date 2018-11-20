package org.fengfei.lanproxy.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;

public class ProxyMessageDecoder extends LengthFieldBasedFrameDecoder {

    private static final byte HEADER_SIZE = 4;

    private static final int TYPE_SIZE = 1;

    private static final int SERIAL_NUMBER_SIZE = 8;

    private static final int URI_LENGTH_SIZE = 1;

    /**
     * @param maxFrameLength
     * @param lengthFieldOffset
     * @param lengthFieldLength
     * @param lengthAdjustment
     * @param initialBytesToStrip
     */
    public ProxyMessageDecoder(int maxFrameLength, int lengthFieldOffset, int lengthFieldLength, int lengthAdjustment, int initialBytesToStrip) {
        super(maxFrameLength, lengthFieldOffset, lengthFieldLength, lengthAdjustment, initialBytesToStrip);
    }

    /**
     * @param maxFrameLength
     * @param lengthFieldOffset
     * @param lengthFieldLength
     * @param lengthAdjustment
     * @param initialBytesToStrip
     * @param failFast
     */
    public ProxyMessageDecoder(int maxFrameLength, int lengthFieldOffset, int lengthFieldLength, int lengthAdjustment, int initialBytesToStrip, boolean failFast) {
        super(maxFrameLength, lengthFieldOffset, lengthFieldLength, lengthAdjustment, initialBytesToStrip, failFast);
    }

    @Override
    protected ProxyMessage decode(ChannelHandlerContext ctx, ByteBuf in2) throws Exception {
        ByteBuf in = (ByteBuf) super.decode(ctx, in2);
        if (in == null) {
            return null;
        }

        if (in.readableBytes() < HEADER_SIZE) {
            return null;
        }

        int frameLength = in.readInt();
        if (in.readableBytes() < frameLength) {
            return null;
        }
        ProxyMessage proxyMessage = new ProxyMessage();
        byte type = in.readByte();
        long sn = in.readLong();

        proxyMessage.setSerialNumber(sn);

        proxyMessage.setType(type);

        byte uriLength = in.readByte();
        byte[] uriBytes = new byte[uriLength];
        in.readBytes(uriBytes);
        proxyMessage.setUri(new String(uriBytes));

        byte[] data = new byte[frameLength - TYPE_SIZE - SERIAL_NUMBER_SIZE - URI_LENGTH_SIZE - uriLength];
        in.readBytes(data);

        // 解压缩
//        byte[] newData = Compress.gzipDeCompress(data);

        proxyMessage.setData(data);
//        proxyMessage.setData(newData);

        in.release();

        return proxyMessage;
    }
}

//
//public class ProxyMessageEncoder extends MessageToByteEncoder<ProxyMessage> {
//
//    private static final int TYPE_SIZE = 1;
//
//    private static final int SERIAL_NUMBER_SIZE = 8;
//
//    private static final int URI_LENGTH_SIZE = 1;
//
//    @Override
//    protected void encode(ChannelHandlerContext ctx, ProxyMessage msg, ByteBuf out) throws Exception {
//
//        int bodyLength = TYPE_SIZE + SERIAL_NUMBER_SIZE + URI_LENGTH_SIZE;
//
//        byte[] uriBytes = null;
//
//        if (msg.getUri() != null) {
//            uriBytes = msg.getUri().getBytes();
//            bodyLength += uriBytes.length;
//        }
//
//        if (msg.getData() != null) {
//            bodyLength += msg.getData().length;
//        }
//
//        // write the total packet length but without length field's length.
//        out.writeInt(bodyLength);
//
//        out.writeByte(msg.getType());
//        out.writeLong(msg.getSerialNumber());
//
//        if (uriBytes != null) {
//            out.writeByte((byte) uriBytes.length);
//            out.writeBytes(uriBytes);
//        } else {
//            out.writeByte((byte) 0x00);
//        }
//
//        if (msg.getData() != null) {
//            out.writeBytes(msg.getData());
//        }
//    }
//}