/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.telephony.tools.configdatagenerate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import com.android.internal.telephony.satellite.SatelliteConfigData.CarrierSupportedSatelliteServicesProto;
import com.android.internal.telephony.satellite.SatelliteConfigData.SatelliteConfigProto;
import com.android.internal.telephony.satellite.SatelliteConfigData.SatelliteProviderCapabilityProto;
import com.android.internal.telephony.satellite.SatelliteConfigData.SatelliteRegionProto;
import com.android.internal.telephony.satellite.SatelliteConfigData.TelephonyConfigProto;

import com.google.protobuf.ByteString;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

public class ConfigDataGeneratorTest {
    private Path mTempDirPath;

    @Before
    public void setUp() throws IOException {
        mTempDirPath = createTempDir(this.getClass());
    }

    @After
    public void tearDown() throws IOException {
        if (mTempDirPath != null) {
            deleteDirectory(mTempDirPath);
        }
    }

    @Test
    public void testConfigDataGeneratorWithInvalidPlmn() throws Exception {
        Path inputDirPath = mTempDirPath.resolve("input");
        Files.createDirectory(inputDirPath);
        Path inputFilePath = inputDirPath.resolve("test_input.xml");
        Path inputS2CellFilePath = inputDirPath.resolve("sats2.dat");

        Path outputDirPath = mTempDirPath.resolve("output");
        Files.createDirectory(outputDirPath);
        Path outputFilePath = outputDirPath.resolve("test_out.pb");
        String inputfileName = inputFilePath.toAbsolutePath().toString();
        String inputS2CellFileName = inputS2CellFilePath.toAbsolutePath().toString();
        File inputFile = new File(inputfileName);
        ByteString inputByteStringForS2Cell = ByteString.copyFromUtf8("Test ByteString!");
        writeByteStringToFile(inputS2CellFileName, inputByteStringForS2Cell);

        createInputXml(inputFile, 14, 1, "310062222", 1,
                "US", true, inputS2CellFileName);
        String[] args = {
                "--input-file", inputFilePath.toAbsolutePath().toString(),
                "--output-file", outputFilePath.toAbsolutePath().toString()
        };
        try {
            ConfigDataGenerator.main(args);
        } catch (Exception ex) {
            // Expected exception because input plmn is invalid
            return;
        }
        fail("Exception should have been caught");
    }

    @Test
    public void testConfigDataGeneratorWithInvalidService() throws Exception {
        Path inputDirPath = mTempDirPath.resolve("input");
        Files.createDirectory(inputDirPath);
        Path inputFilePath = inputDirPath.resolve("test_input.xml");
        Path inputS2CellFilePath = inputDirPath.resolve("sats2.dat");

        Path outputDirPath = mTempDirPath.resolve("output");
        Files.createDirectory(outputDirPath);
        Path outputFilePath = outputDirPath.resolve("test_out.pb");
        String inputfileName = inputFilePath.toAbsolutePath().toString();
        String inputS2CellFileName = inputS2CellFilePath.toAbsolutePath().toString();
        File inputFile = new File(inputfileName);
        ByteString inputByteStringForS2Cell = ByteString.copyFromUtf8("Test ByteString!");
        writeByteStringToFile(inputS2CellFileName, inputByteStringForS2Cell);

        createInputXml(inputFile, 14, 1, "31006", -1,
                "US", true, inputS2CellFileName);
        String[] args = {
                "--input-file", inputFilePath.toAbsolutePath().toString(),
                "--output-file", outputFilePath.toAbsolutePath().toString()
        };
        try {
            ConfigDataGenerator.main(args);
        } catch (Exception ex) {
            // Expected exception because input allowed service is invalid
            return;
        }
        fail("Exception should have been caught");
    }

