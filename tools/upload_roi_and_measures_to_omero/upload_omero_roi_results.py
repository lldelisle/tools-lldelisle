import argparse
import json
import os
import sys
import re
import pandas as pd
import numpy as np
import tempfile
import omero
from omero.gateway import BlitzGateway
from omero.rtypes import rstring, rdouble
from omero.cmd import Delete2

file_base_name_exportedTIFF = re.compile(r'^.*__(\d+)__0__0__\d+__\d+$')
file_base_name_original = re.compile(r'^.*__(\d+)$')

non_roi_value = -1

def get_image_id(image_file_name):
    # Check the file name corresponds to the expected:
    match = file_base_name_exportedTIFF.findall(image_file_name.replace('.tiff', ''))
    if len(match) == 0:
        match = file_base_name_original.findall(image_file_name.replace('.tiff', ''))
        if len(match) == 0:
            raise Exception(f"{r_file} does not match the expected format")
    # Get the image_id
    image_id = int(match[0])
    return(image_id)


def get_omero_credentials(config_file):
    if config_file is None:  # IDR connection
        omero_username = 'public'
        omero_password = 'public'
    else:  # other omero instance
        with open(config_file) as f:
            cfg = json.load(f)
            omero_username = cfg['username']
            omero_password = cfg['password']

            if omero_username == "" or omero_password == "":
                omero_username = 'public'
                omero_password = 'public'
    return(omero_username, omero_password)


def clean(image_id,
          omero_username, omero_password,
          omero_host, omero_secured,
          verbose):
    with BlitzGateway(omero_username, omero_password, host=omero_host, secure=omero_secured) as conn:
        roi_service = conn.getRoiService()
        img = conn.getObject('Image', image_id)
        rois = roi_service.findByImage(image_id, None, conn.SERVICE_OPTS).rois
        # Delete existing rois
        if len(rois) > 0:
            if verbose:
                print(f"Removing {len(rois)} existing ROIs.")
            conn.deleteObjects('Roi', [roi.getId().val for roi in rois], wait=True)
        # Delete existing table named Results_from_Fiji
        for ann in img.listAnnotations():
            if ann.OMERO_TYPE == omero.model.FileAnnotationI:
                if ann.getFileName() == 'Results_from_Fiji':
                    if verbose:
                        print(f"Removing the table Results_from_Fiji.")
                    conn.deleteObjects('OriginalFile', [ann.getFile().id], wait=True)


