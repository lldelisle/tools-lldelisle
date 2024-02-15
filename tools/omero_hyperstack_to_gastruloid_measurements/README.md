# OMERO hyperstack to gastruloid measurements

## Set up user credentials on Galaxy to connect to other omero instance

To enable users to set their credentials for this tool,
make sure the file `config/user_preferences_extra.yml` has the following section:

```
    omero_account:
        description: Your OMERO instance connection credentials
        inputs:
            - name: username
              label: Username
              type: text
              required: False
            - name: password
              label: Password
              type:  password
              required: False
```

## Dependencies

This tool requires the channels: `--channel conda-forge --channel bioconda --channel defaults --channel pytorch --channel ilastik-forge`.


## CHANGELOG

### 20240214

- Update fiji, max_inscribed_circles, omero_ij, simple_omero_client
- Allow to do 'Replace at runtime' where it deletes the ROIs of the image before running the analysis on the image and remove all tables linked to the object on which the script is run.
- Add a parameter for the thresholding method when using 'convert_to_mask'.
- Allow to run 'Options...' and 'Fill Holes' between the mask and the median step.
- As a consequence the tables have more columns than before.
- Use um as unit for minimum_diameter and closeness_tolerance.
- Use Double for minimum_diameter_um, closeness_tolerance_um and min_size_particle
- Change the default of min_size_particle from 5000 to 20000.
- Change the default of minimume_diameter from 20 to 40.
- Change the default of radius_median from 20 to 10.

### 20231220

- Add a new parameter: segmentation_method which can be 'ilastik' or 'convert_to_mask'. If 'convert_to_mask' is chosen, it does an autothreshold.
- The tool_version has been changed from Phase to White

### 20230728

- Add a new parameter: keep_only_largest which allows to keep only the largest ROI for each stack

### 20230727

- Add new parameters (ilastik_label_BG and probability_threshold_BG) to be able to generate a ROI for background.
- Add XCentroid and YCentroid to the result table

### 20230628

- Change RoiWrapper to ROIWrapper

### 20230623

- Be more robust to OMERO reboot:
    - 'rescue' allows to only process images which does not have ROIs and tables and generate final table
    - When making a query to omero repeat it after 0 minutes if it fails and again with 10, 60, 360, 600.

### 20230405

- New parameter 'use_existing' allows to recompute only the spine

### 20230324

First release
