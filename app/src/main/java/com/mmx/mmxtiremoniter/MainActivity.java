package com.mmx.mmxtiremoniter;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements TireService.ErrorListener, TireService.LogListener {
    private static final String TAG = "mmxbtMainActivity";
    private static final int REQUEST_ENABLE_BT = 1;
    private HashMap<String, View> mTireDataViews = new HashMap<>();
    private ArrayAdapter<String> mErrorAdaptor;
    private ArrayAdapter<String> mLogAdaptor;
    private TireService mTireService;
    int a = 0;
    private static final int MSG_INIT = 1;
    private static final int MSG_UNINIT = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        startService(new Intent(this, TireService.class));
        if (!bindService(new Intent(this, TireService.class), mConnection, 0)) {
            Log.d(TAG, "onCreate: bind service fail");
            Toast.makeText(this, "Bind tire service fail", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        mTireDataViews.put("low1", findViewById(R.id.low1));
        mTireDataViews.put("high1", findViewById(R.id.high1));
        mTireDataViews.put("leak1", findViewById(R.id.leak1));
        mTireDataViews.put("hot1", findViewById(R.id.hot1));
        mTireDataViews.put("lowBattery1", findViewById(R.id.lowBattery1));
        mTireDataViews.put("low2", findViewById(R.id.low2));
        mTireDataViews.put("high2", findViewById(R.id.high2));
        mTireDataViews.put("leak2", findViewById(R.id.leak2));
        mTireDataViews.put("hot2", findViewById(R.id.hot2));
        mTireDataViews.put("lowBattery2", findViewById(R.id.lowBattery2));
        mTireDataViews.put("low3", findViewById(R.id.low3));
        mTireDataViews.put("high3", findViewById(R.id.high3));
        mTireDataViews.put("leak3", findViewById(R.id.leak3));
        mTireDataViews.put("hot3", findViewById(R.id.hot3));
        mTireDataViews.put("lowBattery3", findViewById(R.id.lowBattery3));
        mTireDataViews.put("low4", findViewById(R.id.low4));
        mTireDataViews.put("high4", findViewById(R.id.high4));
        mTireDataViews.put("leak4", findViewById(R.id.leak4));
        mTireDataViews.put("hot4", findViewById(R.id.hot4));
        mTireDataViews.put("lowBattery4", findViewById(R.id.lowBattery4));
        for (View v : mTireDataViews.values()) {
            v.setVisibility(View.INVISIBLE);
        }
        mTireDataViews.put("data1", findViewById(R.id.tireData1));
        mTireDataViews.put("data2", findViewById(R.id.tireData2));
        mTireDataViews.put("data3", findViewById(R.id.tireData3));
        mTireDataViews.put("data4", findViewById(R.id.tireData4));

        ListView mErrorList = (ListView) findViewById(R.id.ErrorList);
        ListView mLogList = (ListView) findViewById(R.id.LogList);
        findViewById(R.id.button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mTireService != null) {
                    mTireService.QuitServer();
                }
                finish();
            }
        });
        findViewById(R.id.buttonRunInBackground).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
//                if (mTireService != null) {
//                    mTireService.test();
//                }
            }
        });

        findViewById(R.id.btnPair1).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mTireService != null) {
                    mTireService.ManualPair(1);
                }
            }
        });
        findViewById(R.id.btnPair2).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mTireService != null) {
                    mTireService.ManualPair(2);
                }
            }
        });
        findViewById(R.id.btnPair3).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mTireService != null) {
                    mTireService.ManualPair(3);
                }
            }
        });
        findViewById(R.id.btnPair4).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mTireService != null) {
                    mTireService.ManualPair(4);
                }
            }
        });

        findViewById(R.id.btnInit).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mTireService != null) {
                    mTireService.ManualInit();
                }
            }
        });
        findViewById(R.id.btnRead).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mTireService != null) {
                    mTireService.ManualRead();
                }
            }
        });


        mErrorAdaptor = new ArrayAdapter<String>(this, android.R.layout.simple_expandable_list_item_1);
        mErrorList.setAdapter(mErrorAdaptor);
        mLogAdaptor = new ArrayAdapter<String>(this, android.R.layout.simple_expandable_list_item_1);
        mLogList.setAdapter(mLogAdaptor);

//        if (!IsBtEnabled()) {
//            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
//            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
//        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode){
            case REQUEST_ENABLE_BT:
                if (resultCode == RESULT_CANCELED){
                    Toast.makeText(this, "蓝牙未打开", Toast.LENGTH_LONG).show();
                }
                break;
        }
    }

