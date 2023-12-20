# OMERO hyperstack to gastruloid measurements

## CHANGELOG

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
