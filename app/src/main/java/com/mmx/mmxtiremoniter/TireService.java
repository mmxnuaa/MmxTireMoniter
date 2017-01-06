package com.mmx.mmxtiremoniter;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.widget.RemoteViews;

import java.lang.ref.WeakReference;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.UUID;

public class TireService extends Service {
    private static final String TAG = "mmxbtservice";
    private static final int NID_TIREDATA = 1;
    // Binder given to clients
    private final IBinder mBinder = new LocalBinder();

    private NotificationManager mNotifyManager;
    private BluetoothAdapter mBluetoothAdapter;
    //    private String target_address = "78:C5:E5:A0:1D:42";
    private static final String target_address = "44:A6:E5:03:43:C5";
    private static final UUID RX_SERVICE_UUID = UUID.fromString("00001000-0000-1000-8000-00805f9b34fb");
    private static final UUID RX_CHAR_UUID = UUID.fromString("00001001-0000-1000-8000-00805f9b34fb");
    private static final UUID TX_CHAR_UUID = UUID.fromString("00001002-0000-1000-8000-00805f9b34fb");
    private static final UUID CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    public static final String ACTION_WORK_SATE_CHANGE = "com.mmx.ACTION_WORK_SATE_CHANGE";
    public static final String ACTION_TIRE_CHANGE = "com.mmx.ACTION_TIRE_CHANGE";
    public static final String EXTRA_WORK_STATE = "EXTRA_WORK_STATE";
    public static final String EXTRA_TIRE_DATA = "EXTRA_TIRE_DATA";

    BluetoothGatt mGatt;
    BluetoothGattCharacteristic mRxChar;
    RemoteViews mNotifyView;
    Notification mNotification;
    HashMap<String, Integer> mViewIdMap = new HashMap<>();

    private static final int WS_NO_BT = -1;
    private static final int WS_CONNECTING = 0;
    private static final int WS_DESCOVER_SERVER = 1;
    private static final int WS_INIT_SERVER = 2;
    private static final int WS_READY = 3;

    private static final int MSG_ALARM = 1000;
    private static final int MSG_BT_ON = 1001;
    private static final int MSG_BT_OFF = 1002;
    private static final int MSG_MANUAL_PAIR = 1003;
    private static final int MSG_MANUAL_INIT = 1004;
    private static final int MSG_MANUAL_READ = 1005;
    private static final int MSG_READ_ALL = 1006;
    private static final int READ_0 = 0;
    private static final int READ_1 = 1;
    private static final int READ_2 = 2;
    private static final int READ_3 = 3;
    private static final int READ_end = 4;
    static byte[] cmd0 = new byte[]{(byte) 0xAA, 0x41, (byte) 0xA1, 0x07, 0x63, 0x01, (byte) 0xF7};
    static byte[] cmd1 = new byte[]{(byte) 0xAA, 0x41, (byte) 0xA1, 0x07, 0x63, 0x02, (byte) 0xF8};
    static byte[] cmd2 = new byte[]{(byte) 0xAA, 0x41, (byte) 0xA1, 0x07, 0x63, 0x03, (byte) 0xF9};
    static byte[] cmd3 = new byte[]{(byte) 0xAA, 0x41, (byte) 0xA1, 0x07, 0x63, 0x04, (byte) 0xFa};
    static byte[] cmdend = new byte[]{(byte) 0xAA, 0x41, (byte) 0xA1, 0x07, 0x62, 0x00, (byte) 0xF5};
    private int mCmdRound = 0;

    SparseArray<TireData> mTireDataPool = new SparseArray<>();
    private boolean mbAlarm = false;
    private static final int ALARM_INTERVAL = 20000;
    private boolean mAllFound = false;
    private ArrayList<String> mErrorHistory = new ArrayList<>();
    ErrorListener mErrorListener = null;
    private ArrayList<String> mLogHistory = new ArrayList<>();
    LogListener mLogListener = null;

    int mWorkState = WS_NO_BT;
    private TextToSpeech tts = null; // TTS对象
    private ArrayList<String> ttsQueue = new ArrayList<>();
    private boolean mbTtsInitOk;

