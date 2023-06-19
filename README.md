# GPS Logger project

This project is a demonstration of an IoT GPS data logger which integrates into the ArcGIS platform.

It has been designed to work on a Raspberry Pi Model 4B with a GPS receiver.

The project uses an early adopter version of the ArcGIS Maps SDK for Java specifically compiled to work with Arm Linux platforms such as the Raspberry Pi or Jenson Orin units.  The Maps SDK for Arm Linux can be requested by emailing ARM64LinuxNative@esri.com.  This will install the libraries needed for this app in a Maven Local instance.  This is a temporary way of getting the SDK whilst it is in Beta.  Subsequent releases will be available in a public maven repository.

The project includes the Gradle wrapper, so there is no need to install Gradle to run the app.

The app launches a window displaying a map.

![screenshot](screenshot.png)

## Instructions

### IntelliJ IDEA

1. Open IntelliJ IDEA and select _File > Open..._.
2. Choose the java-gradle-starter-project directory and click _OK_.
3. Select _File > Project Structure..._ and ensure that the Project SDK and language level are set to use Java 11.
4. Open the Gradle view with _View > Tool Windows > Gradle_.
5. In the Gradle view, double-click `copyNatives` under _Tasks > build_. This will unpack the native library dependencies to $USER_HOME/.arcgis.
6. In the Gradle view, double-click `run` under _Tasks > application_ to run the app.


### Command Line

1. `cd` into the project's root directory.
2. Run `./gradlew clean build` on Linux/Mac or `gradlew.bat clean build` on Windows.
3. Run `./gradlew copyNatives` on Linux/Mac or `gradlew.bat copyNatives` on Windows. This will unpack the native library dependencies to $USER_HOME.arcgis.
4. Run `./gradlew run` on Linux/Mac or `gradlew.bat run` on Windows to run the app.

## Requirements

See the Java Maps SDK [system requirements](https://developers.arcgis.com/java/reference/system-requirements/).

## Resources

* [ArcGIS Maps SDK for Java](https://developers.arcgis.com/java/)  
* [ArcGIS Blog](https://www.esri.com/arcgis-blog/developers/)  
* [Esri Twitter](https://twitter.com/arcgisdevs)  

## Issues

Find a bug or want to request a new feature?  Please let us know by submitting an issue.

## Contributing

Esri welcomes contributions from anyone and everyone. Please see our [guidelines for contributing](https://github.com/esri/contributing).

## Licensing

Copyright 2023 Esri

Licensed under the Apache License, Version 2.0 (the "License"); you may not 
use this file except in compliance with the License. You may obtain a copy 
of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software 
distributed under the License is distributed on an "AS IS" BASIS, WITHOUT 
WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the 
License for the specific language governing permissions and limitations 
under the License.

A copy of the license is available in the repository's license.txt file.
