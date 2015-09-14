package com.chooshine.fep.fas.realtimecom;

import java.io.*;
import java.util.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.Calendar;

import com.chooshine.fep.ConstAndTypeDefine.FileLogger;
import com.chooshine.fep.communicate.utils;

/**
 * <p>Title: ʵʱͨѶģ��</p>
 *
 * <p>Description: �ṩ�ӿ������ݲɼ�ģ����ã������ͨѶ�����ͨѶ���������ݵ��շ�����</p>
 *
 * <p>Copyright: </p>
 *
 * <p>Company: </p>
 *
 * @author 
 * @version 
 */
public class RealTimeCommunication {
  private Selector selector;
  private SocketChannel client;
  private List <RealTimeCommunicationInfo>RealTimeList; //�·����������ݵĶ�Ӧ��ϵ
  private List <SendInfo>SendInfoList; //�����·����ݵĶ���

  private String HostName = ""; //���ӵ�ͨѶ�����IP
  private int Port = 0; //����ͨѶ����Ķ˿�
  private byte[] data = null; //�������ݵ�byte����
  private int ReConnectTimes = 0; //��������
  private int Sequence = 1; //��ˮ��
  private boolean FormData = false; //Ҫ���͵������Ƿ������ɵı�־
  private boolean AckBack = true; //ȷ���ź��Ƿ񷵻صı�־
  public boolean ConnectFlag = false; //����ǰ�û��Ƿ�ɹ��ı�־�������Ƿ���Ҫ�ϲ����´����¶���
  private boolean FStop = false;
  private FileLogger fl = null;
  private MonitorThread mh = null;
  private RealTimeCommunicationInfoListThread rh = null;
  private boolean GetTerminalOnLineStatus = false;
  private boolean SetTerminalOnLineStatus = false;
  private boolean GetTerminalOnLineStatusBack = false;
  private boolean BuildCertainTerminalInfo = false;
  private String TerminalAddr = "";
  private List <TerminalOnLineStatus>TerminalStatusList = new LinkedList<TerminalOnLineStatus>();
  private Calendar LastTime = null;
  public RealTimeCommunication() {
    try {
      jbInit();
    }
    catch (Exception ex) {
      ex.printStackTrace();
    }
  }

  public RealTimeCommunication(String hostName, int port) { //�ع����캯��������Ip�Ͷ˿�
    fl = new FileLogger("RealTimeCommunication");
    ReConnectTimes = 1;
    HostName = hostName;
    Port = port;
    LastTime = Calendar.getInstance();
  }

  public void Connect() {
    if (ConnectFlag) { //�����ǰ���ӻ�������Ҫ�ȶϿ������������ӵ�¼
      try {
        DisConnect();
      }
      catch (Exception ex) {
      }
    }
    FStop = false;
    ConnectFlag = true;
    InitSocket(); //��ʼ��socket����
    RealTimeList = Collections.synchronizedList(new LinkedList<RealTimeCommunicationInfo>()); //������Ӧ����
    SendInfoList = Collections.synchronizedList(new LinkedList<SendInfo>()); //������Ӧ����
    mh = new MonitorThread(); //���������߳�
    mh.start(); //�����߳�
    rh = new RealTimeCommunicationInfoListThread(); //����ʵʱ���ݶ��е�ά���߳�
    rh.start(); //�����߳�
    while (!ConnectFlag) {
      try {
        Thread.sleep(1);
      }
      catch (InterruptedException ex1) {
      }
    }
  }

  public void DisConnect() {
    try {
      try {
        client.socket().setSoLinger(true, 0);
      }
      catch (SocketException ex) {
      }
    }
    catch (Exception ex1) {
    }
    BuildDisConnectAndSend();
    RealTimeList = Collections.synchronizedList(new LinkedList<RealTimeCommunicationInfo>()); //������Ӧ����
    SendInfoList = Collections.synchronizedList(new LinkedList<SendInfo>()); //������Ӧ����
    FStop = true;
    ConnectFlag = false;
    try {
      Thread.sleep(100);
    }
    catch (InterruptedException ex2) {
    }
  }

  private void InitSocket() {
    try {
      selector = Selector.open();
      client = SocketChannel.open();
      InetSocketAddress isa = new InetSocketAddress(HostName, Port); //socket���ӵĲ�����ʼ��
      client.configureBlocking(false); //����socket�Ƿ��������ͨѶ
      client.connect(isa); //������������
      client.register(selector, SelectionKey.OP_CONNECT); //�������ڴ�����������ʱ��
    }
    catch (Exception ex) {
    }
  }

  private void AddCallInfoToList(int AppID, String TerminalAddr, int GYH,
                                 int CommandID,
                                 int ReadCount) {
    switch (CommandID) {
      case 10: {
        RealTimeCommunicationInfo rtci = new RealTimeCommunicationInfo();
        rtci.AppID = AppID;
        rtci.TerminalAddr = TerminalAddr.trim();
        rtci.BackFlag = false;
        rtci.GYH = GYH;
        rtci.AddInTime = Calendar.getInstance();
        RealTimeList.add(rtci); //��������ֻ��Ҫ����Ӧ��ID���ն��߼���ַ���ȶ�
        break;
      }
      case 20: {
        for (int i = 0; i < ReadCount; i++) {
          RealTimeCommunicationInfo rtci = new RealTimeCommunicationInfo();
          rtci.AppID = AppID;
          rtci.TerminalAddr = TerminalAddr.trim();
          rtci.BackFlag = false;
          rtci.GYH = GYH;
          rtci.AddInTime = Calendar.getInstance();
          RealTimeList.add(rtci); //��������ֻ��Ҫ����Ӧ��ID���ն��߼���ַ���ȶ�
        }
        break;
      }
      case 30: {
        for (int i = 0; i < ReadCount; i++) {
          RealTimeCommunicationInfo rtci = new RealTimeCommunicationInfo();
          rtci.AppID = AppID;
          rtci.TerminalAddr = TerminalAddr.trim();
          rtci.BackFlag = false;
          rtci.GYH = GYH;
          rtci.AddInTime = Calendar.getInstance();
          RealTimeList.add(rtci); //��������ֻ��Ҫ����Ӧ��ID���ն��߼���ַ���ȶ�
        }
        break;
      }
    }
  }

  private void BuildConnectAndSend(SelectionKey key) {
    ByteBuffer bBuffer = ByteBuffer.allocate(5000);
    FormData = false; //���ͨѶ���ݿ�ʼ�����ñ�־Ϊ��Ч
    data = null;
    byte[] MsgLength = new byte[4];
    data = new byte[12];
    MsgLength = utils.int2byte(12);
    //��Ϣͷ���ֵ���Ϣ�ܳ���
    data[0] = MsgLength[0];
    data[1] = MsgLength[1];
    data[2] = MsgLength[2];
    data[3] = MsgLength[3];
    //��Ϣͷ���ֵ���Ϣ���ʹ���
    data[4] = 0;
    data[5] = 0;
    data[6] = 0;
    data[7] = 13;
    //��Ϣͷ���ֵ���Ϣ���
    byte[] MsgSequence = new byte[4];
    MsgSequence = utils.int2byte(Sequence);
    data[8] = MsgSequence[0];
    data[9] = MsgSequence[1];
    data[10] = MsgSequence[2];
    data[11] = MsgSequence[3];
    FormData = true;
    Sequence = Sequence + 1;
    if ( (data != null) && (FormData)) { //�������ݵ������������������ݲ�����Ϣ������
      SocketChannel keyChannel = (SocketChannel) key.channel(); //ͨ����Ӧ��ͨ����д������
      bBuffer.clear();
      bBuffer.put(data);
      bBuffer.flip();
      try {
        keyChannel.write(bBuffer); //�������������쳣����˵���޷����ӳɹ�
      }
      catch (IOException ex) {
      }
      bBuffer.clear();
      //data = null; //���Ҫ���͵����ݣ���֤����Ҫ���͵����ݲ��ᶪʧ
    }
    return;
  }

