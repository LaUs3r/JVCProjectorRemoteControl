package com.remotecontrol.jvcprojectorremotecontrol;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.StrictMode;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.text.DecimalFormat;


public class MainActivity extends AppCompatActivity {

    private Socket socket;
    private int SERVER_PORT = 20554; // Port 20554 is a commonly used port for JVC beamers
    private String SERVER_IP = "";
    private String POWER_STATUS = ""; // Power status can be ON, OFF oder COOLING_DOWN
    private static final int SERVER_TIMEOUT = 3000;
    private OutputStream beamerOutputStream;
    private InputStream beamerInputStream;
    private Button statusIcon;
    private static final byte[] PJREQ = new byte[]  { 80, 74, 82, 69, 81 };
    private static final byte[] CONNECTION_CHECK = { (byte)0x21, (byte)0x89, (byte)0x01, (byte)0x00, (byte)0x00, (byte)0x0a };
    private static final byte[] POWER_CHECK =  { (byte)0x3F, (byte)0x89, (byte)0x01, (byte)0x50, (byte)0x57, (byte)0x0a };
    private static final byte[] HDMI_CHECK =  { (byte)0x3F, (byte)0x89, (byte)0x01, (byte)0x49, (byte)0x50, (byte)0x0a };
    private static final byte[] POWER_OFF =  { (byte)0x21, (byte)0x89, (byte)0x01, (byte)0x50, (byte)0x57, (byte)0x30, (byte)0x0a };
    private static final byte[] POWER_ON =  { (byte)0x21, (byte)0x89, (byte)0x01, (byte)0x50, (byte)0x57, (byte)0x31, (byte)0x0a };
    private static final byte[] HDMI_1_ON =  { (byte)0x21, (byte)0x89, (byte)0x01, (byte)0x49, (byte)0x50, (byte)0x36, (byte)0x0a };
    private static final byte[] HDMI_2_ON =  { (byte)0x21, (byte)0x89, (byte)0x01, (byte)0x49, (byte)0x50, (byte)0x37, (byte)0x0a };
    private static final byte[] PICTURE_MODE_NATURAL =  { (byte)0x21, (byte)0x89, (byte)0x01, (byte)0x50, (byte)0x4D, (byte)0x50, (byte)0x4D, (byte)0x30, (byte)0x33, (byte)0x0a };
    private static final byte[] PICTURE_MODE_CINEMA =  { (byte)0x21, (byte)0x89, (byte)0x01, (byte)0x50, (byte)0x4D, (byte)0x50, (byte)0x4D, (byte)0x30, (byte)0x31, (byte)0x0a };
    private static final byte[] PICTURE_MODE_HDR =  { (byte)0x21, (byte)0x89, (byte)0x01, (byte)0x50, (byte)0x4D, (byte)0x50, (byte)0x4D, (byte)0x30, (byte)0x34, (byte)0x0a };
    private static final byte[] PICTURE_MODE_FILM =  { (byte)0x21, (byte)0x89, (byte)0x01, (byte)0x50, (byte)0x4D, (byte)0x50, (byte)0x4D, (byte)0x30, (byte)0x30, (byte)0x0a };
    private static final byte[] PICTURE_MODE_THX =  { (byte)0x21, (byte)0x89, (byte)0x01, (byte)0x50, (byte)0x4D, (byte)0x50, (byte)0x4D, (byte)0x30, (byte)0x36, (byte)0x0a };
    private static final byte[] PICTURE_MODE_USER1 =  { (byte)0x21, (byte)0x89, (byte)0x01, (byte)0x50, (byte)0x4D, (byte)0x50, (byte)0x4D, (byte)0x30, (byte)0x43, (byte)0x0a };
    private final static String SUCCESSFUL_CONNECTION_REPLY = "06890100000A";
    private final static String POWERED_ON = "4089015057310A";
    private final static String POWERED_OFF = "4089015057300A";
    private final static String COOLING_DOWN = "4089015057320A";
    private final static String HDMI_1 = "4089014950360A";
    private final static String HDMI_2 = "4089014950370A";
    private static final int CONNECTION_CHECK_INTERVAL = 30000;
    Handler handler = new Handler();
    Runnable runnable;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);
        // Sets title by getting @string/app_name value
        setTitle(this.getApplicationInfo().loadLabel(this.getPackageManager()).toString());
    }

    @Override
    protected void onResume() {
        // Create a thread in to check the connection every 'CONNECTION_CHECK_INTERVAL' seconds
        handler.postDelayed(runnable = () -> {
            handler.postDelayed(runnable,CONNECTION_CHECK_INTERVAL);
            try {
                checkConnection();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }, CONNECTION_CHECK_INTERVAL);
        super.onResume();
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(runnable);
        super.onPause();
        saveIPAddress(this.SERVER_IP);
        savePort(this.SERVER_PORT);
    }

    @Override
    protected void onStart()
    {
        try {
            statusIcon = findViewById(R.id.connectionStatusIcon);
            readSavedIPAddress();
            readSavedPort();
            checkConnection();
        } catch (IOException e) {
            e.printStackTrace();
        }
        super.onStart();
    }


    /**
     * Performs a 3-way TCP handshake to the beamer.
     * step 1: PJ_OK
     * step 2: PJREQ
     * step 3: PJACK
     */
    private boolean threeWayHandshake() throws IOException {
        boolean bool3WayHandshake;

        // Try to connect to the beamer
        try {
            // Re-initiate the socket
            socket = new Socket();
            SocketAddress socketAddress = new InetSocketAddress(SERVER_IP, SERVER_PORT);
            socket.connect(socketAddress, SERVER_TIMEOUT);
            beamerInputStream = socket.getInputStream();

            // Create buffer for the 3-way TCP handshake
            byte[] inputBuffer = new byte[5];
            //noinspection ResultOfMethodCallIgnored
            beamerInputStream.read(inputBuffer);

            // Wait for the PJ_OK packet (in decimal 80 74 95 79 75)
            String response = new String(inputBuffer);

            if (response.equals("PJ_OK")) {
                //Send response PJREQ (in decimal 80 74 82 69 81)
                beamerOutputStream = socket.getOutputStream();
                beamerOutputStream.write(PJREQ);

                // Wait for PJACK packet
                //noinspection ResultOfMethodCallIgnored
                beamerInputStream.read(inputBuffer);
                response = new String(inputBuffer);
                if (response.equals("PJACK")) {
                    bool3WayHandshake =  true;
                    // Do NOT close the socket!
                } else {
                    socket.close();
                    bool3WayHandshake = false;
                }
            } else {
                // PJ_OK not received
                socket.close();
                bool3WayHandshake = false;
            }
        } catch (ConnectException | SocketTimeoutException | UnknownHostException e) {
            socket.close();
            e.printStackTrace();
            bool3WayHandshake = false;
        }
        return bool3WayHandshake;
    }

    /**
     * Checks which HDMi port is selected. This only works if the beamer is ON
     * The active HDMI port is highlighted with a "green" background.
     * @throws ConnectException, SocketTimeoutException, UnknownHostException
     */
    public void checkHDMIInput() throws IOException {
        if (socket != null && POWER_STATUS.equals("ON")) {
            try {
                beamerOutputStream.write(HDMI_CHECK);

                byte[] inputBuffer = new byte[6];
                //noinspection ResultOfMethodCallIgnored
                beamerInputStream.read(inputBuffer);

                inputBuffer = new byte[7];
                //noinspection ResultOfMethodCallIgnored
                beamerInputStream.read(inputBuffer);

                String reply = BinAscii.hexlify(inputBuffer);

                Button hdmi_1 = findViewById(R.id.hdmi_1);
                Button hdmi_2 = findViewById(R.id.hdmi_2);

                switch (reply) {
                    case HDMI_1:
                        // Input is on HDMI 1
                        hdmi_1.setBackgroundColor(Color.GREEN);
                        hdmi_2.setBackgroundColor(getColor(R.color.holo_blue_dark));
                        break;
                    case HDMI_2:
                        // Input is on HDMI 2
                        hdmi_1.setBackgroundColor(getColor(R.color.holo_blue_dark));
                        hdmi_2.setBackgroundColor(Color.GREEN);
                        break;
                }
            } catch (ConnectException | SocketTimeoutException | UnknownHostException e) {
                socket.close();
                e.printStackTrace();
            }
        } else {
            Button hdmi_1 = findViewById(R.id.hdmi_1);
            Button hdmi_2 = findViewById(R.id.hdmi_2);
            hdmi_1.setBackgroundColor(getColor(R.color.holo_blue_dark));
            hdmi_2.setBackgroundColor(getColor(R.color.holo_blue_dark));
        }
    }

    /**
     * Checks the power status of the beamer
     * !!! DO NOT CLOSE THE SOCKET IN THIS METHOD AS IT WILL BE CLOSED IN THE CONNECTION_CHECK
     * Reply is 4089015057xx0A where xx can have the following values:
     * xx = 30 --> Standby
     * xx = 31 --> power on
     * xx = 32 --> cooling down
     * @throws ConnectException, SocketTimeoutException, UnknownHostException
     */
    private void check_power() throws IOException {
        if (socket != null) {
            try {
                beamerOutputStream.write(POWER_CHECK);

                byte[] inputBuffer = new byte[6];
                //noinspection ResultOfMethodCallIgnored
                beamerInputStream.read(inputBuffer);

                inputBuffer = new byte[7];
                //noinspection ResultOfMethodCallIgnored
                beamerInputStream.read(inputBuffer);
                String reply = BinAscii.hexlify(inputBuffer);

                Button powerIcon = findViewById(R.id.powerStatusIcon);
                switch (reply) {
                    case POWERED_ON:
                        // Beamer is powered ON
                        powerIcon.setBackgroundColor(Color.GREEN);
                        POWER_STATUS = "ON";
                        break;
                    case POWERED_OFF:
                        // Beamer is powered OFF
                        powerIcon.setBackgroundColor(Color.RED);
                        POWER_STATUS = "OFF";
                        break;
                    case COOLING_DOWN:
                        // Beamer is cooling down
                        powerIcon.setBackgroundColor(Color.BLUE);
                        POWER_STATUS = "COOLING_DOWN";
                        break;
                }
            } catch (ConnectException | SocketTimeoutException | UnknownHostException e) {
                socket.close();
                e.printStackTrace();
            }
        }
    }

    /**
     * Check for a correct connection to the beamer.
     * 1. Perform 3-way TCP handshake
     * 2. Verify connection
     * @return connection status to the beamer
     */
    private boolean checkConnection() throws IOException {
        boolean bSuccessfulConnect;
        statusIcon =  findViewById(R.id.connectionStatusIcon);

        // Try to connect to the beamer
        try {
            if (this.threeWayHandshake() && socket != null) {
                System.out.println("checkConnection: " + new java.util.Date());
                /*
                 * Check for correct connection to the beamer
                 * For the expected reply the receive buffer needs to be increased to 6
                 * Device --> Beamer: 21 89 01 00 00 0A
                 * Beamer --> Device: 06 89 01 00 00 0A
                 */
                beamerOutputStream.write(CONNECTION_CHECK);
                byte[] inputBuffer = new byte[6];
                //noinspection ResultOfMethodCallIgnored
                beamerInputStream.read(inputBuffer);
                bSuccessfulConnect = BinAscii.hexlify(inputBuffer).equals(SUCCESSFUL_CONNECTION_REPLY);
                statusIcon.setBackgroundColor(Color.GREEN);

                // Check the power status of the beamer
                check_power();

                // Check the HDMI connection
                checkHDMIInput();

                // Close the socket to make sure that no open socket remains
                socket.close();
            } else {
                bSuccessfulConnect = false;
                statusIcon.setBackgroundColor(Color.RED);
            }
        } catch (ConnectException | SocketTimeoutException | UnknownHostException e) {
            socket.close();
            statusIcon.setBackgroundColor(Color.RED);
            e.printStackTrace();
            return false;
        }
        return bSuccessfulConnect;
    }

    /**
     * Power off the beamer
     * @param view current view
     * @throws ConnectException, SocketTimeoutException, UnknownHostException
     */
    public void power_off(View view) throws IOException {
        try {
            if (threeWayHandshake() && socket != null) {
                beamerOutputStream = socket.getOutputStream();

                //Switch off beamer
                beamerOutputStream.write(POWER_OFF);
                socket.close();
            }
        } catch (ConnectException | SocketTimeoutException | UnknownHostException e) {
            socket.close();
            e.printStackTrace();
        }
    }

    /**
     * Power on the beamer
     * @param view current view
     * @throws ConnectException, SocketTimeoutException, UnknownHostException
     */
    public void power_on(View view) throws IOException {
        try {
            if (threeWayHandshake() && socket != null) {
                beamerOutputStream = socket.getOutputStream();

                //Switch on beamer
                beamerOutputStream.write(POWER_ON);
                socket.close();
            }
        } catch (ConnectException | SocketTimeoutException | UnknownHostException e) {
            socket.close();
            e.printStackTrace();
        }
    }

    /**
     * Selects HDMI port 1.
     * This only works if the beamer is ON
     * @param view current view
     * @throws ConnectException, SocketTimeoutException, UnknownHostException
     */
    public void selectHdmi1(View view) throws IOException {
        try {
            if (threeWayHandshake() && socket != null && POWER_STATUS.equals("ON")) {
                beamerOutputStream = socket.getOutputStream();

                // Select HDMI port 1
                beamerOutputStream.write(HDMI_1_ON);
                byte[] inputBuffer = new byte[6];
                //noinspection ResultOfMethodCallIgnored
                beamerInputStream.read(inputBuffer);
                checkHDMIInput();
                socket.close();
            }
        } catch (ConnectException | SocketTimeoutException | IllegalStateException | UnknownHostException e) {
            socket.close();
            e.printStackTrace();
        }
    }

    /**
     * Selects HDMI port 2.
     * This only works if the beamer is ON
     * @param view current view
     * @throws ConnectException, SocketTimeoutException, UnknownHostException
     */
    public void selectHdmi2(View view) throws IOException {
        try {
            if (threeWayHandshake() && socket != null && POWER_STATUS.equals("ON")) {
                // Send response PJREQ (in decimal 80 74 82 69 81)
                beamerOutputStream = socket.getOutputStream();

                // Select HDMI port 2
                beamerOutputStream.write(HDMI_2_ON);
                byte[] inputBuffer = new byte[6];
                //noinspection ResultOfMethodCallIgnored
                beamerInputStream.read(inputBuffer);
                checkHDMIInput();
                socket.close();
            }
        } catch (ConnectException | SocketTimeoutException | IllegalStateException | UnknownHostException e) {
            socket.close();
            e.printStackTrace();
        }
    }


    /**
     * Sets the picture mode to User1
     * @param view this view
     * @throws ConnectException, SocketTimeoutException, UnknownHostException
     */
    public void setPictureModeUser1(View view) throws IOException {
        try {
            if (threeWayHandshake() && socket != null && POWER_STATUS.equals("ON")) {
                // Send response PJREQ (in decimal 80 74 82 69 81)
                beamerOutputStream = socket.getOutputStream();

                // Set picture mode
                beamerOutputStream.write(PICTURE_MODE_USER1);
                socket.close();
            }
        } catch (ConnectException | SocketTimeoutException | UnknownHostException e) {
            socket.close();
            e.printStackTrace();
        }
    }

    /**
     * Sets the picture mode to THX
     * @param view this view
     * @throws ConnectException, SocketTimeoutException, UnknownHostException
     */
    public void setPictureModeTHX(View view) throws IOException {
        try {
            if (threeWayHandshake() && socket != null && POWER_STATUS.equals("ON")) {
                // Send response PJREQ (in decimal 80 74 82 69 81)
                beamerOutputStream = socket.getOutputStream();

                // Set picture mode
                beamerOutputStream.write(PICTURE_MODE_THX);
                socket.close();
            }
        } catch (ConnectException | SocketTimeoutException | UnknownHostException e) {
            socket.close();
            e.printStackTrace();
        }
    }

    /**
     * Sets the picture mode to FILM
     * @param view this view
     * @throws ConnectException, SocketTimeoutException, UnknownHostException
     */
    public void setPictureModeFilm(View view) throws IOException {
        try {
            if (threeWayHandshake() && socket != null && POWER_STATUS.equals("ON")) {
                // Send response PJREQ (in decimal 80 74 82 69 81)
                beamerOutputStream = socket.getOutputStream();

                // Set picture mode
                beamerOutputStream.write(PICTURE_MODE_FILM);
                socket.close();
            }
        } catch (ConnectException | SocketTimeoutException | UnknownHostException e) {
            socket.close();
            e.printStackTrace();
        }
    }


    /**
     * Sets the picture mode to NATURAL
     * @param view this view
     * @throws ConnectException, SocketTimeoutException, UnknownHostException
     */
    public void setPictureModeNatural(View view) throws IOException {
        try {
            if (threeWayHandshake() && socket != null && POWER_STATUS.equals("ON")) {
                // Send response PJREQ (in decimal 80 74 82 69 81)
                beamerOutputStream = socket.getOutputStream();

                // Set picture mode
                beamerOutputStream.write(PICTURE_MODE_NATURAL);
                socket.close();
            }
        } catch (ConnectException | SocketTimeoutException | UnknownHostException e) {
            socket.close();
            e.printStackTrace();
        }
    }

    /**
     * Sets the picture mode to CINEMA
     * @param view this view
     * @throws ConnectException, SocketTimeoutException, UnknownHostException
     */
    public void setPictureModeCinema(View view) throws IOException {
        try {
            if (threeWayHandshake() && socket != null && POWER_STATUS.equals("ON")) {
                // Send response PJREQ (in decimal 80 74 82 69 81)
                beamerOutputStream = socket.getOutputStream();

                // Set picture mode
                beamerOutputStream.write(PICTURE_MODE_CINEMA);
                socket.close();
            }
        } catch (ConnectException | SocketTimeoutException | UnknownHostException e) {
            socket.close();
            e.printStackTrace();
        }
    }

        /**
         * Sets the picture mode to HDR
         * @param view this view
         * @throws ConnectException, SocketTimeoutException, UnknownHostException
         */
        public void setPictureModeHDR(View view) throws IOException {
            try {
                if (threeWayHandshake() && socket != null && POWER_STATUS.equals("ON")) {
                    // Send response PJREQ (in decimal 80 74 82 69 81)
                    beamerOutputStream = socket.getOutputStream();

                    // Set picture mode
                    beamerOutputStream.write(PICTURE_MODE_HDR);
                    socket.close();
                }
            } catch (ConnectException | SocketTimeoutException | UnknownHostException e) {
                socket.close();
                e.printStackTrace();
            }
    }


    /**
     *
     * @param view current view
     */
    public void showSettingsDialog(View view) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        Context context = this;
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        builder.setTitle("Enter IP address and port");

        // Add a TextView here for the "Title" label, as noted in the comments
        final IPAddressText ipAddress = new IPAddressText(context);
        ipAddress.setHint("IP Address");
        layout.addView(ipAddress); // Notice this is an add method

        // Add another TextView here for the "Description" label
        final EditText port = new EditText(context);
        port.setInputType(InputType.TYPE_CLASS_NUMBER);
        port.setHint("Port Number");
        layout.addView(port);

        if (!SERVER_IP.equals("")) ipAddress.setText(SERVER_IP);
        if (SERVER_PORT != 0) port.setText(new DecimalFormat("#").format(SERVER_PORT));

        builder.setView(layout);

        // Add the buttons
        builder.setPositiveButton("OK", (dialog, which) -> {
            if (ipAddress.getText() != null) { SERVER_IP = ipAddress.getText().toString(); }
            SERVER_PORT = Integer.parseInt(port.getText().toString());
            saveIPAddress(SERVER_IP);
            savePort(SERVER_PORT);
            try {
                if (checkConnection() ) { statusIcon.setBackgroundColor(Color.GREEN); } else {
                    statusIcon.setBackgroundColor(Color.RED);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    /**
     * Saves the given IP address from the settings dialog
     * @param IPAddress of the beamer
     */
   private void saveIPAddress(String IPAddress) {
       SharedPreferences sharedPref = getSharedPreferences("JVC BeamerControl", Context.MODE_PRIVATE);
       SharedPreferences.Editor editor = sharedPref.edit();
       editor.putString("SERVER_IP", IPAddress);
       editor.apply();
   }

    /**
     * Saves the given port from the settings dialog
     * @param port of the beamer
     */
    private void savePort(int port) {
        SharedPreferences sharedPref = getSharedPreferences("JVC BeamerControl", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putInt("SERVER_PORT", port);
        editor.apply();
    }

    /**
     * Reads the last given IP address to the global variable to be used in the app
     */
    private void readSavedIPAddress() {
        SharedPreferences sharedPref = getSharedPreferences("JVC BeamerControl", Context.MODE_PRIVATE);
        SERVER_IP = sharedPref.getString("SERVER_IP", SERVER_IP);
    }

    /**
     * Reads the last given IP address to the global variable to be used in the app
     */
    private void readSavedPort() {
        SharedPreferences sharedPref = getSharedPreferences("JVC BeamerControl", Context.MODE_PRIVATE);
        SERVER_PORT = sharedPref.getInt("SERVER_PORT", SERVER_PORT);
    }
}