    private boolean mQuiting = false;
    ProtoDecode mDecoder = new ProtoDecode();

    class ProtoDecode {
        ArrayList<Byte> mFrame = new ArrayList<Byte>();

        int mTotalLen = 0;

        void reset() {
            mFrame.clear();
            mTotalLen = 0;
        }

        void decode(byte[] values) {
            if (mWorkState != WS_READY) {
                return;
            }
            for (byte v : values) {
                boolean broken = false;
                switch (mFrame.size()) {
                    case 0:
                        broken = (v != (byte) 0xAA);
                        break;
                    case 1:
                        broken = (v != (byte) 0xA1);
                        break;
                    case 2:
                        broken = (v != (byte) 0x41);
                        break;
                    case 3:
                        mTotalLen = v;
                        if (mTotalLen < 6 //at least 6 bytes
                                || mTotalLen > 30) { //set a proper MAX limit
                            mTotalLen = 0;
                            broken = true;
                        }
                        break;
                }
                mFrame.add(v);
                if (broken) {
//                    Log.d(TAG, "broken packet: "+Hex2Str(mFrame));
                    log("broken packet: "+Hex2Str(mFrame));
                    reset();
                } else if (mFrame.size() == mTotalLen) {
                    Byte sum = 0;
                    for (Byte b : mFrame) {
                        sum = (byte) (sum + b);
                    }
                    if ((sum & 0xff) != ((mFrame.get(mFrame.size() - 1) * 2) & 0xff)) {
//                        Log.d(TAG, "checksum fail packet: "+Hex2Str(mFrame));
                        log("checksum fail packet: "+Hex2Str(mFrame));
                    } else {
//                        Log.d(TAG, "good packet: "+Hex2Str(mFrame));
                        procFrame(mFrame);
                    }
                    reset();
                }
            }
        }
    }

    private String WorkState2Str(int ws) {
        switch (ws) {
            case WS_NO_BT:
                return "蓝牙关闭";
            case WS_CONNECTING:
                return "连接中";
            case WS_DESCOVER_SERVER:
                return "查询串口服务";
            case WS_INIT_SERVER:
                return "初始化串口服务";
            case WS_READY:
                return "一切就绪";
            default:
                return "错误:未知工作状态码:" + ws;
        }
    }

    private void updateWorkState(int ws) {
        if (ws != mWorkState) {
            Intent intent = new Intent(ACTION_WORK_SATE_CHANGE);
            String strWs = WorkState2Str(ws);
            intent.putExtra(EXTRA_WORK_STATE, strWs);
            mWorkState = ws;
            ttsSay(strWs);
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
            mNotifyView.setTextViewText(R.id.WorkState, strWs);
            mNotifyManager.notify(NID_TIREDATA, mNotification);
        }
    }

    private void updateTireData(TireData td) {
        Intent intent = new Intent(ACTION_TIRE_CHANGE);
        intent.putExtra(EXTRA_TIRE_DATA, td.ToMap());
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);

