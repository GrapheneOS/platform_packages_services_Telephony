This directory contains code and tools for generating and debugging binary
satellite s2 file.

Directory structure
=

`s2storage`
- `src/write` S2 write code used by tools to write the s2 cells into a
  binary file. This code is also used by `TeleServiceTests`.
- `src/readonly` S2 read-only code used by the above read-write code and the class
 `S2RangeSatelliteOnDeviceAccessController`.

`tools`
- `src/main` Contains the tools for generating binary satellite s2 file, and tools
  for dumping the binary file into human-readable format.
- `src/test` Contains the test code for the tools.

`configdatagenerator`
- `src/main` Contains the tool for generating satellite configdata protobuf file.
- `src/test` Contains the test code for the configdatagenerator tool.

Run unit tests
=
- Build the tools and test code: Go to the tool directory (`packages/services/Telephony/tools/
  satellite`) in the local workspace and run `mm`, e.g.,
- Run unit tests: `$atest SatelliteToolsTests`, `$atest SatelliteGenerateProtoTests`

Data file generate tools
=

`satellite_createsats2file`
- Runs the `satellite_createsats2file` to create a binary satellite S2 file from a
  list of S2 cells ID.
- Command: `$satellite_createsats2file --input-file <s2cells.txt> --s2-level <12>
  --is-allowed-list <true> --output-file <sats2.dat>`
  - `--input-file` Each line in the file contains a `unsigned-64bit` number which represents
    the ID of a S2 cell.
  - `--s2-level` The S2 level of all the cells in the input file.
  - `--is-allowed-list` Should be either `trrue` or `false`
    - `true` The input file contains a list of S2 cells where satellite services are allowed.
    - `false` The input file contains a list of S2 cells where satellite services are disallowed.
  - `--output-file` The created binary satellite S2 file, which will be used by
  the `SatelliteAccessController` module in determining if satellite communication
  is allowed at a location.
- Build the tools: Go to the tool directory (`packages/services/Telephony/tools/satellite`)
  in the local workspace and run `mm`.
- Example run command: `$satellite_createsats2file --input-file s2cells.txt --s2-level 12
  --is-allowed-list true --output-file sats2.dat`

`satellite_generateprotobuf`
- Runs the `satellite_generateprotobuf` to create a binary file of TelephonyConfigProto whose format
  is defined in telephony_config_update.proto
- Command: `satellite_generateprotobuf --input-file <input.xml> --output-file <telephony_config.pb>`
  - `--input-file` input XML file contains input information such as carrier id, carrier plmn,
  allowed service list and country code list. This is example of input file.
    ```xml
    <satelliteconfig>
      <!-- version -->
       <version>14</version>

      <!-- CarrierSupportedSatelliteServicesProto -->
      <carriersupportedservices>
        <carrier_id>1</carrier_id>
          <providercapability>
            <carrier_plmn>310160</carrier_plmn>
            <service>1</service>
          </providercapability>
          <providercapability>
            <carrier_plmn>310240</carrier_plmn>
            <service>6</service>
          </providercapability>
      </carriersupportedservices>

      <carriersupportedservices>
        <carrier_id>1891</carrier_id>
        <providercapability>
          <carrier_plmn>45005</carrier_plmn>
          <service>1</service>
          <service>2</service>
        </providercapability>
      </carriersupportedservices>

      <!-- SatelliteRegionProto -->
      <satelliteregion>
        <s2_cell_file>sats2.dat</s2_cell_file>
        <country_code>US</country_code>
        <country_code>KR</country_code>
        <is_allowed>TRUE</is_allowed>
      </satelliteregion>
    </satelliteconfig>
    ```
  - `--output-file` The created binary TelephonyConfigProto file, which will be used by
  the `ConfigUpdater` module for Satellite Project.
- Build the tools: Go to the tool directory (`packages/services/Telephony/tools/satellite`)
  in the local workspace and run `mm`.
- Example run command: `satellite_generateprotobuf --input-file input.xml --output-file
  telephony_config.pb`

Debug tools
=

`satellite_createsats2file_test`
- Create a test binary satellite S2 file with the following ranges:
  - [(prefix=0b100_11111111, suffix=1000), (prefix=0b100_11111111, suffix=2000))
  - [(prefix=0b100_11111111, suffix=2000), (prefix=0b100_11111111, suffix=3000))
  - [(prefix=0b101_11111111, suffix=1000), (prefix=0b101_11111111, suffix=2000))
- Run the test tool: `satellite_createsats2file_test /tmp/foo.dat`
  - This command will generate the binary satellite S2 cell file `/tmp/foo.dat` with
  the above S2 ranges.

`satellite_dumpsats2file`
- Dump the input binary satellite S2 cell file into human-readable text format.
- Run the tool: `$satellite_dumpsats2file /tmp/foo.dat /tmp/foo`
  - `/tmp/foo.dat` Input binary satellite S2 cell file.
  - `/tmp/foo` Output directory which contains the output text files.

`satellite_location_lookup`
- Check if a location is present in the input satellite S2 file.
- Run the tool: `$satellite_location_lookup --input-file <...> --lat-degrees <...>
  --lng-degrees <...>`