    @Test
    public void testConfigDataGeneratorWithInvalidCountryCode() throws Exception {
        Path inputDirPath = mTempDirPath.resolve("input");
        Files.createDirectory(inputDirPath);
        Path inputFilePath = inputDirPath.resolve("test_input.xml");
        Path inputS2CellFilePath = inputDirPath.resolve("sats2.dat");

        Path outputDirPath = mTempDirPath.resolve("output");
        Files.createDirectory(outputDirPath);
        Path outputFilePath = outputDirPath.resolve("test_out.pb");
        String inputfileName = inputFilePath.toAbsolutePath().toString();
        String inputS2CellFileName = inputS2CellFilePath.toAbsolutePath().toString();
        File inputFile = new File(inputfileName);
        ByteString inputByteStringForS2Cell = ByteString.copyFromUtf8("Test ByteString!");
        writeByteStringToFile(inputS2CellFileName, inputByteStringForS2Cell);

        createInputXml(inputFile, 14, 1, "31006", 1,
                "USSSS", true, inputS2CellFileName);
        String[] args = {
                "--input-file", inputFilePath.toAbsolutePath().toString(),
                "--output-file", outputFilePath.toAbsolutePath().toString()
        };
        try {
            ConfigDataGenerator.main(args);
        } catch (Exception ex) {
            // Expected exception because input country code is invalid
            return;
        }
        fail("Exception should have been caught");
    }

    @Test
    public void testConfigDataGeneratorWithValidInput() throws Exception {
        Path inputDirPath = mTempDirPath.resolve("input");
        Files.createDirectory(inputDirPath);
        Path inputFilePath = inputDirPath.resolve("test_input.xml");
        Path inputS2CellFilePath = inputDirPath.resolve("sats2.dat");

        Path outputDirPath = mTempDirPath.resolve("output");
        Files.createDirectory(outputDirPath);
        Path outputFilePath = outputDirPath.resolve("test_out.pb");
        String inputfileName = inputFilePath.toAbsolutePath().toString();
        String inputS2CellFileName = inputS2CellFilePath.toAbsolutePath().toString();
        File inputFile = new File(inputfileName);
        String outputFileName = outputFilePath.toAbsolutePath().toString();


        int inputVersion = 14;
        int inputCarrierId = 1;
        String inputPlmn = "31006";
        int inputAllowedService = 1;
        String inputCountryCode = "US";
        boolean inputIsAllowed = true;
        ByteString inputByteStringForS2Cell = ByteString.copyFromUtf8("Test ByteString!");
        writeByteStringToFile(inputS2CellFileName, inputByteStringForS2Cell);
        createInputXml(inputFile, inputVersion, inputCarrierId, inputPlmn, inputAllowedService,
                inputCountryCode, inputIsAllowed, inputS2CellFileName);
        String[] args = {
                "--input-file", inputFilePath.toAbsolutePath().toString(),
                "--output-file", outputFilePath.toAbsolutePath().toString()
        };
        try {
            ConfigDataGenerator.main(args);
        } catch (Exception ex) {
            fail("Unexpected exception when executing the tool ex=" + ex);
        }

        Path filePath = Paths.get(outputFileName);
        byte[] fileBytes = Files.readAllBytes(filePath);
        TelephonyConfigProto telephonyConfigProto = TelephonyConfigProto.parseFrom(fileBytes);
        SatelliteConfigProto satelliteConfigProto = telephonyConfigProto.getSatellite();
        int version  = satelliteConfigProto.getVersion();
        assertEquals(inputVersion, version);
        CarrierSupportedSatelliteServicesProto serviceProto =
                satelliteConfigProto.getCarrierSupportedSatelliteServices(0);
        int carrierId = serviceProto.getCarrierId();
        assertEquals(inputCarrierId, carrierId);
        SatelliteProviderCapabilityProto providerCapabilityProto =
                serviceProto.getSupportedSatelliteProviderCapabilities(0);
        String plmn = providerCapabilityProto.getCarrierPlmn();
        assertEquals(inputPlmn, plmn);
        int allowedService = providerCapabilityProto.getAllowedServices(0);
        assertEquals(inputAllowedService, allowedService);

        SatelliteRegionProto regionProto = satelliteConfigProto.getDeviceSatelliteRegion();
        String countryCode = regionProto.getCountryCodes(0);
        assertEquals(inputCountryCode, countryCode);
        ByteString s2cellfile = regionProto.getS2CellFile();
        byte[] fileBytesForInputS2CellFile = Files.readAllBytes(Paths.get(inputS2CellFileName));
        ByteString inputS2CellFile = ByteString.copyFrom(fileBytesForInputS2CellFile);
        assertEquals(inputS2CellFile, s2cellfile);
        boolean isAllowed = regionProto.getIsAllowed();
        assertEquals(inputIsAllowed, isAllowed);
    }