        Log.d(TAG, "updateTireData: "+td);
        if (td.idx >= 1 && td.idx <= 4) {
            mNotifyView.setInt(mViewIdMap.get("low" + td.idx), "setVisibility", (td.tooLow ? View.VISIBLE : (td.tooHigh ? View.GONE : View.INVISIBLE)));
            mNotifyView.setInt(mViewIdMap.get("high" + td.idx), "setVisibility", (td.tooHigh ? View.VISIBLE : View.GONE));
            mNotifyView.setInt(mViewIdMap.get("hot" + td.idx), "setVisibility", (td.tooHot ? View.VISIBLE : View.INVISIBLE));
            mNotifyView.setInt(mViewIdMap.get("leak" + td.idx), "setVisibility", (td.fastLeak ? View.VISIBLE : View.INVISIBLE));
            mNotifyView.setInt(mViewIdMap.get("lowBattery" + td.idx), "setVisibility", (td.lowBattery ? View.VISIBLE : View.INVISIBLE));
            SimpleDateFormat df = new SimpleDateFormat("mm:ss", Locale.CHINESE);
            mNotifyView.setTextViewText( mViewIdMap.get("data" + td.idx), String.format(Locale.CHINESE, "% 4d: %1.2f巴 % 2d度",
                    td.updateCnt, td.pressure, td.temp));
            mNotifyManager.notify(NID_TIREDATA, mNotification);
        }
    }

    private void procFrame(ArrayList<Byte> frame) {
        switch (frame.get(4)) {
            case 0x63:
                if (frame.size() == 14 || frame.size() == 15) {
                    procTireData(frame);
                }else {
                    log(String.format(Locale.CHINESE, "Unknown Frame (%d) [%02X]", frame.size(), frame.get(4)) + Hex2Str(frame));
                }
                break;
            default:
//                showMessage(String.format("Unknown Frame [%02X]", frame.get(4)) + Hex2Str(frame));
                log(String.format(Locale.CHINESE, "Unknown Frame (%d) [%02X]", frame.size(), frame.get(4)) + Hex2Str(frame));
                break;
        }
    }

    class TireData {
        int idx;
        double pressure;
        int temp;
        boolean fastLeak;
        boolean tooHigh;
        boolean tooLow;
        boolean tooHot;
        boolean lowBattery;
        int updateCnt;
        Date updateTime;
        Date changeTime;

        TireData(int Idx, double pres, int Temp, boolean leak, boolean high, boolean low, boolean hot, boolean lowBat) {
            updateCnt = 0;
            idx = Idx;
            pressure = pres;
            temp = Temp;
            fastLeak = leak;
            tooHigh = high;
            tooLow = low;
            tooHot = hot;
            lowBattery = lowBat;
            updateTime = new Date();
            changeTime = new Date();
            SpeakOutTireData();
        }

        HashMap<String, Object> ToMap() {
            HashMap<String, Object> map = new HashMap<>();
            map.put("idx", idx);
            map.put("pressure", pressure);
            map.put("temp", temp);
            map.put("high", tooHigh);
            map.put("low", tooLow);
            map.put("hot", tooHot);
            map.put("lowBattery", lowBattery);
            map.put("leak", fastLeak);
            map.put("updateCnt", updateCnt);
            map.put("updateTime", updateTime);
            map.put("changeTime", changeTime);
            return map;
        }

        void SpeakOutTireData() {
            SpeakOutTireData(false);
        }

        void SpeakOutTireData(boolean alarm) {
            String msg = "未知轮胎序号" + idx;
//            switch (idx) {
//                case 1:
//                    msg = "左前... ";
//                    break;
//                case 2:
//                    msg = "左后... ";
//                    break;
//                case 3:
//                    msg = "右前... ";
//                    break;
//                case 4:
//                    msg = "右后... ";
//                    break;
//            }
            switch (idx) {
                case 1:
                    msg = "右前... ";
                    break;
                case 2:
                    msg = "左前... ";
                    break;
                case 3:
                    msg = "右后... ";
                    break;
                case 4:
                    msg = "左后... ";
                    break;
            }
            msg += String.format(Locale.CHINESE, "%.3f",pressure) + "巴," + temp + "度."
                    + (fastLeak ? "漏气," : "")
                    + (tooHigh ? "高压," : "")
                    + (tooLow ? "低压," : "")
                    + (tooHot ? "过热," : "")
                    + (lowBattery ? "缺电," : "");
            ttsSay((alarm ? "警告:" : "") + msg);
        }

        boolean updateTireData(int Idx, double pres, int Temp, boolean leak, boolean high, boolean low, boolean hot, boolean lowBat) {
            updateCnt++;
            boolean changed = (pressure != pres
                    || temp != Temp
                    || fastLeak != leak
                    || tooHigh != high
                    || tooLow != low
                    || tooHot != hot
                    || lowBattery != lowBat
            );
            if (idx != Idx) {
                logErrorMsg("程序错误, 轮胎序号错乱");
            }
            ;
            pressure = pres;
            temp = Temp;
            fastLeak = leak;
            tooHigh = high;
            tooLow = low;
            tooHot = hot;
            lowBattery = lowBat;
            updateTime = new Date();
            if (changed) {
                SpeakOutTireData();
                changeTime = new Date();
            }
            return changed;
        }

        boolean isAlarm() {
            return fastLeak || tooHigh || tooLow || tooHot || lowBattery;
        }

        boolean isValid() {
            return temp > -40;
        }

        @Override
        public String toString() {
            return ToMap().toString();
        }
    }

    private void procTireData(ArrayList<Byte> frame) {
        int baseIdx = 0;
        if (frame.size() == 14) {
            baseIdx = 0;
        }else if (frame.size() == 15) {
            baseIdx = 1;
        }else {
            log("ERROR: tire data len<13: "+Hex2Str(frame));
            return;
        }
        String tireId = String.format("0x%02X%02X%02X", frame.get(6+baseIdx), frame.get(7+baseIdx), frame.get(8+baseIdx));
        Byte idx = frame.get(5+baseIdx);
        double Pressure = (frame.get(9+baseIdx) * 256 + frame.get(10+baseIdx)) * 0.025;
        int temp = frame.get(11+baseIdx) - 50;
        Byte flag = frame.get(12+baseIdx);
        boolean fastLeak = (flag & 0x3) == 1;
        boolean tooHigh = (flag & 0x10) == 0x10;
        boolean tooLow = (flag & 0x8) == 0x8;
        boolean tooHot = (flag & 0x4) == 0x4;
        boolean lowBattery = (flag & 0x80) == 0x80;

        boolean changed = true;
        TireData td = mTireDataPool.get(idx);
        if (td != null) {
            changed = td.updateTireData(idx, Pressure, temp, fastLeak, tooHigh, tooLow, tooHot, lowBattery);
        } else {
            td = new TireData(idx, Pressure, temp, fastLeak, tooHigh, tooLow, tooHot, lowBattery);
            mTireDataPool.put(idx, td);
        }
        updateTireData(td);

        if (changed) {
            boolean alarm = false;
            for (int i = 0; i < mTireDataPool.size(); i++) {
                td = mTireDataPool.valueAt(i);
                if (td != null) {
                    alarm = alarm || td.isAlarm();
                }
            }
            if (alarm && !mbAlarm) {
                mbAlarm = true;
                mHandler.sendEmptyMessageDelayed(MSG_ALARM, ALARM_INTERVAL);
            }
            if (!mAllFound) {
                int i = 1;
                for (; i <= 4; i++) {
                    td = mTireDataPool.get(i);
                    if (td == null || !td.isValid()) {
                        break;
                    }
                }
                if (i > 4) {
                    mAllFound = true;
                    ttsSay("所有轮胎已被监控!");
                }
            }
        }
    }

    public TireService() {
    }

    interface ErrorListener {
        void newError(ArrayList<String> s);

        void newError(String s);
    }

    interface LogListener {
        void newLog(ArrayList<String> s);

        void newLog(String s);
    }

    private void logErrorMsg(String s) {
        ttsSay("错误:" + s);
        s = DateFormat.getDateTimeInstance().format(new Date()) + " : " + s;
        mErrorHistory.add(s);
        if (mErrorListener != null) {
            mErrorListener.newError(s);
        }
    }

    private void log(String s) {
        s = DateFormat.getDateTimeInstance().format(new Date()) + " : " + s;
        if (mLogHistory.size()>100){
            mLogHistory.remove(0);
        }
        mLogHistory.add(s);
        if (mLogListener != null) {
            mLogListener.newLog(s);
        }
        Log.d(TAG, s);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mViewIdMap.put("low1", (R.id.low1));
        mViewIdMap.put("high1", (R.id.high1));
        mViewIdMap.put("leak1", (R.id.leak1));
        mViewIdMap.put("hot1", (R.id.hot1));
        mViewIdMap.put("lowBattery1", (R.id.lowBattery1));
        mViewIdMap.put("low2", (R.id.low2));
        mViewIdMap.put("high2", (R.id.high2));
        mViewIdMap.put("leak2", (R.id.leak2));
        mViewIdMap.put("hot2", (R.id.hot2));
        mViewIdMap.put("lowBattery2", (R.id.lowBattery2));
        mViewIdMap.put("low3", (R.id.low3));
        mViewIdMap.put("high3", (R.id.high3));
        mViewIdMap.put("leak3", (R.id.leak3));
        mViewIdMap.put("hot3", (R.id.hot3));
        mViewIdMap.put("lowBattery3", (R.id.lowBattery3));
        mViewIdMap.put("low4", (R.id.low4));
        mViewIdMap.put("high4", (R.id.high4));
        mViewIdMap.put("leak4", (R.id.leak4));
        mViewIdMap.put("hot4", (R.id.hot4));
        mViewIdMap.put("lowBattery4", (R.id.lowBattery4));
        mNotifyView = new RemoteViews(getPackageName(), R.layout.tire_data_update_notification);
        for (Integer v : mViewIdMap.values()) {
            mNotifyView.setInt(v, "setVisibility", View.INVISIBLE);
        }
        mViewIdMap.put("data1", (R.id.tireData1));
        mViewIdMap.put("data2", (R.id.tireData2));
        mViewIdMap.put("data3", (R.id.tireData3));
        mViewIdMap.put("data4", (R.id.tireData4));
        mNotifyView.setTextViewText(R.id.WorkState, WorkState2Str(mWorkState));
        mNotifyManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        Intent resultIntent = new Intent(this, MainActivity.class);
        resultIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TASK);