def upload(image_id, df, roi_files,
           omero_username, omero_password,
           omero_host, omero_secured,
           verbose):
    with BlitzGateway(omero_username, omero_password, host=omero_host, secure=omero_secured) as conn:
        updateService = conn.getUpdateService()
        img = conn.getObject('Image', image_id)
        # Create ROIs:
        roi_ids = []
        # roi_big_circle_ids = []
        # roi_spine_ids = []
        for i, ro_file in enumerate(roi_files):
            # Create a polygon
            my_poly = omero.model.PolygonI()
            # Add the coordinates
            with open(ro_file, 'r') as f:
                coos = f.readlines()
            coos_formatted = ', '.join([l.strip().replace('\t', ',') for l in coos])
            my_poly.setPoints(rstring(coos_formatted))
            # Add a name
            my_poly.setTextValue(rstring('ROI' + str(i)))
            # Create a omero ROI
            my_new_roi = omero.model.RoiI()
            my_new_roi.addShape(my_poly)
            # Attach it to the image
            my_new_roi.setImage(img._obj)
            my_new_roi = updateService.saveAndReturnObject(my_new_roi)
            roi_ids.append(my_new_roi.getId().val)
            if verbose:
                print(f"Created ROI{i} {my_new_roi.getId().val}.")
            # Check if there is an elongation ROI associated:
            if os.path.exists(ro_file.replace("roi_coordinates", "elongation_rois")):
                # Get the coordinates
                with open(ro_file.replace("roi_coordinates", "elongation_rois"), 'r') as f:
                    all_coos = f.readlines()
                # Get the circles coos
                circles_coos = [l for l in all_coos if len(l.split("\t")) == 3]
                for j, circle_coo in enumerate(circles_coos):
                    # Create an ellipse
                    my_ellipse = omero.model.EllipseI()
                    # Get the characteristics from text file
                    xleft, ytop, width = [float(v) for v in circle_coo.strip().split("\t")]
                    # Add it to the ellipse
                    my_ellipse.setRadiusX(rdouble(width / 2.))
                    my_ellipse.setRadiusY(rdouble(width / 2.))
                    my_ellipse.setX(rdouble(xleft + width / 2))
                    my_ellipse.setY(rdouble(ytop + width / 2))
                    # Add a name
                    my_ellipse.setTextValue(rstring('inscribedCircle' + str(i) + "_" + str(j)))
                    # Create a omero ROI
                    my_new_roi = omero.model.RoiI()
                    my_new_roi.addShape(my_ellipse)
                    # Attach it to the image
                    my_new_roi.setImage(img._obj)
                    my_new_roi = updateService.saveAndReturnObject(my_new_roi)
                    if verbose:
                        print(f"Created ROI inscribedCircle {i}_{j}: {my_new_roi.getId().val}.")
                    # I store the id of the first circle:
                    # if j == 0:
                    #     roi_big_circle_ids.append(my_new_roi.getId().val)
                if len(all_coos) > len(circles_coos):
                    # Create a polyline for the spine
                    my_poly = omero.model.PolylineI()
                    coos_formatted = ', '.join([l.strip().replace('\t', ',') for l in all_coos[len(circles_coos):]])
                    my_poly.setPoints(rstring(coos_formatted))
                    # Add a name
                    my_poly.setTextValue(rstring('spine' + str(i)))
                    # Create a omero ROI
                    my_new_roi = omero.model.RoiI()
                    my_new_roi.addShape(my_poly)
                    # Attach it to the image
                    my_new_roi.setImage(img._obj)
                    my_new_roi = updateService.saveAndReturnObject(my_new_roi)
                    if verbose:
                        print(f"Created ROI spine{i}: {my_new_roi.getId().val}.")
                    # roi_spine_ids.append(my_new_roi.getId().val)
                else:
                    if verbose:
                        print("No spine found")
                    # roi_spine_ids.append(non_roi_value)
            # else:
            #     roi_big_circle_ids.append(non_roi_value)
            #     roi_spine_ids.append(non_roi_value)

        # Create the table:
        table_name = "Results_from_Fiji"
        columns = []
        for col_name in df.columns[1:]:
            if col_name in ['Label', 'Date', 'Version']:
                columns.append(omero.grid.StringColumn(col_name, '', 256, []))
            else:
                columns.append(omero.grid.DoubleColumn(col_name, '', []))

        # From Claire's groovy: 
            # table_columns[size] = new TableDataColumn("Roi", size, ROIData)
        columns.append(omero.grid.RoiColumn('Roi', '', []))
        # For the moment (20220729), the table support only one ROI column with link...
        # if 'Elongation_index' in df.columns[1:]:
        #     columns.append(omero.grid.RoiColumn('Roi_maxCircle', '', []))
        #     columns.append(omero.grid.RoiColumn('Roi_Spine', '', []))
        # columns.append(omero.grid.RoiColumn('Roi_main', '', []))

        resources = conn.c.sf.sharedResources()
        repository_id = resources.repositories().descriptions[0].getId().getValue()
        table = resources.newTable(repository_id, table_name)
        table.initialize(columns)

        data = []
        for col_name in df.columns[1:]:
            if col_name in ['Label', 'Date', 'Version']:
                data.append(omero.grid.StringColumn(col_name, '', 256, df[col_name].astype('string').to_list()))
            else:
                data.append(omero.grid.DoubleColumn(col_name, '', df[col_name].to_list()))
        data.append(omero.grid.RoiColumn('Roi', '', roi_ids))
        # if verbose:
        #     print("Columns are " + " ".join(df.columns[1:]))
        # if 'Elongation_index' in df.columns[1:]:
        #     if verbose:
        #         print("Adding 2 rois columns")
        #         print(roi_ids)
        #         print(roi_big_circle_ids)
        #     data.append(omero.grid.RoiColumn('Roi_maxCircle', '', roi_big_circle_ids))
        #     data.append(omero.grid.RoiColumn('Roi_Spine', '', roi_spine_ids))
        # data.append(omero.grid.RoiColumn('Roi_main', '', roi_ids))

        table.addData(data)
        orig_file = table.getOriginalFile()
        table.close()
        # when we are done, close.

        # Load the table as an original file

        orig_file_id = orig_file.id.val
        # ...so you can attach this data to an object e.g. Image
        file_ann = omero.model.FileAnnotationI()
        # use unloaded OriginalFileI
        file_ann.setFile(omero.model.OriginalFileI(orig_file_id, False))
        file_ann = updateService.saveAndReturnObject(file_ann)
        link = omero.model.ImageAnnotationLinkI()
        link.setParent(omero.model.ImageI(image_id, False))
        link.setChild(omero.model.FileAnnotationI(file_ann.getId().getValue(), False))
        updateService.saveAndReturnObject(link)
        if verbose:
            print("Successfully created a Table with results.")


