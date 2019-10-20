package com.example.loomoonazure.util;

import android.util.Log;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import android.os.Handler;

public class TeleOps {
    private static final String TAG = "TeleOps";

    private Robot robot;
    private Telemetry telemetry;

    private Socket socket;

    private Timer telemetryLowFreqTimer;
    private TimerTask telemetryLowFreqTask;

    private Timer telemetryHighFreqTimer;
    private TimerTask telemetryHighFreqTask;


    private OutputStream out;
    private InputStream in;
    private Handler handler;
    private int CONNECTION_OPEN;
    private int CONNECTION_CLOSED;


    Thread operationsThread;
    byte[] operationBuffer = new byte[10024];
    boolean stopped = false;

    public TeleOps(Handler handler, int CONNECTION_OPEN, int CONNECTION_CLOSED, Robot robot, Telemetry telemetry) {
        this.handler = handler;
        this.CONNECTION_OPEN = CONNECTION_OPEN;
        this.CONNECTION_CLOSED = CONNECTION_CLOSED;
        this.robot = robot;
        this.telemetry = telemetry;
    }

    public synchronized void connect(String server, int port) throws Exception {

        try {
            InetAddress serverAddr = InetAddress.getByName(server);
            this.socket = new Socket(serverAddr, port);
            Log.d(TAG, String.format("Socket created to %s:%d", server, port));

            this.out = this.socket.getOutputStream();
            this.in = this.socket.getInputStream();
            this.handler.sendEmptyMessage(this.CONNECTION_OPEN);
        }catch (Exception e)
        {
            Log.e(TAG, "Exception connecting", e);
            throw new Exception(e);
        }
    }

    public synchronized void restart(String server, int port) {

    }

