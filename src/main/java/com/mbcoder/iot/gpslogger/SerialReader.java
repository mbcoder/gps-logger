/**
 * Copyright 2023 Esri
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
import com.pi4j.io.serial.Serial;
import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * A class for receiving byte data received on a serial port which contains NMEA sentences.  The NMEA sentences will be passed by a provided
 * NmeaLocationDataSource which will provide location information from the connected GPS device.
 */
public class SerialReader implements Runnable {

  //private final Console console;
  private final Serial serial;
  private NmeaLocationDataSource nmeaLocationDataSource;

  private boolean continueReading = true;

  /**
   * Constructor for the serial reader which takes a serial port and a location data source
   *
   * @param serial port for GPS
   * @param nmeaLocationDataSource location data source for processing NMEA sentences
   */
  public SerialReader(Serial serial, NmeaLocationDataSource nmeaLocationDataSource) {
    this.serial = serial;
    this.nmeaLocationDataSource = nmeaLocationDataSource;
  }

  /**
   * Method to stop reading from the serial port
   */
  public void stopReading() {
    continueReading = false;
  }

  @Override
  public void run() {
    // fpr reading data from the serial port
    BufferedReader br = new BufferedReader(new InputStreamReader(serial.getInputStream()));

    try {

      // Read data
      while (continueReading) {
        // check if there is serial data ready to read
        var available = serial.available();

        // if there is data then send it to the nmeaLocationDataSource for processing
        if (available > 0) {
          byte[] bytes = {(byte) br.read()};
          nmeaLocationDataSource.pushData(bytes);
        } else {
          Thread.sleep(10);
        }
      }
    } catch (Exception e) {
      System.out.println("Error reading data from serial: " + e.getMessage());
      System.out.println(e.getStackTrace());
    }
  }
}

