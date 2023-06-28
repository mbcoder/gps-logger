/**
 * Copyright 2023 Esri
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
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
import com.esri.arcgisruntime.geometry.SpatialReferences;
import com.esri.arcgisruntime.loadable.LoadStatus;
import com.esri.arcgisruntime.location.NmeaLocationDataSource;
import com.esri.arcgisruntime.tasks.geodatabase.GenerateGeodatabaseJob;
import com.esri.arcgisruntime.tasks.geodatabase.GenerateGeodatabaseParameters;
import com.esri.arcgisruntime.tasks.geodatabase.GenerateLayerOption;
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

/**
 * An application for logging GPS locations to an offline geodatabase
 */
public class GPS_Logger extends Application {

    private Serial serial;
    private SerialReader serialReader;
    private final String featureLayerURL = "https://services1.arcgis.com/6677msI40mnLuuLr/arcgis/rest/services/GPS_Locations/FeatureServer";

    private Geodatabase geodatabase;
    private GeodatabaseFeatureTable table;
    private GenerateGeodatabaseJob generateGeodatabaseJob;
    private GeodatabaseSyncTask syncTask;
    private Button btnSync;
    private Button btnStartGPSUpdate;
    private Button btnStartLogger;

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
        stage.setHeight(100);

        // create a JavaFX scene with a stack pane as the root node and add it to the scene
        StackPane stackPane = new StackPane();
        Scene scene = new Scene(stackPane);
        stage.setScene(scene);
        stage.show();

        // Hbox for the buttons
        HBox hBox = new HBox();
        stackPane.getChildren().add(hBox);

        // Button to get offline geodatabase
        Button btnDownloadOfflineGDB = new Button("Get offline gdb");
        btnDownloadOfflineGDB.setOnAction(event -> downloadOfflineGDB());
        hBox.getChildren().add(btnDownloadOfflineGDB);

        // Button to open offline geodatabase
        Button btnOpenGDB = new Button("Open offline gdb");
        btnOpenGDB.setOnAction(event -> {
            System.out.println("opening gdb");
            openGeodatabase();
        });
        hBox.getChildren().add(btnOpenGDB);

        // Button to open the serial port and start processing GPS data
        btnStartGPSUpdate = new Button("start GPS");
        btnStartGPSUpdate.setOnAction(event -> startGPSUpdate());
        btnStartGPSUpdate.setDisable(true);
        hBox.getChildren().add(btnStartGPSUpdate);

        // Button to start recording the GPS position to offline gdb every 10 seconds
        btnStartLogger = new Button("start logging");
        btnStartLogger.setOnAction(event -> startLogging());
        btnStartLogger.setDisable(true);
        hBox.getChildren().add(btnStartLogger);

