package com.johnny.udpserver.udp;

import com.johnny.udpserver.udp.cache.MsgCache;
import com.johnny.udpserver.udp.cache.ServiceMessageCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

public class MessageCallService {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    public synchronized void read(String msg, UdpClientSocket client, Long index) {
        //LogAPI.GetInstance().Info(index + "监测数据:" +msg);
        if (!checkMsg(msg)) {
            logger.info("消息格式不正确={}", msg);
            return;
        }
        int cmd = getCommand(msg);
        //logger.info("cmd={}",cmd);
        switch (cmd) {
            case Code.MAC_HEART_BEAT:
                //0x88
                refreshMacStatus();
                break;
            case Code.GET_MAC_VERSION:
                //0x71
                getMacVersion(msg);
                break;
            case Code.MAC_CRC_CHECK:
                //0x73
                sendMacCRCCheck(msg);
                break;
            case Code.MAC_FIRMWARE_ENCRYPTION:
                //0x74
                sendMacFirmwareEncryption(msg);
                break;
            case Code.MAC_FIRMWARE_UPGRADE:
                //0x72
                if(getReady(msg)){
                    logger.info("硬件回复ready,接收到的数据"+msg);
                }else{
                    logger.info("硬件回复error,接收到的数据:"+msg);
                }
                sendMacFirmwareUpgrade(msg);
                break;
            case Code.MAC_FIRMWARE_UPGRADE_END:
                //0x76
                sendMacFirmwareUpgradeEnd(msg);
                break;
            case Code.MAC_FIRMWARE_UPGRADE_START:
                //0x75
                sendMacFirmwareUpgradeStart(msg);
                break;
            case Code.MAC_READY_SHAKE:
                //0x7F
                getMacReadyShake(msg);
                break;
            case Code.MAC_RESET_SHAKE:
                //0x70
                if(MsgCache.machineCodeTo70.equals(getMacCode(msg))){
                    logger.info("监测到指定机器码"+MsgCache.machineCodeTo70+"的70指令,即将反馈进入烧写模式");
                    sendMacResetShake(msg, client);
                }
                //sendMacResetShake(msg, client);
                logger.info("屏蔽非指定机器码"+getMacCode(msg)+"进入烧写模式");
                break;
            case Code.MAC_TO_WORK:
                //0x71
                sendMacToWork(msg);
                break;
            case Code.MAC_CODE_MODIFY:
                //0x79
                sendMacCodeModify(msg);
                break;
            default:
                logger.info("未处理的数据={}", msg);
                break;
        }
    }
    //根据机器码和酒店ID,更新硬件端口和IP
    private void refreshMacStatus() {

    }

    private void sendMacCodeModify(String msg) {

    }

    private void sendMacToWork(String msg) {

    }

    private void sendMacResetShake(String msg, UdpClientSocket client) {
        //获取设备机器码
        String machineCode = getMacCode(msg);
        String ip = ServiceMessageCache.getIpByMac(machineCode);
        int port = ServiceMessageCache.getPort(Long.parseLong(machineCode));
        //接收到主机复位重启的0x70指令
        MessageSender.sendMacResetShake(machineCode,ip,port);


    }

    private void getMacReadyShake(String msg) {

    }

    private void sendMacFirmwareUpgradeStart(String msg) {

    }

    private void sendMacFirmwareUpgradeEnd(String msg) {

    }

    private void sendMacFirmwareUpgrade(String msg) {
        String seq = getSeq(msg);
        if(getReady(msg)){
            MsgCache.sendPCCurrentIndex = 1;
        }else{
            logger.info("硬件未回复ready,接收到的数据:"+msg);
        }
        if("0".equals(seq)){
            //当接收到第一条72数据时开启发送线程
            String machineCode = getMacCode(msg);
            String ip = ServiceMessageCache.getIpByMac(machineCode);
            int port = ServiceMessageCache.getPort(Long.parseLong(machineCode));
            logger.info("接收到第一条72返回,开启线程");
            new LogicMsgPCSenderThread(msg,ip,port,machineCode).start();
        }
    }

    private void sendMacCRCCheck(String msg) {

    }

