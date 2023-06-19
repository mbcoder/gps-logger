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

import com.esri.arcgisruntime.location.NmeaLocationDataSource;
import com.esri.arcgisruntime.mapping.view.MapView;
import com.pi4j.Pi4J;
import com.pi4j.context.Context;
import com.pi4j.io.serial.Baud;
import com.pi4j.io.serial.FlowControl;
import com.pi4j.io.serial.Parity;
import com.pi4j.io.serial.Serial;
import com.pi4j.io.serial.StopBits;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class GPS_Logger extends Application {

    private Context pi4j;
    private Serial serial;
    private SerialReader serialReader;
    private String featureLayerURL = "https://services1.arcgis.com/6677msI40mnLuuLr/arcgis/rest/services/GPS_Tracks/FeatureServer";


    private MapView mapView;

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

        //start listening to serial port to get NMEA data
        NmeaLocationDataSource nmeaLocationDataSource = new NmeaLocationDataSource();
        var nmeaFuture = nmeaLocationDataSource.startAsync();
        nmeaFuture.addDoneListener(()-> {
            initGPS(locationDataSource);
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

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
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
            }
        };
        runnable.run();
    }

    /**
     * Stops and releases all resources used in application.
     */
    @Override
    public void stop() {
        serialReader.stopReading();
    }
}