    public synchronized void start(String server, int port) {
        Log.d(TAG, String.format("start threadId=%d", Thread.currentThread().getId()));

        TeleOps that = this;

        new Thread() {
            @Override
            public void run() {
                int highCadence = 100;
                int lowCadence = 5;

                try {
                    that.connect(server, port);

                    // Start the pulling video image task
                    telemetry.startPullingVideoImage(80);

                } catch(Exception e) {
                    Log.e(TAG, "Exception connecting", e);
                }

                that.telemetryHighFreqTimer = new Timer();
                that.telemetryHighFreqTask = new TimerTask() {
                    long lastSentVideoAt = 0;

                    @Override
                    public void run() {
                        //Log.d(TAG, String.format("start-telemetry threadId=%d", Thread.currentThread().getId()));

                        synchronized (that) {
                            if (that.stopped) {
                                Log.d(TAG, "start-telemetry TeleOps has been stopped");
                                return;
                            }

                            if (that.socket.isOutputShutdown()) {
                                Log.d(TAG, "start-telemetry socket closed");
                                return;
                            }
                        }

                        try {
                            long currentTime = System.currentTimeMillis();

                            //start
                            long lTelemetryStartTime = System.nanoTime();
                            ArrayList<String> data = that.telemetry.getLive();

                            // Send video image at lower rate
                            if(currentTime - highCadence > lastSentVideoAt) {
                                ArrayList<String> videoImageData = that.telemetry.getVideoImage();

                                data.addAll(videoImageData);

                                lastSentVideoAt = currentTime;
                            }

                            //end
                            long lTelemetryEndTime = System.nanoTime();

                            //time elapsed
                            long telemetryGetheringTime = lTelemetryEndTime - lTelemetryStartTime;


                            long lTelemetryTcpStartTime = System.nanoTime();
                            for (String packet : data) {
                                that.out.write(packet.getBytes());
                            }
                            long lTelemetryTcpEndTime = System.nanoTime();
                            //time elapsed
                            long telemetryTcpRunningTime = lTelemetryTcpEndTime - lTelemetryTcpStartTime;


                            //Log.d(TAG, String.format("Telemetry running time. Telemetry gathering: %d. Telemetry TCP: %d.", telemetryGetheringTime/1000000, telemetryTcpRunningTime/1000000));
                        } catch(Exception e) {
                            Log.e(TAG, "Exception sending", e);
                        }
                    }
                };
                that.telemetryHighFreqTimer.schedule(that.telemetryHighFreqTask, lowCadence, lowCadence);


                that.operationsThread = new Thread() {
                    @Override
                    public void run() {
                        try {
                            int len;
                            String content;
                            JsonElement element;
                            JsonObject command;
                            JsonParser parser = new JsonParser();
                            while (true) {
                                synchronized (that) {
                                    if (that.stopped) {
                                        Log.d(TAG, "start-operations TeleOps has been stopped");
                                        that.connect(server, port);

                                        return;
                                    }
                                    if (that.socket.isInputShutdown()) {
                                        Log.d(TAG, "start-operations socket closed");
                                        that.connect(server, port);

                                        return;
                                    }
                                }

                                len = that.in.read(that.operationBuffer);
                                Log.d(TAG, String.format("Read %d bytes", len));

                                if (len == -1) {
                                    that.stop();

                                    return;
                                }

                                content = new String(that.operationBuffer, 0, len);

                                int start = 0;
                                int end = 0;
                                int state = 0;
                                String msg = "";
                                do {
                                    for (int i = 0; i < content.length() && end == 0; i += 1) {
                                        switch (content.charAt(i)) {
                                            case '{':
                                                if (state == 0) {
                                                    start = i;
                                                }
                                                state += 1;
                                                break;
                                            case '}':
                                                state -= 1;
                                                if (state == 0) {
                                                    end = i;
                                                } else if (state < 0) {
                                                    state = 0;
                                                }
                                                break;
                                            default:
                                                break;
                                        }
                                    }

                                    try {

                                        if (state == 0 && end != 0) {
                                            msg = content.substring(start, end + 1);
                                            content = content.substring(end + 1);
                                            if (!content.isEmpty()) {
                                                Log.d(TAG, "DEBUG put breakpoint here");
                                            }
                                        } else {
                                            content = "";
                                        }

                                        if (!msg.isEmpty()) {
                                            Log.d(TAG, "Tele op msg received");

                                            element = parser.parse(msg);

                                            if (element.isJsonObject()) {
                                                command = element.getAsJsonObject();
                                                if (!command.has("type"))
                                                    return;

                                                switch (command.get("type").getAsString()) {
                                                    case "move":
                                                        float linear = command.get("linear").getAsFloat();
                                                        float angular = command.get("angular").getAsFloat();

                                                        RobotAction moveRa = RobotAction.getMovement(Robot.MOVEMENT_BEHAVIOR_MOVE_VELOCITY, linear, angular);
                                                        that.robot.actionDo(moveRa);
                                                        break;
                                                    case "greeting":
                                                        that.robot.actionSay("Hello my friend");
                                                        break;
                                                    case "headReset":
                                                        RobotAction headResetRa = RobotAction.getLook(0, 0);

                                                        that.robot.actionDo(headResetRa);
                                                        break;
                                                    case "head":
                                                        float yaw = command.get("yaw").getAsFloat();
                                                        float pitch = command.get("pitch").getAsFloat();

                                                        RobotAction headRa = RobotAction.getHead(Robot.HEAD_BEHAVIOR_MOVE_VELOCITY, yaw, pitch);

                                                        that.robot.actionDo(headRa);
                                                        break;
                                                }
                                            }
                                        }
                                    } catch (Exception e) {
                                        Log.e(TAG, "Exception parsing", e);

                                        // Clean the content if there is an exception
                                        content = "";
                                    }
                                } while (!content.isEmpty());
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Exception receiving", e);
                        }
                    }
                };
                that.operationsThread.start();
            }
        }.start();
    }

    public synchronized void stop() {
        Log.d(TAG, String.format("start threadId=%d", Thread.currentThread().getId()));

        stopped = true;
        try {
            if (telemetryHighFreqTimer != null) {
                telemetryHighFreqTimer.cancel();
            }

            if (telemetryLowFreqTimer != null) {
                telemetryLowFreqTimer.cancel();
            }

            if (socket != null) {
                socket.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception closing", e);
        }

        socket = null;
        out = null;
        in  = null;

        telemetryHighFreqTimer = null;
        telemetryLowFreqTimer = null;

        telemetryHighFreqTask = null;
        telemetryLowFreqTask = null;

        operationsThread = null;
    }
}
