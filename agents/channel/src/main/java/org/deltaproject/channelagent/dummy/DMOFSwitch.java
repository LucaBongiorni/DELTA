package org.deltaproject.channelagent.dummy;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.deltaproject.channelagent.core.Utils;
import org.deltaproject.channelagent.fuzz.SeedPackets;
import org.projectfloodlight.openflow.exceptions.OFParseError;
import org.projectfloodlight.openflow.protocol.*;
import org.projectfloodlight.openflow.protocol.errormsg.OFErrorMsgs;
import org.projectfloodlight.openflow.protocol.ver13.OFStatsRequestFlagsSerializerVer13;
import org.projectfloodlight.openflow.types.OFHelloElement;
import org.projectfloodlight.openflow.types.U16;

import com.google.common.primitives.Longs;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import org.projectfloodlight.openflow.types.U64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DMOFSwitch extends Thread {
    private static final Logger log = LoggerFactory.getLogger(DMOFSwitch.class);

    public static final int HANDSHAKE_DEFAULT = 0;
    public static final int HANDSHAKE_NO_HELLO = 1;

    public static final int MINIMUM_LENGTH = 8;

    private Socket socket;
    private InputStream in;
    private OutputStream out;

    /* for target controller */
    private String IP = "";
    private int PORT = 0;

    /* for OF message */
    private OFFactory factory;

    private OFMessageReader<OFMessage> reader;

    private SeedPackets seedpkts;

    private OFMessage res;

    private int testHandShakeType;

    private long requestXid = 0xeeeeeeaal;
    private boolean handshaked = false;
    private boolean synack = false;

    private OFFlowAdd backupFlowAdd;

    public DMOFSwitch() {
        res = null;
    }

    public void setTestHandShakeType(int type) {
        this.testHandShakeType = type;
    }

    public void setSeed(SeedPackets seed) {
        seedpkts = seed;
    }

    public OFFactory getFactory() {
        return this.factory;
    }

    public void connectTargetController(String cip, String ofPort) {
        this.IP = cip;
        this.PORT = Integer.parseInt(ofPort);
        try {
            socket = new Socket(IP, PORT);
            socket.setReuseAddress(true);

            in = socket.getInputStream();
            out = socket.getOutputStream();
        } catch (UnknownHostException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public void setOFFactory(byte ofVersion) {
        if (ofVersion == 1)
            factory = OFFactories.getFactory(OFVersion.OF_10);
        else
            factory = OFFactories.getFactory(OFVersion.OF_13);

        reader = factory.getReader();
    }

    public byte[] readBytes() throws IOException {
        // Again, probably better to store these objects references in the
        // support class
        InputStream in = socket.getInputStream();
        DataInputStream dis = new DataInputStream(in);

        int len = dis.readInt();
        byte[] data = new byte[len];
        if (len > 0) {
            dis.readFully(data);
        }
        return data;
    }


    public void printError(OFMessage msg) {
        System.out.println(msg.toString());
    }

    public void sendMsg(OFMessage msg, int len) {
        ByteBuf buf;

        if (len == -1) {
            buf = PooledByteBufAllocator.DEFAULT.directBuffer(1024);
        } else {
            buf = PooledByteBufAllocator.DEFAULT.directBuffer(len);
        }

        msg.writeTo(buf);

        int length = buf.readableBytes();
        byte[] bytes = new byte[length];
        buf.getBytes(buf.readerIndex(), bytes);

        try {
            this.out.write(bytes, 0, length);
        } catch (IOException e) {
            // TODO Auto-gaenerated catch block
            e.printStackTrace();
        }

        buf.clear();
        buf.release();
    }

    public void setReqXid(long xid) {
        requestXid = xid;
    }

    public OFMessage getResponse() {
        return res;
    }

    public void sendRawMsg(byte[] msg) {
        try {
            this.out.write(msg, 0, msg.length);
        } catch (IOException e) {
            // TODO Auto-gaenerated catch block
            e.printStackTrace();
        }
    }

    public OFFlowAdd getBackupFlowAdd() {
        return backupFlowAdd;
    }


    public void sendError() throws OFParseError {
        long r_xid = 0xeeeeeeeel;

        OFErrorMsgs msg = factory.errorMsgs();
        OFFeaturesReply.Builder frb = factory.buildFeaturesReply();
        OFFeaturesReply fr = frb.build();

        return;
    }

    public ByteBuf sendFlowRemoved() throws OFParseError {
        OFFactory factory = OFFactories.getFactory(OFVersion.OF_10);
        long r_xid = 0xeeeeeeeel;

        OFFlowRemoved.Builder fab = factory.buildFlowRemoved();
        fab.setXid(r_xid);
        OFFlowRemoved hello = fab.build();

        ByteBuf buf = null;
        buf = PooledByteBufAllocator.DEFAULT.directBuffer(88);
        hello.writeTo(buf);

        byte[] bytes;
        int length = buf.readableBytes();
        bytes = new byte[length];
        buf.getBytes(buf.readerIndex(), bytes);

        try {
            this.out.write(bytes, 0, length);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return buf;
    }

    public ByteBuf sendPortStatus() throws OFParseError {
        OFFactory factory = OFFactories.getFactory(OFVersion.OF_10);
        long r_xid = 0xeeeeeeeel;

        OFPortStatus.Builder fab = factory.buildPortStatus();
        fab.setXid(r_xid);
        OFPortStatus hello = fab.build();

        ByteBuf buf = null;
        buf = PooledByteBufAllocator.DEFAULT.directBuffer(64);
        hello.writeTo(buf);

        byte[] bytes;
        int length = buf.readableBytes();
        bytes = new byte[length];
        buf.getBytes(buf.readerIndex(), bytes);

        try {
            this.out.write(bytes, 0, length);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return buf;
    }

    public boolean getHandshaked() {
        return this.handshaked;
    }


    /* OF HandShake */
    public void sendHello(long xid) throws OFParseError {
        if (testHandShakeType == DMOFSwitch.HANDSHAKE_NO_HELLO) {
            this.sendFeatureReply(requestXid);
            return;
        }

        if (factory.getVersion() == OFVersion.OF_13) {
            byte[] msg = Utils.hexStringToByteArray(DMDataOF13.HELLO);
            sendRawMsg(msg);
            return;
        }

        OFHello.Builder builder = factory.buildHello();
        builder.setXid(0xb0);

        sendMsg(builder.build(), -1);
    }

    public void sendFeatureReply(long xid) throws OFParseError {
        byte[] msg;

        if (factory.getVersion() == OFVersion.OF_13) {
            msg = Utils.hexStringToByteArray(DMDataOF13.FEATURE_REPLY);
        } else {
            msg = Utils.hexStringToByteArray(DMDataOF10.FEATURE_REPLY);
        }

        byte[] xidbytes = Longs.toByteArray(xid);
        System.arraycopy(xidbytes, 4, msg, 4, 4);

        sendRawMsg(msg);
    }

    public void sendGetConfigReply(long xid) {
        byte[] msg;

        if (factory.getVersion() == OFVersion.OF_13) {
            msg = Utils.hexStringToByteArray(DMDataOF13.GET_CONFIG_REPLY);
        } else {
            msg = Utils.hexStringToByteArray(DMDataOF10.GET_CONFIG_REPLY);
        }

        byte[] xidbytes = Longs.toByteArray(xid);
        System.arraycopy(xidbytes, 4, msg, 4, 4);

        sendRawMsg(msg);
    }

    public void sendStatReply(OFMessage input, long xid) {
        byte[] msg = null;

        if (factory.getVersion() == OFVersion.OF_13) {
            OFStatsRequest req = (OFStatsRequest) input;
            switch (req.getStatsType()) {
                case FLOW:
                    msg = Utils.hexStringToByteArray(DMDataOF13.MULTIPART_FLOW);
                    break;
                case AGGREGATE:
                    break;
                case TABLE:
                    break;
                case PORT:
                    msg = Utils.hexStringToByteArray(DMDataOF13.MULTIPART_PORT_STATS);
                    break;
                case QUEUE:
                    break;
                case EXPERIMENTER:
                    break;
                case GROUP:
                    break;
                case GROUP_DESC:
                    break;
                case GROUP_FEATURES:
                    break;
                case METER:
                    msg = Utils.hexStringToByteArray(DMDataOF13.MULTIPART_METER);
                    break;
                case METER_CONFIG:
                    break;
                case METER_FEATURES:
                    break;
                case TABLE_FEATURES:
                    break;
                case PORT_DESC:
                    log.info("OF PORT_DESC");
                    msg = Utils.hexStringToByteArray(DMDataOF13.MULTIPART_PORT_DESC);
                    break;
                case TABLE_DESC:
                    break;
                case QUEUE_DESC:
                    break;
                case FLOW_MONITOR:
                    break;
                case DESC:
                    msg = Utils.hexStringToByteArray(DMDataOF13.MULTIPART_DESC);
                    break;
            }
        } else {
            msg = Utils.hexStringToByteArray(DMDataOF10.STATS_REPLY);
        }

        byte[] xidbytes = Longs.toByteArray(xid);
        System.arraycopy(xidbytes, 4, msg, 4, 4);

        sendRawMsg(msg);
    }

    public void sendRoleRes(long xid) {
        OFRoleReply.Builder builder = factory.buildRoleReply();
        builder.setXid(xid);
        builder.setGenerationId(U64.of(0));
        builder.setRole(OFControllerRole.ROLE_MASTER);

        OFRoleReply msg = builder.build();
        sendMsg(msg, -1);
    }

    public void sendBarrierRes(long xid) {
        OFBarrierReply.Builder builder = factory.buildBarrierReply();
        builder.setXid(xid);
        OFBarrierReply msg = builder.build();
        sendMsg(msg, 8);
    }

    public void sendEchoReply(long xid) {
        OFEchoReply.Builder builder = factory.buildEchoReply();
        builder.setXid(xid);
        OFEchoReply msg = builder.build();
        sendMsg(msg, 8);
    }

    public void sendExperimenter(long xid) {
        byte[] msg = DMDataOF10.hexStringToByteArray(DMDataOF10.experimenter);
        byte[] xidbytes = Longs.toByteArray(xid);
        System.arraycopy(xidbytes, 4, msg, 4, 4);

        if (factory.getVersion() == OFVersion.OF_13)
            msg[0] = 0x04;

        sendRawMsg(msg);
    }

    public boolean parseOFMsg(byte[] recv, int len) throws OFParseError {
        // for OpenFlow Message
        byte[] rawMsg = new byte[len];
        System.arraycopy(recv, 0, rawMsg, 0, len);
        ByteBuf bb = Unpooled.copiedBuffer(rawMsg);

        int totalLen = bb.readableBytes();
        int offset = bb.readerIndex();

        while (offset < totalLen) {
            bb.readerIndex(offset);

            byte version = bb.readByte();
            bb.readByte();
            int length = U16.f(bb.readShort());
            bb.readerIndex(offset);

            if (length < MINIMUM_LENGTH)
                throw new OFParseError("Wrong length: Expected to be >= " + MINIMUM_LENGTH + ", was: " + length);

            try {
                OFMessage message = reader.readFrom(bb);

                long xid = message.getXid();

                if (message.getType() == OFType.HELLO) {
                    log.info("[CA] receive HELLO msg");
                } else if (message.getType() == OFType.FEATURES_REQUEST) {
                    log.info("[CA] receive FEATURES_REQUEST msg");
                    sendFeatureReply(xid);
                } else if (message.getType() == OFType.GET_CONFIG_REQUEST) {
                    log.info("[CA] receive GET_CONFIG_REQUEST msg");
                    sendGetConfigReply(xid);
                } else if (message.getType() == OFType.BARRIER_REQUEST) {
                    sendBarrierRes(xid);
                } else if (message.getType() == OFType.STATS_REQUEST) {
                    log.info("[CA] receive STATS_REQ msg");
                    sendStatReply(message, xid);
                    handshaked = true;
                } else if (message.getType() == OFType.EXPERIMENTER) {
                    sendExperimenter(xid);
                } else if (message.getType() == OFType.ECHO_REQUEST) {
                    log.info("[CA] receive ECHO_REQUEST msg");
                    sendEchoReply(xid);
                } else if (message.getType() == OFType.ERROR) {
                    printError(message);
                } else if (message.getType() == OFType.FLOW_MOD) {
                    OFFlowMod fm = (OFFlowMod) message;
                    if (fm.getCommand() == OFFlowModCommand.ADD) {
                        OFFlowAdd fa = (OFFlowAdd) fm;
                        if (fa.getPriority() == 555) {
                            backupFlowAdd = fa;
                            log.info("[CA] catch unflagged msg");
                        }
                    }
                } else if (message.getType() == OFType.ROLE_REQUEST) {
                    sendRoleRes(xid);
                }

                if (xid == this.requestXid) {
                    res = message;
                }

            } catch (OFParseError e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

            offset += length;
        }

        bb.clear();
        return true;
    }

    @Override
    public void run() {
        // TODO Auto-generated method stub
        byte[] recv;
        int readlen;
        boolean synack = false;

        try {
            while (true) {
                recv = new byte[2048];
                if ((readlen = in.read(recv, 0, recv.length)) != -1) {
                    if (!synack) {
                        log.info(readlen + ":" + recv.toString());
                        synack = true;
                    } else {
                        /* after hello */
                        parseOFMsg(recv, readlen);
                    }
                } else
                    break; // end of connection
            }
        } catch (Exception e) {
            // if any error occurs
            // e.printStackTrace();

            if (in != null)
                try {
                    in.close();
                } catch (IOException e1) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
        }
    }
}