  private void BuildDisConnectAndSend() {
    FormData = false; //���ͨѶ���ݿ�ʼ�����ñ�־Ϊ��Ч
    ByteBuffer bBuffer = ByteBuffer.allocate(5000);
    data = null;
    byte[] MsgLength = new byte[4];
    data = new byte[12];
    MsgLength = utils.int2byte(12);
    //��Ϣͷ���ֵ���Ϣ�ܳ���
    data[0] = MsgLength[0];
    data[1] = MsgLength[1];
    data[2] = MsgLength[2];
    data[3] = MsgLength[3];
    //��Ϣͷ���ֵ���Ϣ���ʹ���
    data[4] = 0;
    data[5] = 0;
    data[6] = 0;
    data[7] = 14;
    //��Ϣͷ���ֵ���Ϣ���
    byte[] MsgSequence = new byte[4];
    MsgSequence = utils.int2byte(Sequence);
    data[8] = MsgSequence[0];
    data[9] = MsgSequence[1];
    data[10] = MsgSequence[2];
    data[11] = MsgSequence[3];
    FormData = true;
    Sequence = Sequence + 1;
    if ( (data != null) && (FormData)) { //�������ݵ������������������ݲ�����Ϣ������
      bBuffer.clear();
      bBuffer.put(data);
      bBuffer.flip();
      try {
        client.write(bBuffer); //�������������쳣����˵���޷����ӳɹ�
      }
      catch (Exception ex) {
      }
      try {
        client.socket().close();
        client.close();
        selector.close();
        FStop = true;
        ConnectFlag = false;
        //utils.PrintDebugMessage("ʵʱͨѶ�����Ͽ���ͨѶ���������", "D");
      }
      catch (Exception ex1) {
      }
      bBuffer.clear();
      //data = null; //���Ҫ���͵����ݣ���֤����Ҫ���͵����ݲ��ᶪʧ
    }
    return;
  }

//���ݴ�����������Ҫ���͵����ݷ��͵�ͨѶ����
  private void BuildSendFrameAndSend(int AppID, char[] TerminalAddr, int GYH,
                                     int TXFS, int RWH, int RWDS, int YCDS,
                                     char[] GNM, int SJQCD, char[] SJQNR,
                                     SelectionKey key, boolean SMS,
                                     boolean SelfDefined, int Priority,
                                     int ArithmeticNo) throws Exception {
    ByteBuffer bBuffer = ByteBuffer.allocate(5000);
    if (SMS) { //���Ͷ��Ŵ���
      FormData = false; //���ͨѶ���ݿ�ʼ�����ñ�־Ϊ��Ч
      data = null;
      byte[] MsgLength = new byte[4];
      if (GYH == 10) {
        SJQCD = SJQCD * 2;
        data = new byte[12 + 23 + SJQCD];
        MsgLength = utils.int2byte(12 + 23 + SJQCD);
      }
      else {
        data = new byte[12 + 21 + SJQNR.length];
        MsgLength = utils.int2byte(12 + 21 + SJQNR.length);
      }
      //��Ϣͷ���ֵ���Ϣ�ܳ���
      data[0] = MsgLength[0];
      data[1] = MsgLength[1];
      data[2] = MsgLength[2];
      data[3] = MsgLength[3];
      //��Ϣͷ���ֵ���Ϣ���ʹ���
      data[4] = 0;
      data[5] = 0;
      data[6] = 0;
      data[7] = 10;
      //��Ϣͷ���ֵ���Ϣ���
      byte[] MsgSequence = new byte[4];
      MsgSequence = utils.int2byte(Sequence);
      data[8] = MsgSequence[0];
      data[9] = MsgSequence[1];
      data[10] = MsgSequence[2];
      data[11] = MsgSequence[3];
      //��Ϣ�岿�ֵ�Ŀ���ֻ�����
      byte[] bTemp = new byte[20];
      byte[] bMBHM = new String(TerminalAddr).getBytes();
      for (int i = 0; i < bMBHM.length; i++) {
        bTemp[i] = bMBHM[i];
      }
      for (int i = 0; i < 15; i++) {
        data[12 + i] = bTemp[i];
      }
      //��Ϣ�岿�ֵ���Ϣ����
      bTemp = new byte[2];
      byte[] bTXLX = new byte[2];
      if (GYH < 15) { //ֻ��С��15��������Ҫ���⴦��������������ͳһ����ʵ��
        bTXLX[1] = 0;
        bTXLX[0] = (byte) GYH;
      }
      else {
        bTXLX = utils.str2bytes(Integer.toHexString(GYH));
      }
      for (int i = 0; i < bTXLX.length; i++) {
        bTemp[i] = bTXLX[i];
      }
      data[27] = bTemp[1]; //���������ȵ��ֶ���Ҫ���ߵ�����
      data[28] = bTemp[0];
      //��Ϣ�岿�ֵ���Ϣ����
      bTemp = new byte[2];
      byte[] bSJQCD = new byte[2];
      if (SJQCD < 15) { //ֻ��С��15��������Ҫ���⴦��������������ͳһ����ʵ��
        bSJQCD[1] = 0;
        bSJQCD[0] = (byte) SJQCD;
      }
      else {
        bSJQCD = utils.str2bytes(Integer.toHexString(SJQCD));
      }
      for (int i = 0; i < bSJQCD.length; i++) {
        bTemp[i] = bSJQCD[i];
      }
      if (SJQCD < 256) {
        data[29] = bTemp[1]; //���������ȵ��ֶ���Ҫ���ߵ�����
        data[30] = bTemp[0];
      }
      else {
        data[29] = bTemp[0];
        data[30] = bTemp[1];
      }
      //��Ϣ�岿�ֵ���Ϣ����
      byte[] bSJQNR = new byte[SJQCD];
      bSJQNR = new String(SJQNR).getBytes();
      if (bSJQNR.length > SJQCD) {
        for (int i = 0; i < SJQCD; i++) {
          data[31 + i] = bSJQNR[i];
        }
      }
      else {
        for (int i = 0; i < bSJQNR.length; i++) {
          data[31 + i] = bSJQNR[i];
        }
      }
      //��Ϣ�岿�ֵĶ���ͨ��
      data[31 + SJQCD] = (byte) TXFS;
      //��Ϣ�岿�ֵ�����ѡ��
      data[32 + SJQCD] = 1;
      FormData = true;
      Sequence = Sequence + 1;
      if ( (data != null) && (FormData)) { //�������ݵ������������������ݲ�����Ϣ������
        SocketChannel keyChannel = (SocketChannel) key.channel(); //ͨ����Ӧ��ͨ����д������
        bBuffer.clear();
        bBuffer.put(data);
        bBuffer.flip();
        keyChannel.write(bBuffer); //�������������쳣����˵���޷����ӳɹ�
        bBuffer.clear();
        AckBack = false;
      }
      return;
    }
    else if (SelfDefined) { //�����Զ�������Ĵ���
      FormData = false; //���ͨѶ���ݿ�ʼ�����ñ�־Ϊ��Ч
      data = null;
      data = new byte[12 + 30 + SJQNR.length + 1];
      byte[] MsgLength = new byte[4];
      MsgLength = utils.int2byte(12 + 30 + SJQNR.length + 1);
      data[0] = MsgLength[0];
      data[1] = MsgLength[1];
      data[2] = MsgLength[2];
      data[3] = MsgLength[3];

      data[4] = 0;
      data[5] = 0;
      data[6] = 0;
      data[7] = 12;

      byte[] MsgSequence = new byte[4];
      MsgSequence = utils.int2byte(Sequence);
      data[8] = MsgSequence[0];
      data[9] = MsgSequence[1];
      data[10] = MsgSequence[2];
      data[11] = MsgSequence[3];

      byte[] bAppID = new byte[4];
      bAppID = utils.int2byte(AppID);
      data[12] = bAppID[0];
      data[13] = bAppID[1];
      data[14] = bAppID[2];
      data[15] = bAppID[3];

      //Ӧ��ID
      byte[] bTemp = new byte[20];
      byte[] bZDLJDZ = new String(TerminalAddr).getBytes();
      for (int i = 0; i < bZDLJDZ.length; i++) {
        bTemp[i] = bZDLJDZ[i];
      }
      for (int i = 0; i < 20; i++) {
        data[16 + i] = bTemp[i];
      }
      //�ն��߼���ַ����Ҫ��ȫ20λ
      byte[] bTXCS = new byte[1];
      if (TXFS < 15) { //ֻ��С��15��������Ҫ���⴦��������������ͳһ����ʵ��
        bTXCS[0] = (byte) TXFS;
      }
      else {
        bTXCS = utils.str2bytes(Integer.toHexString(TXFS));
      }

      data[36] = bTXCS[0];
      //ͨѶ��ʽ�����ն˵�ǰͨѶ����
      data[37] = 1;

      //�����Զ���������������
      byte[] bMLXH = new byte[1];
      if (RWH < 15) { //ֻ��С��15��������Ҫ���⴦��������������ͳһ����ʵ��
        bMLXH[0] = (byte) RWH;
      }
      else {
        bMLXH = utils.str2bytes(Integer.toHexString(RWH));
      }
      data[38] = bMLXH[0];
      //��Լ��
      byte[] bGYH = new byte[1];
      if (GYH < 15) { //ֻ��С��15��������Ҫ���⴦��������������ͳһ����ʵ��
        bGYH[0] = (byte) GYH;
      }
      else {
        bGYH = utils.str2bytes(Integer.toHexString(GYH));
      }
      data[39] = bGYH[0];
      //����������
      bTemp = new byte[2];
      byte[] bSJQCD = new byte[2];
      if (SJQCD < 15) { //ֻ��С��15��������Ҫ���⴦��������������ͳһ����ʵ��
        bSJQCD[1] = 0;
        bSJQCD[0] = (byte) SJQCD;
      }
      else {
        bSJQCD = utils.str2bytes(Integer.toHexString(SJQCD));
      }
      for (int i = 0; i < bSJQCD.length; i++) {
        bTemp[i] = bSJQCD[i];
      }
      if (SJQCD < 256) {
        data[40] = bTemp[1]; //���������ȵ��ֶ���Ҫ���ߵ�����
        data[41] = bTemp[0];
      }
      else {
        data[40] = bTemp[0];
        data[41] = bTemp[1];
      }
      //����������
      byte[] bSJQNR = new byte[SJQCD];
      bSJQNR = new String(SJQNR).getBytes();
      for (int i = 0; i < SJQCD; i++) {
        data[42 + i] = bSJQNR[i];
      }

      byte[] bYXJ = new byte[1];
      if (Priority < 15) {
        bYXJ[0] = (byte) Priority;
      }
      else {
        bYXJ = utils.str2bytes(Integer.toHexString(Priority));
      }
      data[42 + SJQCD] = bYXJ[0]; //���ȼ�

      //��������������
      AddCallInfoToList(AppID, new String(TerminalAddr), GYH, 10, 0); //�����ն���Ϣ��AppID��������
      FormData = true;
      Sequence = Sequence + 1;
      if ( (data != null) && (FormData)) { //�������ݵ������������������ݲ�����Ϣ������
        SocketChannel keyChannel = (SocketChannel) key.channel(); //ͨ����Ӧ��ͨ����д������
        bBuffer.clear();
        bBuffer.put(data);
        bBuffer.flip();
        keyChannel.write(bBuffer); //�������������쳣����˵���޷����ӳɹ�
        bBuffer.clear();
        AckBack = false;
      }
      return;
    }
    else if (( GYH == 1000 )||( GYH == 1001 )||( GYH == 1100)){
    	FormData = false; //���ͨѶ���ݿ�ʼ�����ñ�־Ϊ��Ч
        data = null;
        data = new byte[12 + 28 + SJQNR.length];
        
        //��Ϣ����
        byte[] MsgLength = new byte[4];
        MsgLength = utils.int2byte(12 + 28 + SJQNR.length );
        data[0] = MsgLength[0];
        data[1] = MsgLength[1];
        data[2] = MsgLength[2];
        data[3] = MsgLength[3];
        
        //��Ϣ����
        if (( GYH == 1000 )||( GYH == 1100)){ //���ü��ܻ�
        	data[4] = 0;
            data[5] = 0;
            data[6] = 1;
            data[7] = 1;
        }
        else { //���ö�����
        	data[4] = 0;
            data[5] = 0;
            data[6] = 1;
            data[7] = 2;
        }

        byte[] MsgSequence = new byte[4];
        MsgSequence = utils.int2byte(Sequence);
        data[8] = MsgSequence[0];
        data[9] = MsgSequence[1];
        data[10] = MsgSequence[2];
        data[11] = MsgSequence[3];
        //Ӧ��ID
        byte[] bAppID = new byte[4];
        bAppID = utils.int2byte(AppID);
        data[12] = bAppID[0];
        data[13] = bAppID[1];
        data[14] = bAppID[2];
        data[15] = bAppID[3];
        
        //�û�����
        byte[] bTemp = new byte[20];
        byte[] bZDLJDZ = new String(TerminalAddr).getBytes();
        for (int i = 0; i < bZDLJDZ.length; i++) {
          bTemp[i] = bZDLJDZ[i];
        }
        for (int i = 0; i < 20; i++) {
          data[16 + i] = bTemp[i];
        }
        //�����ܻ�����,0x10:����;0x20:����
        if ((GYH == 1000) || (GYH == 1001)){
        	data[36] = 0;
            data[37] = 10;
        }
        else if (GYH == 1100){
        	data[36] = 0;
            data[37] = 20;
        }
        //����������
        bTemp = new byte[2];
        byte[] bSJQCD = new byte[2];
        if (SJQCD < 15) { //ֻ��С��15��������Ҫ���⴦��������������ͳһ����ʵ��
          bSJQCD[1] = 0;
          bSJQCD[0] = (byte) SJQCD;
        }
        else {
          bSJQCD = utils.str2bytes(Integer.toHexString(SJQCD));
        }
        for (int i = 0; i < bSJQCD.length; i++) {
          bTemp[i] = bSJQCD[i];
        }
        if (SJQCD < 256) {
          data[38] = bTemp[1]; //���������ȵ��ֶ���Ҫ���ߵ�����
          data[39] = bTemp[0];
        }
        else {
          data[38] = bTemp[0];
          data[39] = bTemp[1];
        }

        //��������
        byte[] bSJQNR = new byte[SJQCD];
        bSJQNR = new String(SJQNR).getBytes();
        for (int i = 0; i < SJQCD; i++) {
          data[40 + i] = bSJQNR[i];
        }

        //��������������
        AddCallInfoToList(AppID, new String(TerminalAddr), GYH, 10, RWDS); //�����ն���Ϣ��AppID��������
        FormData = true;
        Sequence = Sequence + 1;
        if ( (data != null) && (FormData)) { //�������ݵ������������������ݲ�����Ϣ������
          SocketChannel keyChannel = (SocketChannel) key.channel(); //ͨ����Ӧ��ͨ����д������
          try {
            bBuffer.clear();
            bBuffer.put(data);
            bBuffer.flip();
            keyChannel.write(bBuffer); //�������������쳣����˵���޷����ӳɹ�
            bBuffer.clear();
            AckBack = false;
            //System.out.println("ʵʱͨѶ�������Ӧ��ID��" + AppID + "���ն��߼���ַ��" +
            //     new String(TerminalAddr) + "��Լ�ţ�" + GYH + "������������" + new String(SJQNR));
          }
          catch (IOException ex) {
          }
        }
        return;
    }
    else if ( (RWH == 0) && (RWDS == 0) && (YCDS == 0)) { //ֻ������š�����������쳣������Ϊ0������Ϊ���ٲ������·�
      FormData = false; //���ͨѶ���ݿ�ʼ�����ñ�־Ϊ��Ч
      data = null;
      data = new byte[12 + 33 + SJQNR.length + 2];
      byte[] MsgLength = new byte[4];
      MsgLength = utils.int2byte(12 + 33 + SJQNR.length + 2);
      data[0] = MsgLength[0];
      data[1] = MsgLength[1];
      data[2] = MsgLength[2];
      data[3] = MsgLength[3];

      data[4] = 0;
      data[5] = 0;
      data[6] = 0;
      data[7] = 4;

      byte[] MsgSequence = new byte[4];
      MsgSequence = utils.int2byte(Sequence);
      data[8] = MsgSequence[0];
      data[9] = MsgSequence[1];
      data[10] = MsgSequence[2];
      data[11] = MsgSequence[3];

      byte[] bAppID = new byte[4];
      bAppID = utils.int2byte(AppID);
      data[12] = bAppID[0];
      data[13] = bAppID[1];
      data[14] = bAppID[2];
      data[15] = bAppID[3];

      //Ӧ��ID
      byte[] bTemp = new byte[20];
      byte[] bZDLJDZ = new String(TerminalAddr).getBytes();
      for (int i = 0; i < bZDLJDZ.length; i++) {
        bTemp[i] = bZDLJDZ[i];
      }
      for (int i = 0; i < 20; i++) {
        data[16 + i] = bTemp[i];
      }
      //�ն��߼���ַ����Ҫ��ȫ20λ
      byte[] bTXCS = new byte[1];
      if (TXFS < 15) { //ֻ��С��15��������Ҫ���⴦��������������ͳһ����ʵ��
        bTXCS[0] = (byte) TXFS;
      }
      else {
        bTXCS = utils.str2bytes(Integer.toHexString(TXFS));
      }

      data[36] = bTXCS[0];
      //ͨѶ��ʽ�����ն˵�ǰͨѶ����
      data[37] = 1;
      //����ѡ��Ĭ����Ч
      data[38] = 0;
      //���������
      data[39] = 10;
      //����������
      byte[] bGYH = new byte[1];
      bGYH = utils.str2bytes(Integer.toHexString(GYH));
      data[40] = bGYH[0];
      //��Լ��
      bTemp = new byte[2];
      byte[] bGNM = new String(GNM).getBytes();
      for (int i = 0; i < bGNM.length; i++) {
        bTemp[i] = bGNM[i];
      }
      for (int i = 0; i < 2; i++) {
        data[41 + i] = bTemp[i];
      }
      //������
      bTemp = new byte[2];
      byte[] bSJQCD = new byte[2];
      if (SJQCD < 15) { //ֻ��С��15��������Ҫ���⴦��������������ͳһ����ʵ��
        bSJQCD[1] = 0;
        bSJQCD[0] = (byte) SJQCD;
      }
      else {
        bSJQCD = utils.str2bytes(Integer.toHexString(SJQCD));
      }
      for (int i = 0; i < bSJQCD.length; i++) {
        bTemp[i] = bSJQCD[i];
      }
      if (SJQCD < 256) {
        data[43] = bTemp[1]; //���������ȵ��ֶ���Ҫ���ߵ�����
        data[44] = bTemp[0];
      }
      else {
        data[43] = bTemp[0];
        data[44] = bTemp[1];
      }
      //����������
      byte[] bSJQNR = new byte[SJQCD];
      bSJQNR = new String(SJQNR).getBytes();
      for (int i = 0; i < SJQCD; i++) {
        data[45 + i] = bSJQNR[i];
      }

      byte[] bYXJ = new byte[1];
      if (Priority < 15) {
        bYXJ[0] = (byte) Priority;
      }
      else {
        bYXJ = utils.str2bytes(Integer.toHexString(Priority));
      }
      data[45 + SJQCD] = bYXJ[0]; //���ȼ�

      byte[] bSFBH = new byte[1];
      if (ArithmeticNo < 15) {
        bSFBH[0] = (byte) ArithmeticNo;
      }
      else {
        bSFBH = utils.str2bytes(Integer.toHexString(ArithmeticNo));
      }
      data[46 + SJQCD] = bSFBH[0]; //�㷨���

      //���������ݣ���������������
      AddCallInfoToList(AppID, new String(TerminalAddr), GYH, 10, 0); //�����ն���Ϣ��AppID��������
      FormData = true;
      Sequence = Sequence + 1;
      fl.WriteLog("ʵʱͨѶ������Ӧ��ID��" + AppID + "���ն��߼���ַ��" +
                  new String(TerminalAddr) + "������������" + new String(SJQNR));
      if ( (data != null) && (FormData)) { //�������ݵ������������������ݲ�����Ϣ������
        fl.WriteLog("ʵʱͨѶ׼���������Ӧ��ID��" + AppID + "���ն��߼���ַ��" +
                    new String(TerminalAddr) + "������������" + new String(SJQNR));
        SocketChannel keyChannel = (SocketChannel) key.channel(); //ͨ����Ӧ��ͨ����д������
        bBuffer.clear();
        bBuffer.put(data);
        bBuffer.flip();
        keyChannel.write(bBuffer); //�������������쳣����˵���޷����ӳɹ�
        bBuffer.clear();
        AckBack = false;
        fl.WriteLog("ʵʱͨѶ�������Ӧ��ID��" + AppID + "���ն��߼���ַ��" +
                    new String(TerminalAddr) + "������������" + new String(SJQNR));
      }
      return;
    }
    else if ( (RWH != 0) && (RWDS != 0)) { //��ʷ���ݶ�ȡ�����������
      FormData = false; //���ͨѶ���ݿ�ʼ�����ñ�־Ϊ��Ч
      data = null;
      data = new byte[12 + 33 + SJQNR.length + 3];
      byte[] MsgLength = new byte[4];
      MsgLength = utils.int2byte(12 + 33 + SJQNR.length + 3);
      data[0] = MsgLength[0];
      data[1] = MsgLength[1];
      data[2] = MsgLength[2];
      data[3] = MsgLength[3];

      data[4] = 0;
      data[5] = 0;
      data[6] = 0;
      data[7] = 5;

      byte[] MsgSequence = new byte[4];
      MsgSequence = utils.int2byte(Sequence);
      data[8] = MsgSequence[0];
      data[9] = MsgSequence[1];
      data[10] = MsgSequence[2];
      data[11] = MsgSequence[3];

      byte[] bAppID = new byte[4];
      bAppID = utils.int2byte(AppID);
      data[12] = bAppID[0];
      data[13] = bAppID[1];
      data[14] = bAppID[2];
      data[15] = bAppID[3];

      //Ӧ��ID
      byte[] bTemp = new byte[20];
      byte[] bZDLJDZ = new String(TerminalAddr).getBytes();
      for (int i = 0; i < bZDLJDZ.length; i++) {
        bTemp[i] = bZDLJDZ[i];
      }
      for (int i = 0; i < 20; i++) {
        data[16 + i] = bTemp[i];
      }
      //�ն��߼���ַ����Ҫ��ȫ20λ
      byte[] bTXCS = new byte[1];
      if (TXFS < 15) { //ֻ��С��15��������Ҫ���⴦��������������ͳһ����ʵ��
        bTXCS[0] = (byte) TXFS;
      }
      else {
        bTXCS = utils.str2bytes(Integer.toHexString(TXFS));
      }

      data[36] = bTXCS[0];
      //ͨѶ��ʽ�����ն˵�ǰͨѶ����
      data[37] = 1;
      //����ѡ��Ĭ����Ч
      data[38] = 0;
      //���������
      data[39] = 10;
      //����������
      byte[] bGYH = new byte[1];
      bGYH = utils.str2bytes(Integer.toHexString(GYH));
      data[40] = bGYH[0];
      //��Լ��
      bTemp = new byte[2];
      byte[] bGNM = new String(GNM).getBytes();
      for (int i = 0; i < bGNM.length; i++) {
        bTemp[i] = bGNM[i];
      }
      for (int i = 0; i < 2; i++) {
        data[41 + i] = bTemp[i];
      }
      //������
      bTemp = new byte[2];
      byte[] bSJQCD = new byte[2];
      if (SJQCD < 15) { //ֻ��С��15��������Ҫ���⴦��������������ͳһ����ʵ��
        bSJQCD[1] = 0;
        bSJQCD[0] = (byte) SJQCD;
      }
      else {
        bSJQCD = utils.str2bytes(Integer.toHexString(SJQCD));
      }
      for (int i = 0; i < bSJQCD.length; i++) {
        bTemp[i] = bSJQCD[i];
      }
      if (SJQCD < 256) {
        data[43] = bTemp[1]; //���������ȵ��ֶ���Ҫ���ߵ�����
        data[44] = bTemp[0];
      }
      else {
        data[43] = bTemp[0];
        data[44] = bTemp[1];
      }
      //����������
      byte[] bSJQNR = new byte[SJQCD];
      bSJQNR = new String(SJQNR).getBytes();
      for (int i = 0; i < SJQCD; i++) {
        data[45 + i] = bSJQNR[i];
      }
      //����������
      byte[] bRWH = new byte[1];
      String sTemp = Integer.toHexString(RWH);
      if (sTemp.length() % 2 != 0) {
        sTemp = "0" + sTemp;
      }
      bRWH = utils.str2bytes(sTemp);
      data[45 + SJQCD] = bRWH[0];
      //�����
      byte[] bRWDS = new byte[1];
      sTemp = Integer.toHexString(RWDS);
      if (sTemp.length() % 2 != 0) {
        sTemp = "0" + sTemp;
      }
      bRWDS = utils.str2bytes(sTemp);
      data[46 + SJQCD] = bRWDS[0];
      //�������

      byte[] bYXJ = new byte[1];
      if (Priority < 15) {
        bYXJ[0] = (byte) Priority;
      }
      else {
        bYXJ = utils.str2bytes(Integer.toHexString(Priority));
      }
      data[47 + SJQCD] = bYXJ[0]; //���ȼ�

      //��������������
      AddCallInfoToList(AppID, new String(TerminalAddr), GYH, 20, RWDS); //�����ն���Ϣ��AppID��������
      FormData = true;
      Sequence = Sequence + 1;
      if ( (data != null) && (FormData)) { //�������ݵ������������������ݲ�����Ϣ������
        SocketChannel keyChannel = (SocketChannel) key.channel(); //ͨ����Ӧ��ͨ����д������
        try {
          bBuffer.clear();
          bBuffer.put(data);
          bBuffer.flip();
          keyChannel.write(bBuffer); //�������������쳣����˵���޷����ӳɹ�
          bBuffer.clear();
          AckBack = false;
          fl.WriteLog("ʵʱͨѶ�������Ӧ��ID��" + AppID + "���ն��߼���ַ��" +
                      new String(TerminalAddr) + "������������" + new String(SJQNR));
        }
        catch (IOException ex) {
        }
      }
      return;
    }
    else if ( (YCDS != 0) && (RWH == 0) && (RWDS == 0)) { //�쳣���ݶ�ȡ�����������
      FormData = false; //���ͨѶ���ݿ�ʼ�����ñ�־Ϊ��Ч
      data = null;
      data = new byte[12 + 33 + SJQNR.length + 2];
      byte[] MsgLength = new byte[4];
      MsgLength = utils.int2byte(12 + 33 + SJQNR.length + 2);
      data[0] = MsgLength[0];
      data[1] = MsgLength[1];
      data[2] = MsgLength[2];
      data[3] = MsgLength[3];

      data[4] = 0;
      data[5] = 0;
      data[6] = 0;
      data[7] = 6;

      byte[] MsgSequence = new byte[4];
      MsgSequence = utils.int2byte(Sequence);
      data[8] = MsgSequence[0];
      data[9] = MsgSequence[1];
      data[10] = MsgSequence[2];
      data[11] = MsgSequence[3];

      byte[] bAppID = new byte[4];
      bAppID = utils.int2byte(AppID);
      data[12] = bAppID[0];
      data[13] = bAppID[1];
      data[14] = bAppID[2];
      data[15] = bAppID[3];

      //Ӧ��ID
      byte[] bTemp = new byte[20];
      byte[] bZDLJDZ = new String(TerminalAddr).getBytes();
      for (int i = 0; i < bZDLJDZ.length; i++) {
        bTemp[i] = bZDLJDZ[i];
      }
      for (int i = 0; i < 20; i++) {
        data[16 + i] = bTemp[i];
      }
      //�ն��߼���ַ����Ҫ��ȫ20λ
      byte[] bTXCS = new byte[1];
      if (TXFS < 15) { //ֻ��С��15��������Ҫ���⴦��������������ͳһ����ʵ��
        bTXCS[0] = (byte) TXFS;
      }
      else {
        bTXCS = utils.str2bytes(Integer.toHexString(TXFS));
      }

      data[36] = bTXCS[0];
      //ͨѶ��ʽ�����ն˵�ǰͨѶ����
      data[37] = 1;
      //����ѡ��Ĭ����Ч
      data[38] = 0;
      //���������
      data[39] = 10;
      //����������
      byte[] bGYH = new byte[1];
      bGYH = utils.str2bytes(Integer.toHexString(GYH));
      data[40] = bGYH[0];
      //��Լ��
      bTemp = new byte[2];
      byte[] bGNM = new String(GNM).getBytes();
      for (int i = 0; i < bGNM.length; i++) {
        bTemp[i] = bGNM[i];
      }
      for (int i = 0; i < 2; i++) {
        data[41 + i] = bTemp[i];
      }
      //������
      bTemp = new byte[2];
      byte[] bSJQCD = new byte[2];
      if (SJQCD < 15) { //ֻ��С��15��������Ҫ���⴦��������������ͳһ����ʵ��
        bSJQCD[1] = 0;
        bSJQCD[0] = (byte) SJQCD;
      }
      else {
        bSJQCD = utils.str2bytes(Integer.toHexString(SJQCD));
      }
      for (int i = 0; i < bSJQCD.length; i++) {
        bTemp[i] = bSJQCD[i];
      }
      if (SJQCD < 256) {
        data[43] = bTemp[1]; //���������ȵ��ֶ���Ҫ���ߵ�����
        data[44] = bTemp[0];
      }
      else {
        data[43] = bTemp[0];
        data[44] = bTemp[1];
      }
      //����������
      byte[] bSJQNR = new byte[SJQCD];
      bSJQNR = new String(SJQNR).getBytes();
      for (int i = 0; i < SJQCD; i++) {
        data[45 + i] = bSJQNR[i];
      }
      //����������
      byte[] bYCDS = new byte[1];
      String sTemp = Integer.toHexString(YCDS);
      if (sTemp.length() % 2 != 0) {
        sTemp = "0" + sTemp;
      }
      bYCDS = utils.str2bytes(sTemp);
      data[45 + SJQCD] = bYCDS[0];
      //�쳣��������������������

      byte[] bYXJ = new byte[1];
      if (Priority < 15) {
        bYXJ[0] = (byte) Priority;
      }
      else {
        bYXJ = utils.str2bytes(Integer.toHexString(Priority));
      }
      data[46 + SJQCD] = bYXJ[0]; //���ȼ�

      AddCallInfoToList(AppID, new String(TerminalAddr), GYH, 30, YCDS); //�����ն���Ϣ��AppID��������
      FormData = true;
      Sequence = Sequence + 1;
      if ( (data != null) && (FormData)) { //�������ݵ������������������ݲ�����Ϣ������
        SocketChannel keyChannel = (SocketChannel) key.channel(); //ͨ����Ӧ��ͨ����д������
        try {
          bBuffer.clear();
          bBuffer.put(data);
          bBuffer.flip();
          keyChannel.write(bBuffer); //�������������쳣����˵���޷����ӳɹ�
          bBuffer.clear();
          AckBack = false;
        }
        catch (IOException ex) {
        }
      }
      return;
    }
  }