    private void createInputXml(File outputFile, int version, int carrierId, String plmn,
            int allowedService, String countryCode, boolean isAllowed, String inputS2CellFileName) {
        try {
            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

            // Create Document and Root Element
            Document doc = docBuilder.newDocument();
            Element rootElement = doc.createElement(ConfigDataGenerator.TAG_SATELLITE_CONFIG);
            doc.appendChild(rootElement);

            // Add <version>
            Element versionElement = doc.createElement(ConfigDataGenerator.TAG_VERSION);
            versionElement.appendChild(doc.createTextNode(String.valueOf(version)));
            rootElement.appendChild(versionElement);

            // Add <carriersupportedservices>
            rootElement.appendChild(
                    createCarrierSupportedServices(doc, carrierId, plmn, allowedService));

            // Add <satelliteregion>
            Element satelliteRegion = doc.createElement(ConfigDataGenerator.TAG_SATELLITE_REGION);
            satelliteRegion.appendChild(
                    createElementWithText(doc, ConfigDataGenerator.TAG_S2_CELL_FILE,
                            inputS2CellFileName));
            satelliteRegion.appendChild(
                    createElementWithText(doc, ConfigDataGenerator.TAG_COUNTRY_CODE, countryCode));
            satelliteRegion.appendChild(
                    createElementWithText(doc, ConfigDataGenerator.TAG_IS_ALLOWED,
                            isAllowed ? "TRUE" : "FALSE"));
            rootElement.appendChild(satelliteRegion);

            // Write XML to File
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            DOMSource source = new DOMSource(doc);
            StreamResult result = new StreamResult(outputFile);
            transformer.transform(source, result);

        } catch (Exception e) {
            throw new RuntimeException("Got exception in creating input file , e=" + e);
        }
    }

    private static Element createCarrierSupportedServices(Document doc, int carrierId,
            String carrierPlmn, int... services) {
        Element carrierSupportedServices = doc.createElement(
                ConfigDataGenerator.TAG_SUPPORTED_SERVICES);
        carrierSupportedServices.appendChild(createElementWithText(doc,
                ConfigDataGenerator.TAG_CARRIER_ID, String.valueOf(carrierId)));

        Element providerCapability = doc.createElement(ConfigDataGenerator.TAG_PROVIDER_CAPABILITY);
        providerCapability.appendChild(createElementWithText(doc,
                ConfigDataGenerator.TAG_CARRIER_PLMN, carrierPlmn));
        for (int service : services) {
            providerCapability.appendChild(createElementWithText(doc,
                    ConfigDataGenerator.TAG_SERVICE, String.valueOf(service)));
        }
        carrierSupportedServices.appendChild(providerCapability);

        return carrierSupportedServices;
    }

    private static Element createElementWithText(Document doc, String tagName, String textContent) {
        Element element = doc.createElement(tagName);
        element.appendChild(doc.createTextNode(textContent));
        return element;
    }

    private static Path createTempDir(Class<?> testClass) throws IOException {
        return Files.createTempDirectory(testClass.getSimpleName());
    }

    private static void deleteDirectory(Path dir) throws IOException {
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path path, BasicFileAttributes basicFileAttributes)
                    throws IOException {
                Files.deleteIfExists(path);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path path, IOException e) throws IOException {
                Files.delete(path);
                return FileVisitResult.CONTINUE;
            }
        });
        assertFalse(Files.exists(dir));
    }

    private void writeByteStringToFile(String fileName, ByteString byteString) {
        try (FileOutputStream fos = new FileOutputStream(fileName)) {
            fos.write(byteString.toByteArray());
        } catch (IOException e) {
            System.err.println("Error writing to file: " + e.getMessage());
        }
    }
}