//    boolean IsBtEnabled() {
//        final BluetoothManager bluetoothManager =
//                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
//        BluetoothAdapter adapter = bluetoothManager.getAdapter();
//        return (adapter != null && adapter.isEnabled());
//    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mConnection);
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(TireService.ACTION_TIRE_CHANGE);
        filter.addAction(TireService.ACTION_WORK_SATE_CHANGE);
        LocalBroadcastManager.getInstance(this).registerReceiver(mReceiver, filter);
        init();
    }

    @Override
    protected void onStop() {
        super.onStop();
        deInit();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mReceiver);
    }

    BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(TireService.ACTION_TIRE_CHANGE)) {
                updateTireData((HashMap<String, Object>) intent.getSerializableExtra(TireService.EXTRA_TIRE_DATA));
            } else if (intent.getAction().equals(TireService.ACTION_WORK_SATE_CHANGE)) {
                ((TextView) findViewById(R.id.WorkState)).setText(intent.getStringExtra(TireService.EXTRA_WORK_STATE));
            }
        }
    };

    //    map.put("idx", idx);
//    map.put("pressure", pressure);
//    map.put("temp", temp);
//    map.put("high", tooHigh);
//    map.put("low", tooLow);
//    map.put("hot", tooHot);
//    map.put("lowBattery", lowBattery);
//    map.put("updateCnt", updateCnt);
    private void updateTireData(HashMap<String, Object> td) {
        Log.d(TAG, "Got Tire Update:"+td);
        Integer idx = (Integer) td.get("idx");
        Double pressure = (Double) td.get("pressure");
        Integer temp = (Integer) td.get("temp");
        Boolean high = (Boolean) td.get("high");
        Boolean leak = (Boolean) td.get("leak");
        Boolean low = (Boolean) td.get("low");
        Boolean hot = (Boolean) td.get("hot");
        Boolean lowBattery = (Boolean) td.get("lowBattery");
        Integer updateCnt = (Integer) td.get("updateCnt");
        Date updateTime = (Date) td.get("updateTime");
        Date changeTime = (Date) td.get("changeTime");
        if (idx != null && idx >= 1 && idx <= 4) {
            mTireDataViews.get("low" + idx).setVisibility(low ? View.VISIBLE : View.INVISIBLE);
            mTireDataViews.get("high" + idx).setVisibility(high ? View.VISIBLE : View.INVISIBLE);
            mTireDataViews.get("hot" + idx).setVisibility(hot ? View.VISIBLE : View.INVISIBLE);
            mTireDataViews.get("leak" + idx).setVisibility(leak ? View.VISIBLE : View.INVISIBLE);
            mTireDataViews.get("lowBattery" + idx).setVisibility(lowBattery ? View.VISIBLE : View.INVISIBLE);
            SimpleDateFormat df = new SimpleDateFormat("mm:ss", Locale.CHINESE);
            ((TextView) mTireDataViews.get("data" + idx)).setText(String.format(Locale.CHINESE, "% 5d [%s/%s]: %2.3fbar  % 2d度",
                    updateCnt, df.format(updateTime), df.format(changeTime), pressure, temp));
        }
    }


    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service.  Because we have bound to a explicit
            // service that we know is running in our own process, we can
            // cast its IBinder to a concrete class and directly access it.
            mTireService = ((TireService.LocalBinder) service).getService();
            mHandler.sendEmptyMessage(MSG_INIT);
        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            // Because it is running in our same process, we should never
            // see this happen.
            mTireService = null;
        }
    };

    @Override
    public void newError(final ArrayList<String> errors) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mErrorAdaptor.clear();
                for (String s : errors) {
                    mErrorAdaptor.insert(s, 0);
                }
            }
        });
    }

    @Override
    public void newError(final String s) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mErrorAdaptor.insert(s, 0);
            }
        });
    }

    private MainHandler mHandler = new MainHandler(this);

    @Override
    public void newLog(final ArrayList<String> logs) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mLogAdaptor.clear();
                for (String s : logs) {
                    mLogAdaptor.insert(s, 0);
                }
            }
        });
    }

    @Override
    public void newLog(final String s) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mLogAdaptor.insert(s, 0);
            }
        });
    }

    static class MainHandler extends Handler {
        WeakReference<MainActivity> mMain;

        MainHandler(MainActivity svr) {
            mMain = new WeakReference<MainActivity>(svr);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            MainActivity main = mMain.get();
            if (main == null) {
                Log.d(TAG, "handleMessage: mMain is null, ignore msg: " + msg);
                return;
            }
            switch (msg.what) {
                case MSG_INIT:
                    main.init();
                    break;
                case MSG_UNINIT:
                    main.deInit();
                    break;
            }
        }
    }

    void init(){
        if (mTireService != null) {
            mTireService.registerErrorListener(MainActivity.this);
            mTireService.registerLogListener(MainActivity.this);
            ((TextView) findViewById(R.id.WorkState)).setText(mTireService.GetCurrentWorkState());
            ArrayList<HashMap<String, Object>> datas = mTireService.GetCurrentTireData();
            for (HashMap<String, Object> d : datas) {
                updateTireData(d);
            }
        }
    }
    void deInit(){
        if (mTireService != null) {
            mTireService.unRegisterErrorListener(MainActivity.this);
            mTireService.unRegisterLogListener(MainActivity.this);
        }
    }
}