  public boolean SendShortMsg(char[] MsgContent, int MsgLength, char[] MBHM,
                              int SendType, int SMSChannel) {
    //���ȸ��ݷ��������ж���Ϣ�����Ƿ񳬹�Ҫ��
    switch (SendType) {
      case 10: {
        int iLen = new String(MsgContent).trim().length();
        if (iLen != MsgLength) {
          MsgLength = iLen;
        }
        if (iLen > 70) {
          return false;
        }
        break;
      }
      case 20: {
        int iLen = new String(MsgContent).trim().length();
        if (iLen != MsgLength) {
          MsgLength = iLen;
        }
        if (iLen > 140) {
          return false;
        }
        break;
      }
      case 30: {
        int iLen = new String(MsgContent).trim().length();
        if (iLen != MsgLength) {
          MsgLength = iLen;
        }
        if ( (iLen > 280) || (iLen % 2 != 0)) {
          return false;
        }
        break;
      }
    }
    try {
      SendInfo si = new SendInfo();
      si.AppID = 0;
      si.TerminalAddress = new String(MBHM); //Ŀ���ֻ�����
      si.TerminalProtocol = SendType; //��Ϣ����
      si.TerminalCommType = SMSChannel; //�ƶ��Ķ���ͨ��
      si.TaskID = 0;
      si.TaskCount = 0;
      si.AlarmCount = 0;
      si.FunctionCode = new String("");
      si.ContentLength = MsgLength;
      si.DataContent = new String(MsgContent).trim(); //���뷢�Ͷ��м��ɣ����þ������
      si.SendShortMsg = true;
      SendInfoList.add(si);
      LastTime = Calendar.getInstance();
      return true;
    }
    catch (Exception e) {
      return false;
    }
  }