    private void sendMacFirmwareEncryption(String msg) {

    }

    private void getMacVersion(String msg) {

    }


    private boolean checkMsg(String msg) {
        if (msg.length() < 50) {
            return false;
        }
        String txpark = msg.substring(4, 16);
        return Config.UDP_HEADER_CODE.equals(txpark);
    }

    /**
     * 17-18
     * 获取指令编码
     *
     * @return //返回值为10进制数
     */
    private static int getCommand(String msg) {
        String cmd = msg.substring(34, 38);
        int intCmd = Integer.parseInt(cmd, 16);
        //LogAPI.GetInstance().Info("获取到的16进制："+cmd+"  10进制："+intCmd);
        return intCmd;
    }

    /**
     * 20-23
     * 获取机器码
     */
    private static String getMacCode(String msg) {
        String code = msg.substring(40, 48);
        return Long.parseLong(code, 16) + "";
    }

    /**
     * 10
     * 判断报文号
     */
    private static String getSeq(String msg) {
        String code = msg.substring(20, 24);
        return Long.parseLong(code, 16) + "";
    }

    /**
     * 判断是否回复成功
     * @param msg
     * @return
     */
    public static boolean getReady(String msg){
        String readyMsg = msg.substring(50,60);
        if("5245414459".equals(readyMsg)){
            return true;
        }
        return false;
    }

    private static String getMsgContent(String msg) {
        return msg.substring(50, msg.length() - 4);
    }
}

/**
 * 主动发送固件升级,判断RCU主机是否在规定时间内回复  2019-12-25
 *
 * @author Administrator Johnny
 */
class LogicMsgPCSenderThread extends Thread {
    private Logger logger = LoggerFactory.getLogger(this.getClass());
    private String msg;
    private String machineCode;
    private String machineIp;
    private int machinePort;

    public LogicMsgPCSenderThread(String msg,String machineIp,int machinePort,String machineCode) {
        this.msg = msg;
        this.machineIp = machineIp;
        this.machinePort = machinePort;
        this.machineCode = machineCode;
    }


    /**
     * 判断RCU回复 每50毫秒监测一次，监测到之前持续1秒钟
     *
     * @return
     */
    private static boolean checkRCUback() {
        int waitCount = 0;
        while (true) {
            if (waitCount == 100) return false;
            if (MsgCache.sendPCCurrentIndex == 1) {
                return true;
            }
            MsgCache.upMsgTime = new Date().getTime();
            MsgCache.ThreadSleep(20);//每1000毫秒监测是否恢复信息啦
            waitCount++;
        }
    }

    public void run() {
        Long t1 = System.currentTimeMillis();
        int sendErrorCount = 0;
        //从第二条72数据开始发
        for(int i=1;i<ServiceMessageCache.firmwareDataList.size();i++){
            if(MsgCache.stopFirmwareUpgrade==1){
                break;
            }
            String s = ServiceMessageCache.firmwareDataList.get(i);
            while(true){
                if(sendErrorCount>3){
                    //重发四次无响应
                    return;
                }
                //发送第二条固件升级数据
                MessageSender.sendLogicTableRowDataPC(i,machineCode,machineIp,machinePort,s);
                MsgCache.sendPCCurrentIndex=0;
                MsgCache.sendSuccessCount++;
                if (checkRCUback()) {
                    break;
                }
                sendErrorCount++;
            }
        }
        //发送结束指令
        try{
            MessageSender.sendFirmwareInfoToMac(machineCode,machineIp,machinePort);
            Thread.sleep(300);
            MessageSender.sendMacCrcCheck(machineCode,machineIp,machinePort);
            Thread.sleep(300);
            MessageSender.sendMacFirmwareEncryption(machineCode,machineIp,machinePort);
            Thread.sleep(300);
            MessageSender.sendMacFirmwareUpgradeEnd(machineCode,machineIp,machinePort);
            Thread.sleep(300);
        }catch(Exception e){
            logger.error(e.getMessage());
        }
        MsgCache.clearSend();
        MsgCache.successUpgrade = 1;
        Long t2 = System.currentTimeMillis();
        logger.info("总耗时={}",t2-t1);
    }
}