// Because clicking the notification launches a new ("special") activity,
// there's no need to create an artificial back stack.
        PendingIntent resultPendingIntent =
                PendingIntent.getActivity(
                        this,
                        0,
                        resultIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT
                );
        mNotification =
                new Notification.Builder(this)
                        // Show controls on lock screen even when user hides sensitive content.
                        .setVisibility(Notification.VISIBILITY_PUBLIC)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        // Add media control buttons that invoke intents in your media service
//                .setStyle(new Notification.MediaStyle())
                        .setStyle(new Notification.BigTextStyle())
                        .setContentIntent(resultPendingIntent)
                        .setContent(mNotifyView)
                        .build();
//        mNotification.flags |= Notification.FLAG_NO_CLEAR | Notification.FLAG_ONGOING_EVENT;
        tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    int result = tts.setLanguage(Locale.CHINESE);
                    if (result == TextToSpeech.LANG_MISSING_DATA
                            || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        logErrorMsg("tts数据丢失或不支持");
                    }
                    mbTtsInitOk = true;
                    for (String s : ttsQueue) {
                        ttsSay(s);
                    }
                    ttsQueue.clear();
                } else {
                    logErrorMsg("tts 初始化失败, 错误码:" + status);
                }
            }
        });
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(mBtStateReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));

        startForeground(NID_TIREDATA, mNotification);

        if (IsBtEnabled()) {
            DoBTConnect();
        }else {
            TurnOnBT();
        }
    }

    BroadcastReceiver mBtStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF);
            int oldState = intent.getIntExtra(BluetoothAdapter.EXTRA_PREVIOUS_STATE, BluetoothAdapter.STATE_OFF);
            if (state == BluetoothAdapter.STATE_ON) {
                mHandler.sendEmptyMessage(MSG_BT_ON);
            } else if (oldState == BluetoothAdapter.STATE_ON) {
                mHandler.sendEmptyMessage(MSG_BT_OFF);
            }
        }
    };


    private void ttsSay(String s) {
        if (tts != null) {
            if (mbTtsInitOk) {
                tts.speak(s, TextToSpeech.QUEUE_ADD, null, null);
            } else {
                ttsQueue.add(s);
            }
        }
    }

    @Override
    public void onDestroy() {
//        mNotifyManager.cancel(NID_TIREDATA);
        super.onDestroy();
        mErrorListener = null;
        mLogListener = null;
        mWorkState = WS_NO_BT;
        if (mGatt != null) {
            mGatt.connect();
            mGatt.close();
            mGatt = null;
        }
        mRxChar = null;
        if (mBluetoothAdapter != null) {
            mBluetoothAdapter.disable();
        }
        mBluetoothAdapter = null;
        if (tts != null) {
            tts.speak("胎压监测服务退出", TextToSpeech.QUEUE_FLUSH, null, null);
            SystemClock.sleep(2000);
            tts.shutdown();
            tts = null;
        }
        unregisterReceiver(mBtStateReceiver);
    }

    boolean IsBtEnabled() {
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        if (mBluetoothAdapter == null) {
            mBluetoothAdapter = bluetoothManager.getAdapter();
        }
        return !(mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled());
    }

    void TurnOnBT() {
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        if (mBluetoothAdapter == null) {
            mBluetoothAdapter = bluetoothManager.getAdapter();
        }
        if (mBluetoothAdapter != null) {
            mBluetoothAdapter.enable();
        }
    }

    void DoBTConnect() {
        if (mQuiting) {
            return;
        }
        if (!IsBtEnabled()) {
            logErrorMsg("蓝牙未使能");
            return;
        }

        updateWorkState(WS_CONNECTING);
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(target_address);
        if (mGatt != null) {
            mGatt.connect();
        } else {
            mGatt = device.connectGatt(this, true, new BluetoothGattCallback() {
                @Override
                public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                    super.onConnectionStateChange(gatt, status, newState);
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        ttsSay("蓝牙已连接．");
                        updateWorkState(WS_DESCOVER_SERVER);
                        mGatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH);
                        mGatt.discoverServices();
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        ttsSay("蓝牙失连．");
                        if (mWorkState != WS_NO_BT) {
                            updateWorkState(WS_CONNECTING);
                        }
                        mGatt.close();
                        mGatt = null;
                        mRxChar = null;
                        DoBTConnect();
                    }
                }

                @Override
                public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                    super.onServicesDiscovered(gatt, status);
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        ttsSay("服务查询完毕．");
                        updateWorkState(WS_INIT_SERVER);
                        initUartServer();
                    } else {
                        logErrorMsg("服务查询失败．错误码:" + status);
                    }
                }

                private void initUartServer() {
                    if (mWorkState == WS_INIT_SERVER) {
                        BluetoothGattService RxService = mGatt.getService(RX_SERVICE_UUID);
                        if (RxService == null) {
                            logErrorMsg("串口服务不存在1");
                            return;
                        }
                        BluetoothGattCharacteristic TxChar = RxService.getCharacteristic(TX_CHAR_UUID);
                        if (TxChar == null) {
                            logErrorMsg("串口服务不存在2");
                            return;
                        }
                        mGatt.setCharacteristicNotification(TxChar, true);
                        BluetoothGattDescriptor descriptor = TxChar.getDescriptor(CCCD);
                        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                        mGatt.writeDescriptor(descriptor);
                        mRxChar = RxService.getCharacteristic(RX_CHAR_UUID);
                        if (mRxChar == null) {
                            logErrorMsg("串口服务属性不存在");
                            return;
                        }
                        updateWorkState(WS_READY);
                        mDecoder.reset();
                        mCmdRound = 0;
                        mTireDataPool.clear();
                        mAllFound = false;
//                        mHandler.sendEmptyMessageDelayed(READ_0, 500);
                        mHandler.sendEmptyMessageDelayed(MSG_READ_ALL, 10*1000);
                    }
                }


                @Override
                public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic
                        characteristic) {
                    super.onCharacteristicChanged(gatt, characteristic);
                    byte[] values = characteristic.getValue();
                    Log.d(TAG, "onCharacteristicChanged: XXXX: " + characteristic.getUuid() + " value: " + Hex2Str(values));
                    if (TX_CHAR_UUID.equals(characteristic.getUuid())) {
                        log("get: " + Hex2Str(values));
                        mDecoder.decode(values);
                    }else {
                        log("get from unknown uuid [" +characteristic.getUuid()+ "]: " + Hex2Str(values));
                    }
                }

            });
        }
    }

    private String Hex2Str(ArrayList<Byte> value) {
        String str = "(" + value.size() + "):";
        for (byte v : value) {
            str += " " + String.format("%02X", v);
        }
        return str;
    }
    private String Hex2Str(byte[] value) {
        String str = "(" + value.length + "):";
        for (byte v : value) {
            str += " " + String.format("%02X", v);
        }
        return str;
    }

    static class ServerHandler extends Handler {
        WeakReference<TireService> mSvr;

        ServerHandler(TireService svr) {
            mSvr = new WeakReference<TireService>(svr);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            TireService svr = mSvr.get();
            if (mSvr == null) {
                Log.d(TAG, "handleMessage: mSvr is null, ignore msg: " + msg);
                return;
            }
            switch (msg.what) {
                case READ_0:
                    svr.writeRXCharacteristic(cmd0);
                    break;
                case READ_1:
                    svr.writeRXCharacteristic(cmd1);
                    break;
                case READ_2:
                    svr.writeRXCharacteristic(cmd2);
                    break;
                case READ_3:
                    svr.writeRXCharacteristic(cmd3);
                    break;
                case READ_end:
                    svr.writeRXCharacteristic(cmdend);
                    break;
                case MSG_ALARM:
                    svr.checkAlarm();
                    break;
                case MSG_BT_ON:
                    svr.onBtOn();
                    break;
                case MSG_BT_OFF:
                    svr.onBtOff();
                    break;
                case MSG_MANUAL_PAIR:
                    svr.sendManualPair(msg.arg1);
                    break;
                case MSG_MANUAL_INIT:
                    svr.sendManualInit();
                    break;
                case MSG_MANUAL_READ:
                    svr.sendManualRead();
                    break;
                case MSG_READ_ALL:
                    svr.sendManualRead();
                    if (svr.mWorkState == WS_READY) {
                        sendEmptyMessageDelayed(MSG_READ_ALL, 60 * 1000);
                    }
                    break;
            }
            if (msg.what >= READ_0 && msg.what <= READ_end) {
                if (svr.mWorkState == WS_READY) {
                    int roundGap = 10000;
                    int cmdGap = 500;
                    if (svr.mCmdRound < 10) {
                        roundGap = 10000;
                        cmdGap = 500;
                    }

                    sendEmptyMessageDelayed(msg.what == READ_end ? READ_0 : msg.what + 1,
                            msg.what == READ_end ? roundGap : cmdGap);
                    if (msg.what == READ_end) {
                        svr.mCmdRound++;
                    }
                }
            }
        }
    }

    private void onBtOff() {
        if (!mQuiting) {
//            ttsSay("蓝牙已关闭");
            updateWorkState(WS_NO_BT);
        }
    }

    private void onBtOn() {
        if (!mQuiting) {
            ttsSay("蓝牙已打开");
            DoBTConnect();
        }
    }

    private void checkAlarm() {
        boolean alarm = false;
        for (int i = 0; i < mTireDataPool.size(); i++) {
            TireData td = mTireDataPool.valueAt(i);
            if (td != null && td.isAlarm()) {
                td.SpeakOutTireData(true);
                alarm = true;
            }
        }
        mbAlarm = alarm;
        if (mbAlarm) {
            mHandler.sendEmptyMessageDelayed(MSG_ALARM, ALARM_INTERVAL);
        }
    }

    private ServerHandler mHandler = new ServerHandler(this);

    public void writeRXCharacteristic(byte[] value) {
        if (mWorkState != WS_READY) {
            Log.d(TAG, "writeRXCharacteristic: WRONG STATE:" + mWorkState);
            return;
        }
        mRxChar.setValue(value);
        log("send: "+Hex2Str(value));
        Log.d(TAG, "write TXchar - status=" + mGatt.writeCharacteristic(mRxChar) + " " + Hex2Str(value));
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }


    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    public class LocalBinder extends Binder {
        TireService getService() {
            // Return this instance of LocalService so clients can call public methods
            return TireService.this;
        }
    }

    void registerErrorListener(ErrorListener listener) {
        mErrorListener = listener;
        if (listener != null) {
            mErrorListener.newError(mErrorHistory);
        }
    }

    void unRegisterErrorListener(ErrorListener listener) {
        if (mErrorListener == listener) {
            mErrorListener = null;
        }
    }

    void CleanErrorHistory() {
        mErrorHistory = new ArrayList<>();
    }

    void registerLogListener(LogListener listener) {
        mLogListener = listener;
        if (listener != null) {
            mLogListener.newLog(mLogHistory);
        }
    }

    void unRegisterLogListener(LogListener listener) {
        if (mLogListener == listener) {
            mLogListener = null;
        }
    }

    void CleanLogHistory() {
        mLogHistory = new ArrayList<>();
    }

    void QuitServer() {
        mQuiting = true;
        stopSelf();
    }

    String GetCurrentWorkState() {
        return WorkState2Str(mWorkState);
    }

    ArrayList<HashMap<String, Object>> GetCurrentTireData() {
        ArrayList<HashMap<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < mTireDataPool.size(); i++) {
            TireData td = mTireDataPool.valueAt(i);
            if (td != null) {
                result.add(td.ToMap());
            }
        }
        return result;
    }

    //    private void procTireData(ArrayList<Byte> frame) {