  private void BuildOffLineTerminalInfoListAndSend(SelectionKey key,
      List terminallist) {
    ByteBuffer bBuffer = ByteBuffer.allocate(5000);
    FormData = false; //���ͨѶ���ݿ�ʼ�����ñ�־Ϊ��Ч
    data = null;
    data = new byte[12 + 2 + 20 * terminallist.size()];
    byte[] MsgLength = new byte[4];
    MsgLength = utils.int2byte(12 + 2 + 20 * terminallist.size());
    //��Ϣͷ���ֵ���Ϣ�ܳ���
    data[0] = MsgLength[0];
    data[1] = MsgLength[1];
    data[2] = MsgLength[2];
    data[3] = MsgLength[3];
    //��Ϣͷ���ֵ���Ϣ���ʹ���
    data[4] = 0;
    data[5] = 0;
    data[6] = 0;
    data[7] = 30;
    //��Ϣͷ���ֵ���Ϣ���
    byte[] MsgSequence = new byte[4];
    MsgSequence = utils.int2byte(Sequence);
    data[8] = MsgSequence[0];
    data[9] = MsgSequence[1];
    data[10] = MsgSequence[2];
    data[11] = MsgSequence[3];
    //��Ϣ�岿�ֵ���Ϣ����
    byte[] bTemp = new byte[2];
    byte[] bSJQCD = new byte[2];
    if (terminallist.size() < 15) { //ֻ��С��15��������Ҫ���⴦��������������ͳһ����ʵ��
      bSJQCD[1] = 0;
      bSJQCD[0] = (byte) terminallist.size();
    }
    else {
      bSJQCD = utils.str2bytes(Integer.toHexString(terminallist.size()));
    }
    for (int i = 0; i < bSJQCD.length; i++) {
      bTemp[i] = bSJQCD[i];
    }
    if (terminallist.size() < 256) {
      data[12] = bTemp[1]; //���������ȵ��ֶ���Ҫ���ߵ�����
      data[13] = bTemp[0];
    }
    else {
      data[12] = bTemp[0];
      data[13] = bTemp[1];
    }
    //��Ϣ�岿�ֵ���Ϣ����
    for (int i = 0; i < terminallist.size(); i++) {
      bTemp = new byte[20];
      TerminalOnLineStatus to = (TerminalOnLineStatus) terminallist.get(i);
      byte[] bZDLJDZ = to.TerminalAddress.getBytes();
      for (int j = 0; j < bZDLJDZ.length; j++) {
        bTemp[j] = bZDLJDZ[j];
      }
      for (int j = 0; j < 20; j++) {
        data[14 + i * 20 + j] = bTemp[j];
      }
      //�ն��߼���ַ����Ҫ��ȫ20λ
    }
    FormData = true;
    Sequence = Sequence + 1;
    if ( (data != null) && (FormData)) { //�������ݵ������������������ݲ�����Ϣ������
      SocketChannel keyChannel = (SocketChannel) key.channel(); //ͨ����Ӧ��ͨ����д������
      bBuffer.clear();
      bBuffer.put(data);
      bBuffer.flip();
      try {
        keyChannel.write(bBuffer); //�������������쳣����˵���޷����ӳɹ�
      }
      catch (IOException ex) {
      }
      bBuffer.clear();
      AckBack = false;
    }
    return;
  }