def scan_and_upload(roi_directory, summary_results,
                    omero_username, omero_password,
                    omero_host='idr.openmicroscopy.org', omero_secured=False,
                    verbose=False):
    # First get the summary results
    full_df = pd.read_csv(summary_results)
    # Loop over the image names
    for image_file_name in np.unique(full_df['Label']):
        # Get the image_id
        image_id = get_image_id(image_file_name)
        if verbose:
            print(f"Image:{image_id} is in the table. Cleaning old results.")
        clean(image_id,
              omero_username, omero_password,
              omero_host, omero_secured,
              verbose)
        # Subset the result to the current image
        df = full_df[full_df['Label'] == image_file_name]
        if np.isnan(df['Area'].to_list()[0]):
            # No ROI has been detected
            if verbose:
                print(f"No ROI was found.")
            continue
        n_rois = df.shape[0]
        if verbose:
            print(f"I found {n_rois} measurements.")
        # Check the corresponding rois exists
        roi_files = [os.path.join(roi_directory,
                                  image_file_name.replace('.tiff', '_tiff') + '__' + str(i) + '_roi_coordinates.txt')
                     for i in range(n_rois)]
        for ro_file in roi_files:
            if not os.path.exists(ro_file):
                raise Exception(f"Could not find {ro_file}")
        upload(image_id, df, roi_files,
               omero_username, omero_password,
               omero_host, omero_secured,
               verbose)
    # Update the full_df with image id:
    full_df['id'] = [get_image_id(image_file_name)
                     for image_file_name in full_df['Label']]
    # Attach it to the dataset:
    with BlitzGateway(omero_username, omero_password, host=omero_host, secure=omero_secured) as conn:
        full_df['dataset_id'] = [a.id for id in full_df['id'] for a in conn.getObject("Image", id).getAncestry() if a.OMERO_CLASS == 'Dataset']
        dir = tempfile.mkdtemp()
        for dataset_id in np.unique(full_df['dataset_id']):
            df = full_df[full_df['dataset_id'] == dataset_id]
            first_date = df['Date'].to_list()[0]
            file_to_upload = os.path.join(dir, 'Results_from_Fiji_' + first_date + '.csv')
            df.to_csv(file_to_upload, index=False)
            dataset = conn.getObject("Dataset", dataset_id)
            # create the original file and file annotation (uploads the file etc.)
            # namespace = "my.custom.demo.namespace"
            file_ann = conn.createFileAnnfromLocalFile(
                file_to_upload) #, mimetype="text/plain", ns=namespace, desc=None)
            print("Attaching FileAnnotation to Dataset: ", "File ID:", file_ann.getId(), \
                ",", file_ann.getFile().getName(), "Size:", file_ann.getFile().getSize())
            dataset.linkAnnotation(file_ann)     # link it to dataset.


if __name__ == "__main__":
    p = argparse.ArgumentParser()
    p.add_argument(
        '-oh', '--omero-host', type=str, default="idr.openmicroscopy.org"
    )
    p.add_argument(
        '--omero-secured', action='store_true', default=True
    )
    p.add_argument(
        '-cf', '--config-file', dest='config_file', default=None
    )
    p.add_argument(
        '--rois', type=str, default=None
    )
    p.add_argument(
        '--summaryResults', type=str, default=None
    )
    p.add_argument(
        '--verbose', action='store_true'
    )
    args = p.parse_args()
    scan_and_upload(args.rois, args.summaryResults,
                    *get_omero_credentials(args.config_file),
                    omero_host=args.omero_host, omero_secured=args.omero_secured,
                    verbose=args.verbose)