        // Button to sync gps positions collected into the hosted feature service
        btnSync = new Button("Sync data");
        btnSync.setOnAction(event -> {
            System.out.println("sync data");
            syncGeodatabase();
        });
        btnSync.setDisable(true);
        hBox.getChildren().add(btnSync);
    }

    /**
     * Method to start logging GPS data into the geodatabase.
     * <p>
     * GPS data is reported very frequently, but this method only records new
     * data at least every 10 seconds to limit the amount of data recorded.
     */
    private void startLogging() {
        loggingTimer = new Timer();

        loggingTimer.schedule( new TimerTask() {
            public void run() {
                // log position to db if there is a new gps update
                System.out.println("logging triggered after 10 seconds");

                if (featureUpdated) {
                    System.out.println("logging new position");
                    table.addFeatureAsync(latestPosition);

                    // flag we've read it
                    featureUpdated = false;
                }

            }
        }, 1000, 10000);
    }

    /**
     * Method to connect to the serial port with the UDB GPS device and listen for incoming NMEA sentences
     * <p>
     * When NMEA data is received, this is processed by the NMEALocationDataSource class to provide location information.
     */
    private void startGPSUpdate() {
        //start listening to serial port to get NMEA data
        NmeaLocationDataSource nmeaLocationDataSource = new NmeaLocationDataSource();
        var nmeaFuture = nmeaLocationDataSource.startAsync();
        nmeaFuture.addDoneListener(()-> {
            initGPS(nmeaLocationDataSource);

            System.out.println("adding location changed listener");
            // listener for location updates
            nmeaLocationDataSource.addLocationChangedListener(listener -> {
                System.out.print(".");

                // create default attributes for the feature
                Map<String, Object> attributes = new HashMap<>();
                attributes.put("TrackID", "GPS Test 2");
                attributes.put("Speed", listener.getLocation().getVelocity());
                attributes.put("Heading", listener.getLocation().getCourse());

                // update the latest position feature
                latestPosition = table.createFeature(attributes, listener.getLocation().getPosition());

                // set flag to say there is a position update
                featureUpdated = true;
            });
        });
    }

    /**
     * A method to sync gps data collected whilst offline into the hosted feature service.   This process will be called
     * when there is network connectivity to the feature service.
     */
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
                    syncJob.addJobDoneListener(()-> System.out.println("sync job done status " + syncJob.getStatus()));

                    syncJob.addProgressChangedListener(()-> System.out.println("progress!"));
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);
                }
            });

        });
    }

    /**
     * A method to open the offline geodatabase used to collect GPS tracks.
     *
     */
    private void openGeodatabase() {
        geodatabase = new Geodatabase("./gpsdata.geodatabase");

        geodatabase.loadAsync();
        geodatabase.addDoneLoadingListener(()-> {
            System.out.println("gdb load status :" + geodatabase.getLoadStatus());

            if (geodatabase.getLoadStatus() == LoadStatus.LOADED) {

                table = geodatabase.getGeodatabaseFeatureTableByServiceLayerId(0);
                table.loadAsync();
                table.addDoneLoadingListener(() -> {
                    System.out.println("opening table " + table.getDisplayName());
                    for (var field : table.getFields()) {
                        System.out.println("field : " + field.getName());
                    }
                    System.out.println("local edits " + table.hasLocalEdits());
                    System.out.println("feature count " + table.getTotalFeatureCount());

                    // enable UI components using the geodatabase
                    btnStartLogger.setDisable(false);
                    btnSync.setDisable(false);
                    btnStartGPSUpdate.setDisable(false);
                });
            } else {
                System.out.println("error loading geodatabase");
            }
        });
    }

    /**
     * A method to download an offline geodatabase for collecting GPS tracks
     */
    private void downloadOfflineGDB() {
        System.out.println("downloading gdb");

        // sync task connecting to the feature service
        syncTask = new GeodatabaseSyncTask(featureLayerURL);

        // load and listen for task to be ready
        syncTask.loadAsync();
        syncTask.addDoneLoadingListener(()-> {

            // creating parameters for requesting an empty geodatabase from the service.
            // The envelope below allows for data to be collected anywhere in the world.
            Envelope envelope = new Envelope(-180,-90,180,90, SpatialReferences.getWgs84());
            var paramsFuture = syncTask.createDefaultGenerateGeodatabaseParametersAsync(envelope);
            paramsFuture.addDoneListener(()-> {
                try {
                    // get the default parameters
                    GenerateGeodatabaseParameters parameters = paramsFuture.get();
                    parameters.setSyncModel(SyncModel.PER_LAYER);

                    // set the layer option, so it only gets the schema (not data)
                    parameters.getLayerOptions().get(0).setQueryOption(GenerateLayerOption.QueryOption.NONE);

                    // request the geodatabase with the task
                    generateGeodatabaseJob = syncTask.generateGeodatabase(parameters, "./gpsdata.geodatabase");
                    generateGeodatabaseJob.start();
                    generateGeodatabaseJob.addJobDoneListener(()-> {
                        if (generateGeodatabaseJob.getStatus() == Job.Status.FAILED) {
                            System.out.println("error :" + generateGeodatabaseJob.getError().getMessage());
                        } else {
                            System.out.println("Geodatabase downloaded");
                        }
                    });
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);
                }
            });
        });
    }

    /**
     * Method to initialise the serial port for reading the NMEA sentences.
     *
     * @param nmeaLocationDataSource the nmea data source which will be provided with the nmea sentences
     */
    private void initGPS(NmeaLocationDataSource nmeaLocationDataSource) {
        System.out.println("Starting serial...");

        Context pi4j = Pi4J.newAutoContext();

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