  private void BuildTerminalInfoListAndSend(SelectionKey key, List terminallist) {
    ByteBuffer bBuffer = ByteBuffer.allocate(5000);
    FormData = false; //���ͨѶ���ݿ�ʼ�����ñ�־Ϊ��Ч
    data = null;
    data = new byte[12 + 2 + 20 * terminallist.size()];
    byte[] MsgLength = new byte[4];
    MsgLength = utils.int2byte(12 + 2 + 20 * terminallist.size());
    //��Ϣͷ���ֵ���Ϣ�ܳ���
    data[0] = MsgLength[0];
    data[1] = MsgLength[1];
    data[2] = MsgLength[2];
    data[3] = MsgLength[3];
    //��Ϣͷ���ֵ���Ϣ���ʹ���
    data[4] = 0;
    data[5] = 0;
    data[6] = 0;
    data[7] = 21;
    //��Ϣͷ���ֵ���Ϣ���
    byte[] MsgSequence = new byte[4];
    MsgSequence = utils.int2byte(Sequence);
    data[8] = MsgSequence[0];
    data[9] = MsgSequence[1];
    data[10] = MsgSequence[2];
    data[11] = MsgSequence[3];
    //��Ϣ�岿�ֵ���Ϣ����
    byte[] bTemp = new byte[2];
    byte[] bSJQCD = new byte[2];
    if (terminallist.size() < 15) { //ֻ��С��15��������Ҫ���⴦��������������ͳһ����ʵ��
      bSJQCD[1] = 0;
      bSJQCD[0] = (byte) terminallist.size();
    }
    else {
      bSJQCD = utils.str2bytes(Integer.toHexString(terminallist.size()));
    }
    for (int i = 0; i < bSJQCD.length; i++) {
      bTemp[i] = bSJQCD[i];
    }
    if (terminallist.size() < 256) {
      data[12] = bTemp[1]; //���������ȵ��ֶ���Ҫ���ߵ�����
      data[13] = bTemp[0];
    }
    else {
      data[12] = bTemp[0];
      data[13] = bTemp[1];
    }
    //��Ϣ�岿�ֵ���Ϣ����
    for (int i = 0; i < terminallist.size(); i++) {
      bTemp = new byte[20];
      TerminalOnLineStatus to = (TerminalOnLineStatus) terminallist.get(i);
      byte[] bZDLJDZ = to.TerminalAddress.getBytes();
      for (int j = 0; j < bZDLJDZ.length; j++) {
        bTemp[j] = bZDLJDZ[j];
      }
      for (int j = 0; j < 20; j++) {
        data[14 + i * 20 + j] = bTemp[j];
      }
      //�ն��߼���ַ����Ҫ��ȫ20λ
    }
    FormData = true;
    Sequence = Sequence + 1;
    if ( (data != null) && (FormData)) { //�������ݵ������������������ݲ�����Ϣ������
      SocketChannel keyChannel = (SocketChannel) key.channel(); //ͨ����Ӧ��ͨ����д������
      bBuffer.clear();
      bBuffer.put(data);
      bBuffer.flip();
      try {
        keyChannel.write(bBuffer); //�������������쳣����˵���޷����ӳɹ�
      }
      catch (IOException ex) {
      }
      bBuffer.clear();
      AckBack = false;
    }
    return;
  }

  private void BuildCertainTerminalInfoAndSend(SelectionKey key,
                                               String terminaladdr) {
    ByteBuffer bBuffer = ByteBuffer.allocate(5000);
    FormData = false; //���ͨѶ���ݿ�ʼ�����ñ�־Ϊ��Ч
    data = null;
    data = new byte[12 + 20];
    byte[] MsgLength = new byte[4];
    MsgLength = utils.int2byte(12 + 20);
    //��Ϣͷ���ֵ���Ϣ�ܳ���
    data[0] = MsgLength[0];
    data[1] = MsgLength[1];
    data[2] = MsgLength[2];
    data[3] = MsgLength[3];
    //��Ϣͷ���ֵ���Ϣ���ʹ���
    data[4] = 0;
    data[5] = 0;
    data[6] = 0;
    data[7] = 31;
    //��Ϣͷ���ֵ���Ϣ���
    byte[] MsgSequence = new byte[4];
    MsgSequence = utils.int2byte(Sequence);
    data[8] = MsgSequence[0];
    data[9] = MsgSequence[1];
    data[10] = MsgSequence[2];
    data[11] = MsgSequence[3];
    //��Ϣ�岿�ֵ���Ϣ����
    byte[] bTemp = new byte[20];
    byte[] bZDLJDZ = terminaladdr.getBytes();
    for (int i = 0; i < bZDLJDZ.length; i++) {
      bTemp[i] = bZDLJDZ[i];
    }
    for (int i = 0; i < 20; i++) {
      data[12 + i] = bTemp[i];
    }
    FormData = true;
    Sequence = Sequence + 1;
    if ( (data != null) && (FormData)) { //�������ݵ������������������ݲ�����Ϣ������
      SocketChannel keyChannel = (SocketChannel) key.channel(); //ͨ����Ӧ��ͨ����д������
      bBuffer.clear();
      bBuffer.put(data);
      bBuffer.flip();
      try {
        keyChannel.write(bBuffer); //�������������쳣����˵���޷����ӳɹ�
      }
      catch (IOException ex) {
      }
      bBuffer.clear();
      AckBack = false;
    }
    return;
  }

  public boolean SetTerminalOnLineStatus(List <TerminalOnLineStatus>terminalList) {
    try {
      SetTerminalOnLineStatus = true;
      TerminalStatusList = terminalList;
      Thread.sleep(500);
      return true;
    }
    catch (Exception ex) {
    }
    return false;
  }

  public boolean BuildCertainTerminalInfo(String terminaladdr) {
    try {
      BuildCertainTerminalInfo = true;
      TerminalAddr = terminaladdr;
      Thread.sleep(500);
      return true;
    }
    catch (Exception ex) {
    }
    return false;
  }

  public List GetTerminalOnLineStatus(List <TerminalOnLineStatus>terminalList) {
    List OnLineList = null;
    try {
      OnLineList = new LinkedList();
      //����ն��߼���ַ���У�ͨ��socket�·���ͨѶ���񣬲��ȴ�ͨѶ����ķ���
      GetTerminalOnLineStatusBack = false;
      GetTerminalOnLineStatus = true;
      TerminalStatusList = terminalList;
      Calendar SendTime = Calendar.getInstance();
      SendTime.add(Calendar.SECOND, 2);
      //�ȴ������·��ķ��أ��ٴ������ݺ�����ն�����״̬���У�����
      while (!GetTerminalOnLineStatusBack &&
             SendTime.after(Calendar.getInstance())) {
        Thread.sleep(1);
      }
      OnLineList = TerminalStatusList;
    }
    catch (Exception ex) {
    }
    return OnLineList;
  }

  public boolean SendBatchToFep(int AppID, int TerminalCount, List TerminalInfo,
                                List DataContentInfo, char[] FunctionCode,
                                int TaskID, int TaskCount, int AlarmCount,
                                int Priority) {
    if (ConnectFlag) {
      try {
        for (int i = 0; i < TerminalCount; i++) {
          TerminalInfoStruct tis = (TerminalInfoStruct) TerminalInfo.get(i);
          DataContentStruct dcs = (DataContentStruct) DataContentInfo.get(i);
          SendInfo si = new SendInfo();
          si.AppID = AppID;
          si.TerminalAddress = new String(tis.TerminalAddress);
          si.TerminalProtocol = tis.TerminalProtocol;
          si.TerminalCommType = tis.TerminalCommType;
          si.TaskID = TaskID;
          si.TaskCount = TaskCount;
          si.AlarmCount = AlarmCount;
          si.FunctionCode = new String(FunctionCode);
          si.ContentLength = dcs.DataContentLength;
          si.DataContent = new String(dcs.DataContent);
          si.Priority = Priority;
          si.ArithmeticNo = tis.ArithmeticNo;
          SendInfoList.add(si);
          fl.WriteLog("�������ݼ�����У�Ӧ��ID��" + AppID + "���ն��߼���ַ��" +
                      si.TerminalAddress + "�����������ݣ�" + si.DataContent);
          LastTime = Calendar.getInstance();
        }
        return true;
      }
      catch (Exception e) {
        return false;
      }
    }
    else {
      return false;
    }
  }

  public boolean SendSelfDefinedMsg(int AppID, char[] TerminalAddr, int GYH,
                                    int TXFS, int MLXH, int SJQCD, char[] SJQNR,
                                    int Priority) {
    if (ConnectFlag) {
      try {
        SendInfo si = new SendInfo();
        si.AppID = AppID;
        si.TerminalAddress = new String(TerminalAddr);
        si.TerminalProtocol = GYH;
        si.TerminalCommType = TXFS;
        si.TaskID = MLXH;
        si.TaskCount = 0;
        si.AlarmCount = 0;
        si.FunctionCode = new String("");
        si.ContentLength = SJQCD;
        si.DataContent = new String(SJQNR);
        si.Priority = Priority;
        si.SendSelfDefinedMsg = true;
        SendInfoList.add(si);
        LastTime = Calendar.getInstance();
        return true;
      }
      catch (Exception e) {
        return false;
      }
    }
    else {
      return false;
    }
  }

  public boolean SendToFep(int AppID, char[] TerminalAddr, int GYH, int TXFS,
                           int RWH, int RWDS, int YCDS, char[] GNM, int SJQCD,
                           char[] SJQNR, int Priority, int ArithmeticNo) {
    if (ConnectFlag) {
      try {
        SendInfo si = new SendInfo();
        si.AppID = AppID;
        si.TerminalAddress = new String(TerminalAddr);
        si.TerminalProtocol = GYH;
        si.TerminalCommType = TXFS;
        si.TaskID = RWH;
        si.TaskCount = RWDS;
        si.AlarmCount = YCDS;
        si.FunctionCode = new String(GNM);
        si.ContentLength = SJQCD;
        si.DataContent = new String(SJQNR);
        si.Priority = Priority;
        si.ArithmeticNo = ArithmeticNo;
        SendInfoList.add(si);
        LastTime = Calendar.getInstance();
        return true;
      }
      catch (Exception e) {
        return false;
      }
    }
    else {
      return false;
    }
  }
  public boolean SendPrepayToFep(int AppID, String strDBJH, String strSendInfo, int opType){	  
	  if (ConnectFlag) {
	      try {
	        SendInfo si = new SendInfo();
	        si.AppID = AppID;
	        si.TerminalAddress = strDBJH;
	        if (opType == 1){
	        	si.TerminalProtocol = 1000;
	        }
	        else if (opType == 2){
	        	si.TerminalProtocol = 1001;
	        }
	        else if (opType == 3){
	        	si.TerminalProtocol = 1100;
	        }
	        
	        si.TerminalCommType = 110;
	        si.TaskID = 0;
	        si.TaskCount = 0;
	        si.AlarmCount = 0;
	        si.FunctionCode = "";
	        si.ContentLength = strSendInfo.length();
	        si.DataContent = new String(strSendInfo);
	        si.Priority = 0;
	        si.ArithmeticNo = 0;
	        SendInfoList.add(si);
	        LastTime = Calendar.getInstance();
	        return true;
	      }
	      catch (Exception e) {
	        return false;
	      }
	    }
	    else {
	      return false;
	    }
  }
  