//        String tireId = String.format("0x%02X%02X%02X", frame.get(6), frame.get(7), frame.get(8));
//        Byte idx = frame.get(5);
//        double Pressure = (frame.get(9) * 256 + frame.get(10)) * 0.025;
//        int temp = frame.get(11) - 50;
//        Byte flag = frame.get(12);
//        boolean fastLeak = (flag & 0x3) == 1;
//        boolean tooHigh = (flag & 0x10) == 0x10;
//        boolean tooLow = (flag & 0x8) == 0x8;
//        boolean tooHot = (flag & 0x4) == 0x4;
//        boolean lowBattery = (flag & 0x80) == 0x80;
    void test() {
        Byte[] a = new Byte[]{0, 0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
        ArrayList<Byte> frame = new ArrayList<>(Arrays.asList(a));
        frame.set(5, (byte) 2);
        frame.set(9, (byte) 2);
        frame.set(10, (byte) 34);
        frame.set(11, (byte) 76);
        frame.set(12, (byte) 134);
        procTireData(frame);
        logErrorMsg("程序错误, 轮胎序号错乱");

    }

    void ManualPair(int index){
        mHandler.obtainMessage(MSG_MANUAL_PAIR, index, 0).sendToTarget();
    }

    void ManualInit(){
        mHandler.sendEmptyMessage(MSG_MANUAL_INIT);
    }

    void ManualRead(){
        mHandler.sendEmptyMessage(MSG_MANUAL_READ);
    }

    private void sendManualInit() {
        writeRXCharacteristic(new byte[]{(byte) 0xaa, 0x41, (byte) 0xa1, 0x06, 0x11, (byte) 0xa3});
    }
    private void sendManualRead() {
        writeRXCharacteristic(new byte[]{(byte) 0xaa, 0x41, (byte) 0xa1, 0x07, 0x63, 0x00, (byte) 0xf6});
    }

    private void sendManualPair(int index) {
        byte[] cmd = new byte[14];
        cmd[0] = (byte) 0xaa;
        cmd[1] = (byte) 0x41;
        cmd[2] = (byte) 0xa1;
        cmd[3] = (byte) 14;
        cmd[4] = (byte) 0x63;
        cmd[5] = (byte) index;
        if (index == 1){
            cmd[6] = 0x02;
            cmd[7] = (byte) 0xfd;
            cmd[8] = 0x6f;
        }else if (index == 2){
            cmd[6] = 0x02;
            cmd[7] = (byte) 0xfc;
            cmd[8] = (byte) 0xe9;
        }else if (index == 3){
            cmd[6] = 0x03;
            cmd[7] = (byte) 0x01;
            cmd[8] = (byte) 0x7f;
        }else if (index == 4) {
            cmd[6] = 0x02;
            cmd[7] = (byte) 0xff;
            cmd[8] = (byte) 0x5f;
        }else {
            logErrorMsg("程序错误, 非法轮胎序号");
            return;
        }
        cmd[9] = (byte) 0;
        cmd[10] = (byte) 0;
        cmd[11] = (byte) 0;
        cmd[12] = (byte) 0;

        cmd[13] = 0;
        for (int i = 0; i <= 12; i ++ ) {
            cmd[13] += cmd[i] ;
        }

        if (mWorkState != WS_READY) {
            ttsSay("状态错误,手动配对"+index+"失败");
            return;
        }
        writeRXCharacteristic(cmd);
        ttsSay("手动配对传感器"+index);
    }
}
