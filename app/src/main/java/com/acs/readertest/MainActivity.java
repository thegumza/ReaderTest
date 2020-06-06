/*
 * Copyright (C) 2011-2013 Advanced Card Systems Ltd. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of Advanced
 * Card Systems Ltd. ("Confidential Information").  You shall not disclose such
 * Confidential Information and shall use it only in accordance with the terms
 * of the license agreement you entered into with ACS.
 */

package com.acs.readertest;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import com.acs.smartcard.Reader;
import com.acs.smartcard.Reader.OnStateChangeListener;

import java.io.UnsupportedEncodingException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Test program for ACS smart card readers.
 *
 * @author Godfrey Chung
 * @version 1.1.1, 16 Apr 2013
 */
public class MainActivity extends Activity implements OnStateChangeListener {

    private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";

    private static final String[] powerActionStrings = {"Power Down",
            "Cold Reset", "Warm Reset"};

    private static final String[] stateStrings = {"Unknown", "Absent",
            "Present", "Swallowed", "Powered", "Negotiable", "Specific"};

    private UsbManager mManager;
    private Reader mReader;
    private PendingIntent mPermissionIntent;

    private static final int MAX_LINES = 25;
    private TextView mResponseTextView;
    private Button mOpenButton;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {

            String action = intent.getAction();

            if (ACTION_USB_PERMISSION.equals(action)) {

                synchronized (this) {

                    final UsbDevice device = intent
                            .getParcelableExtra(UsbManager.EXTRA_DEVICE);

                    if (intent.getBooleanExtra(
                            UsbManager.EXTRA_PERMISSION_GRANTED, false)) {

                        if (device != null) {

                            Handler handler = new Handler();
                            handler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    new OpenTask().execute(device);
                                }
                            }, 1000);
                            handler = new Handler();
                            handler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    PowerParams paramsPower = new PowerParams();
                                    paramsPower.slotNum = mReader.getNumSlots();
                                    paramsPower.action = Reader.CARD_WARM_RESET;
                                    new PowerTask().execute(paramsPower);
                                }
                            }, 1000);
                            handler = new Handler();
                            handler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    SetProtocolParams paramsProtocol = new SetProtocolParams();
                                    paramsProtocol.slotNum = mReader.getNumSlots();
                                    paramsProtocol.preferredProtocols = Reader.PROTOCOL_T0;
                                    new SetProtocolTask().execute(paramsProtocol);
                                }
                            }, 1000);
                        }

                    } else {

                        logMsg("Permission denied for device "
                                + device.getDeviceName());

                    }
                }

            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {

                synchronized (this) {

                    UsbDevice device = intent
                            .getParcelableExtra(UsbManager.EXTRA_DEVICE);

                    if (device != null && device.equals(mReader.getDevice())) {
                        mReader.close();
                    }
                }
            }
        }
    };

    @Override
    public void onStateChange(int slotNum, int prevState, int currState) {

        if (prevState < Reader.CARD_UNKNOWN
                || prevState > Reader.CARD_SPECIFIC) {
            prevState = Reader.CARD_UNKNOWN;
        }

        if (currState < Reader.CARD_UNKNOWN
                || currState > Reader.CARD_SPECIFIC) {
            currState = Reader.CARD_UNKNOWN;
        }

        // Create output string
        final String outputString = "Slot " + slotNum + ": "
                + stateStrings[prevState] + " -> "
                + stateStrings[currState];

        // Show output
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                logMsg(outputString);
            }
        });
    }

    private class OpenTask extends AsyncTask<UsbDevice, Void, Exception> {

        @Override
        protected Exception doInBackground(UsbDevice... params) {

            Exception result = null;

            try {

                mReader.open(params[0]);

            } catch (Exception e) {

                result = e;
            }

            return result;
        }

        @Override
        protected void onPostExecute(Exception result) {

        }
    }

    private class PowerParams {

        public int slotNum;
        public int action;
    }

    private class PowerResult {

        public byte[] atr;
        public Exception e;
    }

    private class PowerTask extends AsyncTask<PowerParams, Void, PowerResult> {

        @Override
        protected PowerResult doInBackground(PowerParams... params) {

            PowerResult result = new PowerResult();

            try {

                result.atr = mReader.power(params[0].slotNum, params[0].action);

            } catch (Exception e) {

                result.e = e;
            }

            return result;
        }

        @Override
        protected void onPostExecute(PowerResult result) {

        }
    }

    private class SetProtocolParams {

        public int slotNum;
        public int preferredProtocols;
    }

    private class SetProtocolResult {

        public int activeProtocol;
        public Exception e;
    }

    private class SetProtocolTask extends
            AsyncTask<SetProtocolParams, Void, SetProtocolResult> {

        @Override
        protected SetProtocolResult doInBackground(SetProtocolParams... params) {

            SetProtocolResult result = new SetProtocolResult();

            try {

                result.activeProtocol = mReader.setProtocol(params[0].slotNum,
                        params[0].preferredProtocols);

            } catch (Exception e) {

                result.e = e;
            }

            return result;
        }

        @Override
        protected void onPostExecute(SetProtocolResult result) {

        }
    }

    private class TransmitParams {

        public int slotNum;
        public int controlCode;
        public String commandString;
    }

    private class TransmitProgress {

        public int controlCode;
        public byte[] command;
        public int commandLength;
        public byte[] response;
        public int responseLength;
        public Exception e;
    }

    private class TransmitTask extends
            AsyncTask<TransmitParams, TransmitProgress, Void> {

        @Override
        protected Void doInBackground(TransmitParams... params) {

            TransmitProgress progress = null;

            byte[] command = null;
            byte[] response = null;
            int responseLength = 0;
            int foundIndex = 0;
            int startIndex = 0;

            do {

                // Find carriage return
                foundIndex = params[0].commandString.indexOf('\n', startIndex);
                if (foundIndex >= 0) {
                    command = toByteArray(params[0].commandString.substring(
                            startIndex, foundIndex));
                } else {
                    command = toByteArray(params[0].commandString
                            .substring(startIndex));
                }

                // Set next start index
                startIndex = foundIndex + 1;

                response = new byte[300];
                progress = new TransmitProgress();
                progress.controlCode = params[0].controlCode;
                try {

                    if (params[0].controlCode < 0) {

                        // Transmit APDU
                        responseLength = mReader.transmit(params[0].slotNum,
                                command, command.length, response,
                                response.length);

                    } else {

                        // Transmit control command
                        responseLength = mReader.control(params[0].slotNum,
                                params[0].controlCode, command, command.length,
                                response, response.length);
                    }

                    progress.command = command;
                    progress.commandLength = command.length;
                    progress.response = response;
                    progress.responseLength = responseLength;
                    progress.e = null;

                } catch (Exception e) {

                    progress.command = null;
                    progress.commandLength = 0;
                    progress.response = null;
                    progress.responseLength = 0;
                    progress.e = e;
                }

                publishProgress(progress);

            } while (foundIndex >= 0);

            return null;
        }

        @Override
        protected void onProgressUpdate(TransmitProgress... progress) {

        }
    }

    private class ThaiTransmitTask extends
            AsyncTask<TransmitParams, TransmitProgress, Void> {

        @Override
        protected Void doInBackground(TransmitParams... params) {

            TransmitProgress progress = null;

            byte[] command = null;
            byte[] response = null;
            int responseLength = 0;
            int foundIndex = 0;
            int startIndex = 0;

            do {

                // Find carriage return
                foundIndex = params[0].commandString.indexOf('\n', startIndex);
                if (foundIndex >= 0) {
                    command = toByteArray(params[0].commandString.substring(
                            startIndex, foundIndex));
                } else {
                    command = toByteArray(params[0].commandString
                            .substring(startIndex));
                }

                // Set next start index
                startIndex = foundIndex + 1;

                response = new byte[300];
                progress = new TransmitProgress();
                progress.controlCode = params[0].controlCode;
                try {

                    if (params[0].controlCode < 0) {

                        // Transmit APDU
                        responseLength = mReader.transmit(params[0].slotNum,
                                command, command.length, response,
                                response.length);

                    } else {

                        // Transmit control command
                        responseLength = mReader.control(params[0].slotNum,
                                params[0].controlCode, command, command.length,
                                response, response.length);
                    }

                    progress.command = command;
                    progress.commandLength = command.length;
                    progress.response = response;
                    progress.responseLength = responseLength;
                    progress.e = null;

                } catch (Exception e) {

                    progress.command = null;
                    progress.commandLength = 0;
                    progress.response = null;
                    progress.responseLength = 0;
                    progress.e = e;
                }

                publishProgress(progress);

            } while (foundIndex >= 0);

            return null;
        }

        @Override
        protected void onProgressUpdate(TransmitProgress... progress) {
            if (progress.length > 0)
                logBuffer(progress[0].response, progress[0].responseLength);
        }
    }

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Get USB manager
        mManager = (UsbManager) getSystemService(Context.USB_SERVICE);

        // Initialize reader
        mReader = new Reader(mManager);
        mReader.setOnStateChangeListener(this);

        // Register receiver for USB permission
        mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(
                ACTION_USB_PERMISSION), 0);
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(mReceiver, filter);

        // Initialize response text view
        mResponseTextView = findViewById(R.id.main_text_view_response);
        mResponseTextView.setMovementMethod(new ScrollingMovementMethod());
        mResponseTextView.setMaxLines(MAX_LINES);
        mResponseTextView.setText("");

        // Initialize open button
        mOpenButton = findViewById(R.id.main_button_open);
        mOpenButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {

                // For each device
                for (UsbDevice device : mManager.getDeviceList().values()) {
                    if (mManager.hasPermission(device)) {
                        int slotNum = 0;
                        int actionNum = Reader.CARD_WARM_RESET;
                        PowerParams powerParams = new PowerParams();
                        powerParams.slotNum = slotNum;
                        powerParams.action = actionNum;
                        new PowerTask().execute(powerParams);
                        int preferredProtocols = Reader.PROTOCOL_T0;
                        SetProtocolParams protocolParams = new SetProtocolParams();
                        protocolParams.slotNum = slotNum;
                        protocolParams.preferredProtocols = preferredProtocols;
                        new SetProtocolTask().execute(protocolParams);
                        // If slot is selected
                        if (slotNum != Spinner.INVALID_POSITION) {
                            TransmitParams params = new TransmitParams();
                            params.slotNum = slotNum;
                            params.controlCode = -1;
                            params.commandString = "00A4040008A000000054480001";
                            new TransmitTask().execute(params);
                            params = new TransmitParams();
                            params.slotNum = slotNum;
                            params.controlCode = -1;
                            params.commandString = "80B0000402000D";
                            new TransmitTask().execute(params);
                            params = new TransmitParams();
                            params.slotNum = slotNum;
                            params.controlCode = -1;
                            params.commandString = "00C000000D";
                            new ThaiTransmitTask().execute(params);
                            params = new TransmitParams();
                            params.slotNum = slotNum;
                            params.controlCode = -1;
                            params.commandString = "80B000110200D1";
                            new TransmitTask().execute(params);
                            params = new TransmitParams();
                            params.slotNum = slotNum;
                            params.controlCode = -1;
                            params.commandString = "00C00000D1";
                            new ThaiTransmitTask().execute(params);
                            params = new TransmitParams();
                            params.slotNum = slotNum;
                            params.controlCode = -1;
                            params.commandString = "80B01579020064";
                            new TransmitTask().execute(params);
                            params = new TransmitParams();
                            params.slotNum = slotNum;
                            params.controlCode = -1;
                            params.commandString = "00C0000064";
                            new ThaiTransmitTask().execute(params);
                            params = new TransmitParams();
                            params.slotNum = slotNum;
                            params.controlCode = -1;
                            params.commandString = "80B00167020012";
                            new TransmitTask().execute(params);
                            params = new TransmitParams();
                            params.slotNum = slotNum;
                            params.controlCode = -1;
                            params.commandString = "00C0000012";
                            new ThaiTransmitTask().execute(params);
                        }
                    } else {
                        mManager.requestPermission(device,
                                mPermissionIntent);
                    }
                    break;
                }

            }
        });

        // Hide input window
        getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    }

    @Override
    protected void onDestroy() {

        // Close reader
        mReader.close();

        // Unregister receiver
        unregisterReceiver(mReceiver);

        super.onDestroy();
    }

    /**
     * Logs the message.
     *
     * @param msg the message.
     */
    private void logMsg(String msg) {

        DateFormat dateFormat = new SimpleDateFormat("[dd-MM-yyyy HH:mm:ss]: ");
        Date date = new Date();
        String oldMsg = mResponseTextView.getText().toString();

        mResponseTextView
                .setText(oldMsg + "\n" + dateFormat.format(date) + msg);

        if (mResponseTextView.getLineCount() > MAX_LINES) {
            mResponseTextView.scrollTo(0,
                    (mResponseTextView.getLineCount() - MAX_LINES)
                            * mResponseTextView.getLineHeight());
        }
    }

    /**
     * Logs the contents of buffer.
     *
     * @param buffer       the buffer.
     * @param bufferLength the buffer length.
     */
    private void logBuffer(byte[] buffer, int bufferLength) {
        try {
            logMsg(new String(buffer, "TIS620").trim().replace("#", "").replace(" ", ""));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    /**
     * Converts the HEX string to byte array.
     *
     * @param hexString the HEX string.
     * @return the byte array.
     */
    private byte[] toByteArray(String hexString) {

        int hexStringLength = hexString.length();
        byte[] byteArray = null;
        int count = 0;
        char c;
        int i;

        // Count number of hex characters
        for (i = 0; i < hexStringLength; i++) {

            c = hexString.charAt(i);
            if (c >= '0' && c <= '9' || c >= 'A' && c <= 'F' || c >= 'a'
                    && c <= 'f') {
                count++;
            }
        }

        byteArray = new byte[(count + 1) / 2];
        boolean first = true;
        int len = 0;
        int value;
        for (i = 0; i < hexStringLength; i++) {

            c = hexString.charAt(i);
            if (c >= '0' && c <= '9') {
                value = c - '0';
            } else if (c >= 'A' && c <= 'F') {
                value = c - 'A' + 10;
            } else if (c >= 'a' && c <= 'f') {
                value = c - 'a' + 10;
            } else {
                value = -1;
            }

            if (value >= 0) {

                if (first) {

                    byteArray[len] = (byte) (value << 4);

                } else {

                    byteArray[len] |= value;
                    len++;
                }

                first = !first;
            }
        }

        return byteArray;
    }

    /**
     * Converts the integer to HEX string.
     *
     * @param i the integer.
     * @return the HEX string.
     */
    private String toHexString(int i) {

        String hexString = Integer.toHexString(i);
        if (hexString.length() % 2 != 0) {
            hexString = "0" + hexString;
        }

        return hexString.toUpperCase();
    }

    /**
     * Converts the byte array to HEX string.
     *
     * @param buffer the buffer.
     * @return the HEX string.
     */
    private String toHexString(byte[] buffer) {

        String bufferString = "";

        for (int i = 0; i < buffer.length; i++) {

            String hexChar = Integer.toHexString(buffer[i] & 0xFF);
            if (hexChar.length() == 1) {
                hexChar = "0" + hexChar;
            }

            bufferString += hexChar.toUpperCase() + " ";
        }

        return bufferString;
    }
}