  public List GetNewListMessage(int AppID) {
    List <StructReturnMessage>nl = new LinkedList<StructReturnMessage>(); 
    for (int i = RealTimeList.size() - 1; i >= 0; i--) {
      RealTimeCommunicationInfo ci = (RealTimeCommunicationInfo) RealTimeList.
          get(i); 
      //System.out.println("ci.AppID/ci.BackFlag:"+ci.AppID+"/"+ci.BackFlag);
      if ( (ci.AppID == AppID) && (ci.BackFlag)) { //�ж�������AppID��ͬ�����ҷ��ر�־��Ч
        StructReturnMessage srm = new StructReturnMessage();
        srm.AppID = ci.AppID;
        srm.DataType = ci.DataType;
        srm.GYH = ci.GYH;
        srm.SJQCD = ci.SJQCD;
        srm.ControlConde = ci.KZM.toCharArray();
        srm.FunctionCode = ci.GNM.toCharArray();
        srm.TerminalAddr = ci.TerminalAddr.toCharArray();
        srm.SJQNR = ci.SJQNR.toCharArray();
        RealTimeList.remove(ci); //�Ӷ�����ɾ������
        nl.add(srm); //����Ҫ���ص�������Ϊ������뵽������
      }
    }
    if (nl.size() > 0) { //����List���Ⱦ�������ֵ
      return nl;
    }
    else {
      return null;
    }
  }

  public StructReturnMessage GetNewMessage(int AppID) { //����Ӧ��ID���Ƿ�����Ϣ����
    for (int i = 0; i < RealTimeList.size(); i++) {
      RealTimeCommunicationInfo ci = (RealTimeCommunicationInfo) RealTimeList.
          get(i);
      
      if ( (ci.AppID == AppID) && (ci.BackFlag)) { //�ж�������AppID��ͬ�����ҷ��ر�־��Ч
        StructReturnMessage srm = new StructReturnMessage();
        srm.AppID = ci.AppID;
        srm.DataType = ci.DataType;
        srm.GYH = ci.GYH;
        srm.SJQCD = ci.SJQCD;
        srm.ControlConde = ci.KZM.toCharArray();
        srm.FunctionCode = ci.GNM.toCharArray();
        srm.TerminalAddr = ci.TerminalAddr.toCharArray();
        srm.SJQNR = ci.SJQNR.toCharArray();
        RealTimeList.remove(ci); //�Ӷ�����ɾ������
        return srm;
      }
    }
    return null;
  }
/*
  private void WaitAckBack(SocketChannel socketChannel) {
    //���ȶ�ȡ12���ַ��ı���ͷ���ݣ���������ͷ����Ч��
    try {
      ByteBuffer[] buffer = new ByteBuffer[10];
      buffer[0] = ByteBuffer.allocate(12); //���ڽ��յĻ���
      buffer[0].clear();
      long count = 0;
      try {
        count = socketChannel.read(buffer, 0, 1);
      }
      catch (IOException ex1) {
        utils.PrintDebugMessage("ReadBuffer Error,Error info " + ex1.toString(),
                                "D");
        count = -1;
      }
      if (count == -1) { //ǰ�û���socketͨ�������쳣ʱ��Ҳ��Ҫ������·���У���֤�����е�������ȷ
        try {
          FStop = true;
          ConnectFlag = false;
          client.socket().close();
          client.close();
          socketChannel.socket().close();
          socketChannel.close();
        }
        catch (Exception ex8) {
        }
      }
      if (count == 12) {
        buffer[0].flip();
        byte data[] = new byte[10000];
        buffer[0].get(data, 0, buffer[0].limit());
        MessageBody mb = new MessageBody();
        String strMesshead = utils.bytes2str(data, buffer[0].limit());
        mb.TotalLength = strMesshead.substring(1, 8);
        mb.CommandID = strMesshead.substring(14, 16);
        mb.SeqID = strMesshead.substring(16, 24);
        //�������ͷ��Ч���������ȡ����ı��������ݣ����д�������ʧ��ǰ����ͷ��������ȡ12���ַ�
        buffer[1] = ByteBuffer.allocate(Integer.parseInt(mb.TotalLength,
            16) - 12); //���ڽ��յĻ���
        count = socketChannel.read(buffer, 1, 1);
        if (count > 0) {
          buffer[1].flip();
          buffer[1].get(data, 12, buffer[1].limit());
          String strMessbody = utils.bytes2str(data,
                                               Integer.parseInt(mb.TotalLength,
              16));
          processData(Integer.parseInt(mb.CommandID, 16), socketChannel,
                      strMessbody, data);
          //������Ч�ı��Ĵ�����˳��������ݵĶ�ȡ������̣��������������Ҫ������ٴν��뱾��������
        }
      }
    }
    catch (Exception ex) {
    }
  }
*/
  protected void receiveData(SelectionKey key) throws Exception {
    //���ȶ�ȡ12���ַ��ı���ͷ���ݣ���������ͷ����Ч��
    ByteBuffer[] buffer = new ByteBuffer[10];
    buffer[0] = ByteBuffer.allocate(12); //���ڽ��յĻ���
    SocketChannel socketChannel = (SocketChannel) key.channel();
    buffer[0].clear();
    long count = 0;
    try {
      count = socketChannel.read(buffer, 0, 1);
    }
    catch (IOException ex1) {
      //utils.PrintDebugMessage("readData Error,Error info " + ex1.toString(),
      //                        "D");
      count = -1;
    }
    if (count == -1) { //ǰ�û���socketͨ�������쳣ʱ��Ҳ��Ҫ������·���У���֤�����е�������ȷ
      try {
        FStop = true;
        ConnectFlag = false;
        client.socket().close();
        client.close();
        socketChannel.socket().close();
        socketChannel.close();
      }
      catch (Exception ex8) {
      }
    }
    if (count == 12) {
      buffer[0].flip();
      byte data[] = new byte[10000];
      buffer[0].get(data, 0, buffer[0].limit());
      MessageBody mb = new MessageBody();
      String strMesshead = utils.bytes2str(data, buffer[0].limit());
      mb.TotalLength = strMesshead.substring(1, 8);
      mb.CommandID = strMesshead.substring(8, 16);
      mb.SeqID = strMesshead.substring(16, 24);
      //�������ͷ��Ч���������ȡ����ı��������ݣ����д�������ʧ��ǰ����ͷ��������ȡ12���ַ�
      buffer[1] = ByteBuffer.allocate(Integer.parseInt(mb.TotalLength,
          16) - 12); //���ڽ��յĻ���
      count = socketChannel.read(buffer, 1, 1);
      if (count > 0) {
        buffer[1].flip();
        buffer[1].get(data, 12, buffer[1].limit());
        String strMessbody = utils.bytes2str(data,
                                             Integer.parseInt(mb.TotalLength,
            16));
        LastTime = Calendar.getInstance();
        processData(Integer.parseInt(mb.CommandID, 16), socketChannel,
                    strMessbody, data);
        //������Ч�ı��Ĵ�����˳��������ݵĶ�ȡ������̣��������������Ҫ������ٴν��뱾��������
      }
    }
  }

  protected String AnalyzeCommandType(String Data) throws Exception {
    //�����õ��������ͣ������Ϣ���Ⱥ��յ��������ݳ��Ȳ�ͬ������������
    String sMessageLen = Data.substring(1, 8);
    if (Data.length() != utils.HexStrToInt(sMessageLen) * 2) {
      return "0";
    }
    else {
      String sCommandType = Data.substring(14, 16);
      return (sCommandType);
    }
  }

  protected void AckMessage(String CommandID, String sData,
                            SocketChannel channel) {
    //����ȷ����Ϣ
    String ss = "0000000D08000" + CommandID + sData.substring(16, 24) + "0A";
    ByteBuffer bBuffer = ByteBuffer.allocate(5000);
    bBuffer.clear();
    bBuffer.put(utils.str2bytes(ss));
    bBuffer.flip();
    try {
      channel.write(bBuffer);
    }
    catch (IOException ex) {
    }
    bBuffer.clear();
  }

  private void UpdateCallInfoList(int AppID, String ZDLJDZ, int GYH, int SJLX,
                                  int SJCD, String SJNR, String GNM, String KZM) {
    for (int i = 0; i < RealTimeList.size(); i++) {
      RealTimeCommunicationInfo ci = (RealTimeCommunicationInfo) RealTimeList.
          get(i);
      //����Ӧ��ID���ն��߼���ַ�жϸ����͵��ٲ������Ƿ��Ƕ�Ӧ���ݵķ���
      if ( (ci.AppID == AppID) && (ci.TerminalAddr.trim().equals(ZDLJDZ)) &&
          (!ci.BackFlag)) {
        //��������������
        RealTimeCommunicationInfo rtcInfo = new RealTimeCommunicationInfo();
        rtcInfo.AppID = ci.AppID;
        rtcInfo.TerminalAddr = ci.TerminalAddr;
        rtcInfo.GYH = ci.GYH;
        rtcInfo.DataType = SJLX;
        rtcInfo.SJQCD = SJCD;
        rtcInfo.SJQNR = SJNR;
        rtcInfo.GNM = GNM;
        rtcInfo.KZM = KZM;
        rtcInfo.BackFlag = true; //����ر�־
        rtcInfo.AddInTime = Calendar.getInstance();
        RealTimeList.set(i, rtcInfo);
        //utils.PrintDebugMessage("UpdateCallInfoList,APPID is "+AppID+",ZDLJDZ is " + ZDLJDZ +
        //        ",SJQNR is " + SJNR, "D");
        break;
      }
    }
  }

