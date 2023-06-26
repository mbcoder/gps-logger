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
   * Constructor the serial reader which takes a serial port and a location data source
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

        // if there is data then send it do the nmeaLocationDataSource for processing
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

