/**
 * Copyright 2019 Esri
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.mbcoder.iot.gpslogger;

import com.esri.arcgisruntime.concurrent.Job;
import com.esri.arcgisruntime.data.Feature;
import com.esri.arcgisruntime.data.Geodatabase;
import com.esri.arcgisruntime.data.GeodatabaseFeatureTable;
import com.esri.arcgisruntime.data.SyncModel;
import com.esri.arcgisruntime.geometry.Envelope;
import com.esri.arcgisruntime.geometry.Point;
import com.esri.arcgisruntime.geometry.SpatialReferences;
import com.esri.arcgisruntime.location.NmeaLocationDataSource;
import com.esri.arcgisruntime.mapping.view.MapView;
import com.esri.arcgisruntime.tasks.geodatabase.GenerateGeodatabaseJob;
import com.esri.arcgisruntime.tasks.geodatabase.GenerateGeodatabaseParameters;
import com.esri.arcgisruntime.tasks.geodatabase.GeodatabaseSyncTask;
import com.pi4j.Pi4J;
import com.pi4j.context.Context;
import com.pi4j.io.serial.Baud;
import com.pi4j.io.serial.FlowControl;
import com.pi4j.io.serial.Parity;
import com.pi4j.io.serial.Serial;
import com.pi4j.io.serial.StopBits;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class GPS_Logger extends Application {

    private Context pi4j;
    private Serial serial;
    private SerialReader serialReader;
    private String featureLayerURL = "https://services1.arcgis.com/6677msI40mnLuuLr/arcgis/rest/services/GPS_Tracks/FeatureServer";

    private Geodatabase geodatabase;
    private GeodatabaseFeatureTable table;
    private GenerateGeodatabaseJob generateGeodatabaseJob;
    private GeodatabaseSyncTask syncTask;

    private Feature latestPosition;  // the latest position read from the GPS
    private boolean featureUpdated = false;  // flag set to true every time we update the latestPosition from the GPS.

    private Timer loggingTimer; // timer for logging to local geodatabase

    public static void main(String[] args) {

        Application.launch(args);
    }

    @Override
    public void start(Stage stage) {

        // set the title and size of the stage and show it
        stage.setTitle("GPS Logger");
        stage.setWidth(800);
        stage.setHeight(700);
        stage.show();

        // create a JavaFX scene with a stack pane as the root node and add it to the scene
        StackPane stackPane = new StackPane();
        Scene scene = new Scene(stackPane);
        stage.setScene(scene);

        HBox hBox = new HBox();
        stackPane.getChildren().add(hBox);
        Button getGDB = new Button("get gdb");
        getGDB.setOnAction(event -> {
            downloadOfflineDB();
        });
        hBox.getChildren().add(getGDB);

        Button openGDB = new Button("open gdb");
        openGDB.setOnAction(event -> {
            System.out.println("opening gdb");

            openGeodatabase();

        });
        hBox.getChildren().add(openGDB);

        Button btnAddFeature = new Button("add feature");
        btnAddFeature.setOnAction(event -> {
            System.out.println("adding feature");
            addFeature();
        });
        hBox.getChildren().add(btnAddFeature);

        Button btnSync = new Button("Sync");
        btnSync.setOnAction(event -> {
            System.out.println("sync");
            System.out.println("has local edits " + table.hasLocalEdits());
            syncGeodatabase();

        });
        hBox.getChildren().add(btnSync);

        Button btnStartGPSUpdate = new Button("start GPS");
        btnStartGPSUpdate.setOnAction(event -> {
            startGPSUpdate();
        });
        hBox.getChildren().add(btnStartGPSUpdate);

        Button btnStopGPSUpdate = new Button("stop GPS");
        btnStopGPSUpdate.setOnAction(event -> {

        });


        Button btnStartLogger = new Button("start logging");
        btnStartLogger.setOnAction(event -> {
            startLogging();
        });
        hBox.getChildren().add(btnStartLogger);
    }

    private void startLogging() {
        loggingTimer = new Timer();

        loggingTimer.schedule( new TimerTask() {
            public void run() {
                // log position to db if there is a new gps update
                System.out.println("logging triggered after 10 seconds");

                if (featureUpdated) {
                    System.out.println("logging new position");
                    table.addFeatureAsync(latestPosition);

                    System.out.println("changes in db? " + table.hasLocalEdits());

                    // flag we've read it
                    featureUpdated = false;
                }

            }
        }, 1000, 10000);
    }


    private void startGPSUpdate() {
        //start listening to serial port to get NMEA data
        NmeaLocationDataSource nmeaLocationDataSource = new NmeaLocationDataSource();
        var nmeaFuture = nmeaLocationDataSource.startAsync();
        nmeaFuture.addDoneListener(()-> {
            initGPS(nmeaLocationDataSource);

            System.out.println("adding location changed listener");
            // listener for location updates
            nmeaLocationDataSource.addLocationChangedListener(listener -> {
                //System.out.println("pos :" + listener.getLocation().getPosition());
                //System.out.println("speed :" + listener.getLocation().getVelocity());
                //System.out.println("direction :" + listener.getLocation().getCourse());
                System.out.print(".");

                // create default attributes for the feature
                Map<String, Object> attributes = new HashMap<>();
                attributes.put("TrackID", "GPS Test");
                attributes.put("Speed", listener.getLocation().getVelocity());
                attributes.put("Heading", listener.getLocation().getCourse());

                Point latestPoint = new Point(-1,53, SpatialReferences.getWgs84());  //listener.getLocation().getPosition();

                // update the latest position feature
                latestPosition = table.createFeature(attributes, latestPoint);

                //System.out.println("feature created " + latestPoint.toString());
                //System.out.println("feature attributes  " + latestPosition.getAttributes());

                // set flag to say there is a position update
                featureUpdated = true;
            });
        });
    }

    private void syncGeodatabase() {
        syncTask = new GeodatabaseSyncTask(featureLayerURL);

        syncTask.loadAsync();
        syncTask.addDoneLoadingListener(()-> {
            System.out.println("sync task load status " + syncTask.getLoadStatus());

            var syncParamsFuture =  syncTask.createDefaultSyncGeodatabaseParametersAsync(geodatabase);
            syncParamsFuture.addDoneListener(()-> {
                try {
                    var syncJob  = syncTask.syncGeodatabase(syncParamsFuture.get(), geodatabase);
                    syncJob.start();
                    syncJob.addJobDoneListener(()-> {
                        System.out.println("sync job done status " + syncJob.getStatus());
                    });

                    syncJob.addProgressChangedListener(()-> {
                        System.out.println("progress!");
                    });
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } catch (ExecutionException e) {
                    throw new RuntimeException(e);
                }
            });

        });
    }

    private void addFeature() {

        double speed = 11.11;
        double heading = 91.11;
        // create default attributes for the feature
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("TrackID", "T1");
        attributes.put("Speed", speed);
        attributes.put("Heading", heading);

        Point point = new Point(1,51, SpatialReferences.getWgs84());

        Feature feature = table.createFeature(attributes, point);

        // check if feature can be added to feature table
        if (table.canAdd()) {
            // add the new feature to the feature table and to server
            var future = table.addFeatureAsync(feature);
            future.addDoneListener(() -> {
                System.out.println("added feature done");
                System.out.println("local edits " + table.hasLocalEdits());
            });

        } else {
            System.out.println("Cannot add a feature to this feature table");
        }

    }

    private void openGeodatabase() {
        geodatabase = new Geodatabase("./gpsdata.geodatabase");

        geodatabase.loadAsync();
        geodatabase.addDoneLoadingListener(()-> {
            System.out.println("gdb load status :" + geodatabase.getLoadStatus());

            table = geodatabase.getGeodatabaseFeatureTableByServiceLayerId(0);
            table.loadAsync();
            table.addDoneLoadingListener(()-> {
                System.out.println("opening table " + table.getDisplayName());
                for (var field : table.getFields()) {
                    System.out.println("field : " + field.getName());
                }
                System.out.println("local edits " + table.hasLocalEdits());
            });
        });
    }

    private void downloadOfflineDB() {
        System.out.println("downloading gdb");

        syncTask = new GeodatabaseSyncTask(featureLayerURL);

        syncTask.loadAsync();
        syncTask.addDoneLoadingListener(()-> {
            System.out.println("load status " + syncTask.getLoadStatus());

            Envelope envelope = new Envelope(0,0,0,0, SpatialReferences.getWebMercator());

            var paramsFuture = syncTask.createDefaultGenerateGeodatabaseParametersAsync(envelope);
            paramsFuture.addDoneListener(()-> {
                System.out.println("params loaded ");
                try {
                    GenerateGeodatabaseParameters parameters = paramsFuture.get();
                    var layerOptions = parameters.getLayerOptions();
                    for (var option : layerOptions) {
                        System.out.println("layer option " + option.getLayerId());
                    }

                    generateGeodatabaseJob = syncTask.generateGeodatabase(parameters, "./gpsdata.geodatabase");

                    generateGeodatabaseJob.start();
                    generateGeodatabaseJob.addJobDoneListener(()-> {
                        System.out.println("job status :" + generateGeodatabaseJob.getStatus());

                        if (generateGeodatabaseJob.getStatus() == Job.Status.FAILED) {
                            System.out.println("error :" + generateGeodatabaseJob.getError().getMessage());
                        }
                    });
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } catch (ExecutionException e) {
                    throw new RuntimeException(e);
                }
            });
        });
    }

    private void initGPS(NmeaLocationDataSource nmeaLocationDataSource) {
        System.out.println("Starting serial...");

        pi4j = Pi4J.newAutoContext();

        serial = pi4j.create(Serial.newConfigBuilder(pi4j)
            .baud(Baud._4800)
            .dataBits_8()
            .parity(Parity.NONE)
            .stopBits(StopBits._1)
            .flowControl(FlowControl.NONE)
            .id("gps")
            .provider("pigpio-serial")
            .device("/dev/ttyUSB0")
            .build());
        serial.open();

        Runnable runnable = () -> {
            System.out.println("Waiting till serial port is open");
            while (!serial.isOpen()) {
                try {
                    Thread.sleep(250);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }

            //opened now!
            System.out.println("serial port is open!");

            // Start a thread to handle the incoming data from the serial port
            serialReader = new SerialReader(serial, nmeaLocationDataSource);
            Thread serialReaderThread = new Thread(serialReader, "SerialReader");
            serialReaderThread.setDaemon(true);
            serialReaderThread.start();
        };
        runnable.run();
    }

    /**
     * Stops and releases all resources used in application.
     */
    @Override
    public void stop() {
        if (serialReader != null) serialReader.stopReading();
        if (loggingTimer != null) loggingTimer.cancel();
    }
}