  protected void AnalyzeCommandContent(String sData, int CommandType,
                                       byte[] indata, SocketChannel channel) {
    String sMessageLen = sData.substring(1, 8);
    if (sData.length() != utils.HexStrToInt(sMessageLen) * 2) {
      return;
    }
    else {
      switch (CommandType) {
        case 0x00000007: { //�ٲ����ݵķ��أ����ڷ������ݵķ���
          int iAppID = utils.HexStrToInt(sData.substring(24, 32)); //Ӧ��ID
          int iGYH = utils.HexStrToInt(sData.substring(72, 74)); //��Լ��
          String sZDLJDZ = "";

          byte[] Content = new byte[20];
          for (int i = 0; i < 20; i++) {
            Content[i] = indata[i + 16];
          }
          sZDLJDZ = new String(Content).trim(); //�ն��߼���ַ

          int iSJLX = utils.HexStrToInt(sData.substring(78, 80)); //��������
          int iSJQCD = utils.HexStrToInt(sData.substring(80, 84)); //����������

          Content = new byte[iSJQCD];
          for (int i = 0; i < iSJQCD; i++) {
            Content[i] = indata[i + 42];
          }
          String sSJQNR = new String(Content).trim(); //����������

          Content = new byte[2];
          for (int i = 0; i < 2; i++) {
            Content[i] = indata[i + 42 + iSJQCD];
          }
          String sGNM = new String(Content).trim(); //������

          Content = new byte[2];
          for (int i = 0; i < 2; i++) {
            Content[i] = indata[i + 44 + iSJQCD];
          }
          String sKZM = new String(Content).trim(); //������
          //utils.PrintDebugMessage("Receive Data Result,APPID is "+iAppID+",ZDLJDZ is " + sZDLJDZ +
          //                        ",SJQNR is " + sSJQNR, "D");
          UpdateCallInfoList(iAppID, sZDLJDZ, iGYH, iSJLX, iSJQCD, sSJQNR, sGNM,
                             sKZM); //����Ӧ��ID���ն��߼���ַ��Ӧ�ϲ�ĵ���
          break;
        }
        case 0x00000008: { //��ʷ�����ٲ�ķ���
          int iAppID = utils.HexStrToInt(sData.substring(24, 32)); //Ӧ��ID
          int iGYH = utils.HexStrToInt(sData.substring(72, 74)); //��Լ��
          String sZDLJDZ = "";

          byte[] Content = new byte[20];
          for (int i = 0; i < 20; i++) {
            Content[i] = indata[i + 16];
          }
          sZDLJDZ = new String(Content).trim(); //�ն��߼���ַ

          int iSJQCD = utils.HexStrToInt(sData.substring(112, 116)); //����������

          Content = new byte[iSJQCD];
          for (int i = 0; i < iSJQCD; i++) {
            Content[i] = indata[i + 58];
          }
          String sSJQNR = new String(Content).trim(); //����������

          Content = new byte[2];
          for (int i = 0; i < 2; i++) {
            Content[i] = indata[i + 58 + iSJQCD];
          }
          String sGNM = new String(Content).trim(); //������

          Content = new byte[2];
          for (int i = 0; i < 2; i++) {
            Content[i] = indata[i + 60 + iSJQCD];
          }
          String sKZM = new String(Content).trim(); //������
          //utils.PrintDebugMessage("Receive Data Result,APPID is "+iAppID+",ZDLJDZ is " + sZDLJDZ +
          //                        ",SJQNR is " + sSJQNR, "D");
          UpdateCallInfoList(iAppID, sZDLJDZ, iGYH, 220, iSJQCD, sSJQNR, sGNM,
                             sKZM); //����Ӧ��ID���ն��߼���ַ��Ӧ�ϲ�ĵ���
          break;
        }
        case 0x00000009: { //�쳣�ٲ����ݵķ���
          int iAppID = utils.HexStrToInt(sData.substring(24, 32)); //Ӧ��ID
          int iGYH = utils.HexStrToInt(sData.substring(72, 74)); //��Լ��
          String sZDLJDZ = "";

          byte[] Content = new byte[20];
          for (int i = 0; i < 20; i++) {
            Content[i] = indata[i + 16];
          }
          sZDLJDZ = new String(Content).trim(); //�ն��߼���ַ

          int iSJQCD = utils.HexStrToInt(sData.substring(128, 132)); //����������

          Content = new byte[iSJQCD];
          for (int i = 0; i < iSJQCD; i++) {
            Content[i] = indata[i + 66];
          }
          String sSJQNR = new String(Content).trim(); //����������

          Content = new byte[2];
          for (int i = 0; i < 2; i++) {
            Content[i] = indata[i + 66 + iSJQCD];
          }
          String sGNM = new String(Content).trim(); //������

          Content = new byte[2];
          for (int i = 0; i < 2; i++) {
            Content[i] = indata[i + 68 + iSJQCD];
          }
          String sKZM = new String(Content).trim(); //������
          UpdateCallInfoList(iAppID, sZDLJDZ, iGYH, 230, iSJQCD, sSJQNR, sGNM,
                             sKZM); //����Ӧ��ID���ն��߼���ַ��Ӧ�ϲ�ĵ���
          break;
        }
        case 0x00000016: {
          int iCount = utils.HexStrToInt(sData.substring(24, 28)); //���ص��ն�����
          String sZDLJDZ = "";
          TerminalStatusList.clear();
          int iPos = 28;
          for (int i = 0; i < iCount; i++) {
            byte[] Content = new byte[20];
            for (int j = 0; j < 20; j++) {
              Content[j] = indata[j + iPos / 2];
            }
            sZDLJDZ = new String(Content).trim(); //�ն��߼���ַ
            iPos = iPos + 40;
            int iOnLineStatus = utils.HexStrToInt(sData.substring(iPos,
                iPos + 2)); //����״̬
            iPos = iPos + 2;
            TerminalOnLineStatus to = new TerminalOnLineStatus();
            to.TerminalAddress = sZDLJDZ;
            to.OnLineStatus = iOnLineStatus;
            TerminalStatusList.add(to);
          }
          GetTerminalOnLineStatusBack = true;
          break;
        }
        case 0x00000108:case 0x00000109: { //�ٲ����ݵķ��أ����ڷ������ݵķ���
            int iAppID = utils.HexStrToInt(sData.substring(24, 32)); //Ӧ��ID
            int iGYH; //��Լ��
            if (CommandType==264){
            	iGYH = 1000;
            }
            else{
            	iGYH = 1001;
            }
            String sZDLJDZ = "";

            byte[] Content = new byte[20];
            for (int i = 0; i < 20; i++) {
              Content[i] = indata[i + 16];
            }
            sZDLJDZ = new String(Content).trim(); //�ն��߼���ַ

            int iSJQCD = utils.HexStrToInt(sData.substring(72, 76)); //����������

            Content = new byte[iSJQCD];
            for (int i = 0; i < iSJQCD; i++) {
              Content[i] = indata[i + 38];
            }
            String sSJQNR = new String(Content).trim(); //����������

            
            //utils.PrintDebugMessage("Receive Data Result,ZDLJDZ is " + sZDLJDZ +
            //                        ",SJQNR is " + sSJQNR, "D");
            UpdateCallInfoList(iAppID, sZDLJDZ, iGYH, 0, iSJQCD, sSJQNR, "",
                               ""); //����Ӧ��ID���ն��߼���ַ��Ӧ�ϲ�ĵ���
            break;
          }
        default: {
          break;
        }
      }
    }
  }

  private void processData(int CommandID, SocketChannel channel,
                           String ReceiveData, byte[] Data) {
    switch (CommandID) { //�����������ͷֱ���
      case 0x08000004: { //��ͨ�ٲ������ȷ��
        AckBack = true;
        break;
      }
      case 0x08000005: { //��ʷ���ݶ�ȡ��ȷ��
        AckBack = true;
        break;
      }
      case 0x08000006: { //�쳣���ݶ�ȡ��ȷ��
        AckBack = true;
        break;
      }
      case 0x00000007: { //��ͨѶ�����ύ���ٲⷵ�����ݴ���
        AckMessage(ReceiveData.substring(13, 16), ReceiveData, channel);
        AnalyzeCommandContent(ReceiveData, CommandID, Data, channel);
        break;
      }
      case 0x00000008: { //��ͨѶ�����ύ����ʷ���ݴ���
        AckMessage(ReceiveData.substring(13, 16), ReceiveData, channel);
        AnalyzeCommandContent(ReceiveData, CommandID, Data, channel);
        break;
      }
      case 0x00000009: { //��ͨѶ�����ύ���쳣���ݴ���
        AckMessage(ReceiveData.substring(13, 16), ReceiveData, channel);
        AnalyzeCommandContent(ReceiveData, CommandID, Data, channel);
        break;
      }
      case 0x0800000A: { //�·��Զ�����ŵ�ȷ��
        AckBack = true;
        break;
      }
      case 0x0800000C: { //�Զ��������ȷ��
        AckBack = true;
        break;
      }
      case 0x0800000D: { //ȷ����Ϣ����������
        ConnectFlag = true;
        AckBack = true;
        //utils.PrintDebugMessage("MasterStation Connected", "D");
        break;
      }
      case 0x0800000E: { //ȷ����Ϣ����������
        try {
          channel.socket().close();
          channel.close();
          client.socket().close();
          client.close();
          selector.close();
        }
        catch (IOException ex) {
        }
        AckBack = true;
        break;
      }
      case 0x08000015: { //�·��ն��߼���ַ����Ϣȷ�ϣ�������
        AckBack = true;
        break;
      }
      case 0x00000016: { //�ն�����״̬��Ϣ�ķ���
        AckMessage(ReceiveData.substring(13, 16), ReceiveData, channel);
        AnalyzeCommandContent(ReceiveData, CommandID, Data, channel);
        break;
      }
      case 0x0800001E: { //�����ն�����״̬����Ϣȷ�ϣ�������
        AckBack = true;
        break;
      }
      case 0x00000108:case 0x00000109:{
    	  AckMessage(ReceiveData.substring(13, 16), ReceiveData, channel);
          AnalyzeCommandContent(ReceiveData, CommandID, Data, channel);
    	  break;
      }
      default: {
        break;
      }
    }
  }
/*
  private void processData(String sData, SocketChannel channel, byte[] InData) {
    String CommandID = null;
    try {
      String sMessageLen = sData.substring(1, 8);
      if (sData.length() != utils.HexStrToInt(sMessageLen) * 2) {
        sData = sData.substring(0, utils.HexStrToInt(sMessageLen) * 2);
      }
      CommandID = sData.substring(14, 16);
    }
    catch (Exception ex) {
      try {
        channel.close();
      }
      catch (IOException e) {
      }
      return ;
    }
    //System.out.println("CommandID is " + (CommandID));
    switch (Integer.parseInt(CommandID, 16)) { //�����������ͷֱ���
      case 4: { //��ͨ�ٲ������ȷ��
        AckBack = true;
        break;
      }
      case 5: { //��ʷ���ݶ�ȡ��ȷ��
        AckBack = true;
        break;
      }
      case 6: { //�쳣���ݶ�ȡ��ȷ��
        AckBack = true;
        break;
      }
      case 7: { //��ͨѶ�����ύ���ٲⷵ�����ݴ���
        //AckMessage(CommandID, sData, channel);
        AnalyzeCommandContent(sData, utils.ten2sixteen(CommandID), InData,
                              channel);
        break;
      }
      case 8: { //��ͨѶ�����ύ����ʷ���ݴ���
        //AckMessage(CommandID, sData, channel);
        AnalyzeCommandContent(sData, utils.ten2sixteen(CommandID), InData,
                              channel);
        break;
      }
      case 9: { //��ͨѶ�����ύ���쳣���ݴ���
        //AckMessage(CommandID, sData, channel);
        AnalyzeCommandContent(sData, utils.ten2sixteen(CommandID), InData,
                              channel);
        break;
      }
      case 10: { //�·��Զ�����ŵ�ȷ��
        AckBack = true;
        break;
      }
      case 12: { //�Զ��������ȷ��
        AckBack = true;
        break;
      }
      case 13: { //ȷ����Ϣ����������
        ConnectFlag = true;
        AckBack = true;
        break;
      }
      case 14: { //ȷ����Ϣ����������
        try {
          channel.socket().close();
          channel.close();
          client.socket().close();
          client.close();
          selector.close();
        }
        catch (IOException ex) {
        }
        AckBack = true;
        break;
      }
      case 21: { //�·��ն��߼���ַ����Ϣȷ�ϣ�������
        AckBack = true;
        break;
      }
      case 22: { //�ն�����״̬��Ϣ�ķ���
        AckMessage(CommandID, sData, channel);
        AnalyzeCommandContent(sData, utils.ten2sixteen(CommandID), InData,
                              channel);
        break;
      }
      case 30: { //�����ն�����״̬����Ϣȷ�ϣ�������
        AckBack = true;
        break;
      }
      default: {
        break;
      }
    }
  }
*/
  class RealTimeCommunicationInfo { //ʵʱͨѶ�Ķ���ṹ
    public int AppID = 0; //Ӧ��ID
    public String TerminalAddr = ""; //�ն��߼���ַ
    public int GYH = 0; //��Լ��
    public int DataType = 0; //��������
    public int SJQCD = 0; //����������
    public String SJQNR = ""; //����������
    public String GNM = ""; //������
    public String KZM = ""; //������
    public boolean BackFlag = false; //��Ϣ�Ƿ񷵻ر�־
    public Calendar AddInTime; //������е�ʱ��
  }

  class RealTimeCommunicationInfoListThread
      extends Thread {
    public void run() {
      while (true) {
        if (FStop) {
          fl.WriteLog("ʵʱͨѶ����߳��˳���");
          return;
        }
        try {
          for (int i = 0; i < RealTimeList.size(); i++) {
            RealTimeCommunicationInfo ci = (RealTimeCommunicationInfo)
                RealTimeList.get(i);
            if (ci.BackFlag) { //�Ѿ����صĳ�ʱʱ��Ϊ60s
              ci.AddInTime.add(Calendar.MINUTE, 1);
              if (ci.AddInTime.before(Calendar.getInstance())) {
                RealTimeList.remove(i);
                break;
              }
              else {
                ci.AddInTime.add(Calendar.MINUTE, -1);
              }
            }
            else { //û�з��صĳ�ʱʱ��Ϊ2����
              ci.AddInTime.add(Calendar.MINUTE, 2);
              if (ci.AddInTime.before(Calendar.getInstance())) {
                RealTimeList.remove(i);
                break;
              }
              else {
                ci.AddInTime.add(Calendar.MINUTE, -2);
              }
            }
            Thread.sleep(1);
          }
          if ( (RealTimeList.size() == 0)) {
            LastTime.add(Calendar.MINUTE, 2);
            if (LastTime.before(Calendar.getInstance())) {
              //Calendar c = Calendar.getInstance();
              //SimpleDateFormat formatter = new SimpleDateFormat(
              //    "yyyy-MM-dd HH:mm:ss");
              //System.out.println(formatter.format(c.getTime()) +
              //                   "ʵʱͨѶ�Ͽ���ͨѶ����Ŀ�������");
              DisConnect();
            }
            else {
              LastTime.add(Calendar.MINUTE, -2);
            }
          }
          Thread.sleep(1);
        }
        catch (Exception ex2) {
        }
      }

    }
  }

  class MonitorThread
      extends Thread {
    SendInfo si = null;
    public void run() {
      while (true) {
        if (FStop) {
          fl.WriteLog("��ʱ���������߳��˳���");
          return;
        }
        try {
          int n = 0;
          try {
            n = selector.selectNow(); //���io׼��������channel����
          }
          catch (Exception ex2) {
          }
          if (n == 0) { //�жϵ�ǰ�Ƿ��������¼�Ҫ����
            try {
              Thread.sleep(1);
            }
            catch (InterruptedException ex1) {
            }
            continue ;
          }
          SendData();
          try {
            Thread.sleep(1);
          }
          catch (InterruptedException ex) {
          }
        }
        catch (Exception ex7) {
        }
      }
    }

    public void SendData() {
      Calendar SendTime = Calendar.getInstance(); //��������ʱ�䣬�����ж����ʱ���ط�
      int ReSendTimes = 0; //�����ظ����ʹ���
      try {
        Set readyKeys = selector.selectedKeys();
        Iterator i = readyKeys.iterator();
        while (i.hasNext()) {
          SelectionKey key = (SelectionKey) i.next(); //ȡ�������жϵ�ǰsocketͨ����״̬����
          i.remove();
          if (key.isConnectable()) { //˵��socket����׼������
            SocketChannel keyChannel = (SocketChannel) key.channel();
            keyChannel.register(selector,
                                SelectionKey.OP_READ |
                                SelectionKey.OP_WRITE); //�������ڴ��ڿ��տɷ�״̬��һ����Ǽ���������ʱ��
            if (keyChannel.isConnectionPending()) { //�����������ʱ�䱻�ң�������������ֹ
              keyChannel.finishConnect();
            }
            //���͵�¼����
            try {
              BuildConnectAndSend(key);
            }
            catch (Exception ex6) {
            }
          }
          if (key.isReadable()) { //���¼��Ǳ���������
            try {
              receiveData(key); //������յ�������
            }
            catch (Exception ex3) {
            }
          }
          else if (key.isWritable()) {
            int iSize = SendInfoList.size();
            if (GetTerminalOnLineStatus) {
              GetTerminalOnLineStatus = false;
              try {
                BuildTerminalInfoListAndSend(key, TerminalStatusList);
              }
              catch (Exception ex5) {
              }
            }
            else if (SetTerminalOnLineStatus) {
              SetTerminalOnLineStatus = false;
              BuildOffLineTerminalInfoListAndSend(key, TerminalStatusList);
            }
            else if (BuildCertainTerminalInfo) {
              BuildCertainTerminalInfo = false;
              BuildCertainTerminalInfoAndSend(key, TerminalAddr);
            }
            else {
              if (iSize > 0) {
                if (AckBack) {
                  try {
                    si = (SendInfo) SendInfoList.get(0);
                    SendInfoList.remove(0);
                  }
                  catch (Exception ex7) {
                  }
                  ReSendTimes = 0;
                  //AckBack = false;
                  try {
                    BuildSendFrameAndSend(si.AppID,
                                          si.TerminalAddress.toCharArray(),
                                          si.TerminalProtocol,
                                          si.TerminalCommType,
                                          si.TaskID,
                                          si.TaskCount, si.AlarmCount,
                                          si.FunctionCode.toCharArray(),
                                          si.ContentLength,
                                          si.DataContent.toCharArray(), key,
                                          si.SendShortMsg,
                                          si.SendSelfDefinedMsg, si.Priority,
                                          si.ArithmeticNo);
                  }
                  catch (Exception ex1) {
                    fl.WriteLog("����ʵʱͨѶ�����쳣���쳣����:" + ex1.toString());
                  }
                  SendTime = Calendar.getInstance();
                  SendTime.add(Calendar.MILLISECOND, 100);
                  AckBack = true;
                }
                //�����ط�����ΪͨѶ�����ٻ�ȷ��
                else if (SendTime.before(Calendar.getInstance()) &&
                         (ReSendTimes < 2)) {
                  if (si != null) {
                    //System.err.println("ReSend");
                    fl.WriteLog("ʵʱͨѶ�ط����Ӧ��ID��" + si.AppID + "���ն��߼���ַ��" +
                                si.TerminalAddress + "�����������ݣ�" + si.DataContent);
                    ReSendTimes = ReSendTimes + 1;
                    try {
                      BuildSendFrameAndSend(si.AppID,
                                            si.TerminalAddress.toCharArray(),
                                            si.TerminalProtocol,
                                            si.TerminalCommType,
                                            si.TaskID,
                                            si.TaskCount, si.AlarmCount,
                                            si.FunctionCode.toCharArray(),
                                            si.ContentLength,
                                            si.DataContent.toCharArray(), key,
                                            si.SendShortMsg,
                                            si.SendSelfDefinedMsg, si.Priority,
                                            si.ArithmeticNo);
                    }
                    catch (Exception ex2) {
                      fl.WriteLog("����ʵʱͨѶ�����쳣���쳣����:" + ex2.toString());
                    }
                    SendTime = Calendar.getInstance();
                    SendTime.add(Calendar.MILLISECOND, 100);
                  }
                  else {
                    AckBack = true;
                  }
                }
                else if (ReSendTimes >= 2) {
                  AckBack = true;
                }
                try {
                  Thread.sleep(10);
                }
                catch (InterruptedException ex) {
                }
              }
            }
          }
          try {
            Thread.sleep(1);
          }
          catch (InterruptedException ex) {
          }

        }
      }
      catch (Exception ex4) {
        fl.WriteLog("ʵʱͨѶ��·�쳣���쳣���ݣ�" + ex4.toString());
        try {
          Thread.sleep(1000 * ReConnectTimes); //������������������Ҫ�ȴ���ʱ��
        }
        catch (Exception ex8) {
        }
      }
    }
  }

  public static void main(String[] args) {
    RealTimeCommunication rtc = new RealTimeCommunication("127.0.0.1", 3000);
    rtc.Connect();
    try {
		Thread.sleep(1500);
	} catch (InterruptedException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}
    String s = "68BD08BD08684B33332222500F6D0000080C99C5EEC7990C5630303030303330383132313356565623280325051100000002B8390002EC390002EC390002EC390002F2390002EC390002F8390002FE390002043A0002EC390002EC390002EC3900020A3A0002103A0002163A00021C3A0002EC390002EC390002EC390002EC390002EC390002EC390002223A0002283A00022E3A0002343A00023A3A0002EC390002EC390002EC3900026245000254450002EC390002EC39000270450002EC390002EC390002EC390002EC390002EC390002EC390002EC390002EC390002EC390002EC390002EC390002EC390002EC390002EC390002EC390002EC390002EC390002EC390002A0450002EC390002EC390002EC390002A84900026E4B0002EC390002EC390002184A0002D84B0002EC390002EC390002EC390002EC390002EC390002EC390002EC390002EC390002BC520002EC390002EC390002EC390002EC390002EC3900028A4A00024E4C0002EC390002EC390002FC4A0002BC4C0002EC390002EC390002EC390002EC390002EC390002EC390002EC390002EC390002EC390002EC390002EC390002EC390002EC390002EC390002EC390002EC390002EC390002EC390002EC390002EC390002EC390002EC390002EC390002EC390002EC390002EC390002EC390002EC390002EC390002EC390002EC390002EC390002103D0002983C0002A63C0002103D0002B83C0002103D0002103D0002103D0002CA3C0002103D0002103D0002103D0002103D0002EE55554816";
    boolean flag = rtc.SendSelfDefinedMsg(1, "33331111".toCharArray(), 100, 50, 1, s.length(), s.toCharArray(), 1);
    //StructReturnMessage rm = rtc.GetNewMessage(0);
    if (flag) {
//     System.out.println("ok");
      while (true) {
        StructReturnMessage rm = rtc.GetNewMessage(516);
        if (rm != null) {
//          System.out.println("Get a New Message!");
          //System.out.println(rm.AppID + " " + rm.TerminalAddr + " " + rm.SJQNR);
          break;
        }
        try {
          Thread.sleep(2000);
        }
        catch (InterruptedException ex) {
        }
      }
    }
    else {
//      System.out.println("fail");
    }
  }

  class SendInfo { //��Ҫ�·������ݽṹ�����ڱ��浽������
    public int AppID; //Ӧ��ID
    public String TerminalAddress; //�ն��߼���ַ������Ŀ����ֻ�����
    public int TerminalProtocol; //�ն˹�Լ�Ż����Ƕ��ŷ��͵�����
    public int TerminalCommType; //�ն�ͨѶ��ʽ
    public String FunctionCode; //������
    public int ContentLength; //����������
    public String DataContent; //����������
    public int TaskID; //�����
    public int TaskCount; //�������
    public int AlarmCount; //�쳣����
    public int Priority; //���ȼ�(���230MͨѶ����)
    public int ArithmeticNo; //�㷨���(��Թ��������ģ������)
    public boolean SendShortMsg = false; //ֱ�ӷ��͵ı�־�����ڷ��Ͷ���
    public boolean SendSelfDefinedMsg = false; //���ڷ����Զ�������
  }

  private void jbInit() throws Exception {
  }
}

class MessageBody {
  public String TotalLength;
  public String CommandID;
  public String SeqID;
}
