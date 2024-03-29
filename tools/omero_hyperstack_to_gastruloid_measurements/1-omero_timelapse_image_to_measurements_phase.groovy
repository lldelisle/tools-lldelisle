// This macro was written by the BIOP (https://github.com/BIOP)
// Romain Guiet and Rémy Dornier
// Lucille Delisle modified to support headless
// And to be more robust to OMERO reboot
// merge the analysis script with templates available at
// https://github.com/BIOP/OMERO-scripts/tree/025047955b5c1265e1a93b259c1de4600d00f107/Fiji

// Last modification: 2024-02-14

/*
 * = COPYRIGHT =
 * © All rights reserved. ECOLE POLYTECHNIQUE FEDERALE DE LAUSANNE, Switzerland, BioImaging And Optics Platform (BIOP), 2024
 *
 * Licensed under the BSD-3-Clause License:
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided
 * that the following conditions are met:
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer
 *    in the documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products
 *     derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING,
 * BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

// This macro will use ilastik or convert to mask
// to detect ROIs
// measure and compute elongation index
// It may also regenerate a ROI of background

// The input image(s) may have multiple time stacks
// It may have multiple channels
// If multiple channels are present, the LUT will be
// used to determine which channel should be used.

// The option keep_only_largest (by default to true)
// Allows to keep only the largest ROI for each stack

// The option 'rescue' allows to only process images without ROIs and
// tables and generate the final table

// The option 'replace_at_runtime' allows to remove tables at start
// and ROIs on each image just before processing.

// The option 'use_existing' allows to
// Recompute only spine

// Without 'use_existing' or 'replace_at_runtime', the job will fail if a final table exists

// Without 'use_existing' or 'replace_at_runtime' or 'rescue', the job will fail if ROI exists

// This macro works both in headless
// or GUI

// In both modes,
// The result table and the result ROIs are sent to omero
// The measures are: Area,Perim.,Circ.,Feret,FeretX,FeretY,FeretAngle,MinFeret,AR,Round,Solidity,Unit,Date,Version,IlastikProject,ProbabilityThreshold,ThresholdingMethod,OptionsDo,OptionsIterations,OptionsCount,FillHolesBefore,RadiusMedian,MinSizeParticle,MinDiameter,ClosenessTolerance,MinSimilarity,BaseImage,ROI,Time,ROI_type,XCentroid,YCentroid,LargestRadius,SpineLength,ElongationIndex[,Date_rerun_spine,Version_rerun_spine]

// LargestRadius and SpineLength are set to 0 if no circle was found.
// ElongationIndex is set to 0 if a gastruloid was found and to -1 if no gastruloid was found.

// SpineLength is set to 0 and ElongationIndex is set to 1 if a single circle was found.

import ch.epfl.biop.MaxInscribedCircles

import fr.igred.omero.annotations.TableWrapper
import fr.igred.omero.Client
import fr.igred.omero.repository.DatasetWrapper
import fr.igred.omero.repository.GenericRepositoryObjectWrapper
import fr.igred.omero.repository.ImageWrapper
import fr.igred.omero.repository.PlateWrapper
import fr.igred.omero.repository.PixelsWrapper
import fr.igred.omero.repository.WellWrapper
import fr.igred.omero.roi.ROIWrapper

import ij.ImagePlus
import ij.gui.Overlay
import ij.gui.PolygonRoi
import ij.gui.Roi
import ij.gui.ShapeRoi
import ij.IJ
import ij.io.FileSaver
import ij.measure.ResultsTable
import ij.plugin.Concatenator
import ij.plugin.Duplicator
import ij.plugin.frame.RoiManager
import ij.plugin.HyperStackConverter
import ij.plugin.ImageCalculator
import ij.plugin.Thresholder
import ij.Prefs
import ij.process.FloatPolygon
import ij.process.ImageProcessor

import java.awt.Color
import java.awt.GraphicsEnvironment
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors

import loci.plugins.in.ImporterOptions

import net.imglib2.img.display.imagej.ImageJFunctions

import omero.model.LengthI

import org.apache.commons.io.FilenameUtils
import org.apache.commons.io.FileUtils
import org.ilastik.ilastik4ij.ui.*


// Global variable with times in minutes to wait:
waiting_times = [0, 10, 60, 360, 600]

def robustlyGetAll(GenericRepositoryObjectWrapper obj_wrp, String object_type, Client user_client) {
    for (waiting_time in waiting_times) {
        try {
            wrappers = null
            switch (object_type) {
                case "image":
                    wrappers = obj_wrp.getImages(user_client)
                    break
                case "dataset":
                    wrappers = obj_wrp.getDatasets(user_client)
                    break
                case "well":
                    wrappers = obj_wrp.getWells(user_client)
                    break
                case "project":
                    wrappers = obj_wrp.getProjects(user_client)
                    break
                case "plate":
                    wrappers = obj_wrp.getPlates(user_client)
                    break
                case "screen":
                    wrappers = obj_wrp.getScreens(user_client)
                    break
            }
            return wrappers
        } catch(Exception e) {
            println("Could not get " + object_type + " for " + obj_wrp + " waiting " + waiting_time + " minutes and trying again.")
            println e
            TimeUnit.MINUTES.sleep(waiting_time)
            last_exception = e
            if (!user_client.isConnected()) {
                println("Has been deconnected. Will reconnect.")
                user_client.connect(host, port, USERNAME, PASSWORD.toCharArray())
            }
        }
    }
    throw last_exception
}

def robustlyGetOne(Long id, String object_type, Client user_client) {
    for (waiting_time in waiting_times) {
        try {

            wrapper = null
            switch (object_type) {
                case "image":
                    warpper = user_client.getImage(id)
                    break
                case "dataset":
                    warpper = user_client.getDataset(id)
                    break
                case "well":
                    warpper = user_client.getWell(id)
                    break
                case "project":
                    warpper = user_client.getProject(id)
                    break
                case "plate":
                    warpper = user_client.getPlate(id)
                    break
                case "screen":
                    warpper = user_client.getScreen(id)
                    break
            }
            return warpper
        } catch(Exception e) {
            println("Could not get " + object_type + " id " + id + " waiting " + waiting_time + " minutes and trying again.")
            println e
            TimeUnit.MINUTES.sleep(waiting_time)
            last_exception = e
            if (!user_client.isConnected()) {
                println("Has been deconnected. Will reconnect.")
                user_client.connect(host, port, USERNAME, PASSWORD.toCharArray())
            }
        }
    }
    throw last_exception
}

def robustlyGetTables(GenericRepositoryObjectWrapper obj_wrp,Client user_client) {
    for (waiting_time in waiting_times) {
        try {
            return obj_wrp.getTables(user_client)
        } catch(Exception e) {
            println("Could not get tables for " + obj_wrp + " waiting " + waiting_time + " minutes and trying again.")
            println e
            TimeUnit.MINUTES.sleep(waiting_time)
            last_exception = e
            if (!user_client.isConnected()) {
                println("Has been deconnected. Will reconnect.")
                user_client.connect(host, port, USERNAME, PASSWORD.toCharArray())
            }
        }
    }
    throw last_exception
}

def robustlyGetROIs(ImageWrapper image_wrp,Client user_client) {
    for (waiting_time in waiting_times) {
        try {
            return image_wrp.getROIs(user_client)
        } catch(Exception e) {
            println("Could not get ROIs for " + image_wrp + " waiting " + waiting_time + " minutes and trying again.")
            println e
            TimeUnit.MINUTES.sleep(waiting_time)
            last_exception = e
            if (!user_client.isConnected()) {
                println("Has been deconnected. Will reconnect.")
                user_client.connect(host, port, USERNAME, PASSWORD.toCharArray())
            }
        }
    }
    throw last_exception
}

def robustlyHasAnyTable(GenericRepositoryObjectWrapper obj_wrp, String object_type, Client user_client) {
    if (robustlyGetTables(obj_wrp, user_client).size() > 0) {
        return true
    } else {
        for (image_wrp in robustlyGetAll(obj_wrp, "image", user_client)) {
            if (robustlyGetTables(image_wrp, user_client).size() > 0) {
                return true
            }
        }
    }
    return false
}

def robustlyHasAnyROI(GenericRepositoryObjectWrapper obj_wrp, Client user_client) {
    for (image_wrp in robustlyGetAll(obj_wrp, "image", user_client)) {
        if (robustlyGetROIs(image_wrp, user_client).size() > 0) {
            return true
        }
    }
    return false
}


def robustlyDeleteTables(GenericRepositoryObjectWrapper obj_wrp,Client user_client) {
    for (waiting_time in waiting_times) {
        try {
            obj_wrp.getTables(user_client).each{
                user_client.delete(it)
            }
            return
        } catch(Exception e) {
            println("Could not remove tables for " + obj_wrp + " waiting " + waiting_time + " minutes and trying again.")
            println e
            TimeUnit.MINUTES.sleep(waiting_time)
            last_exception = e
            if (!user_client.isConnected()) {
                println("Has been deconnected. Will reconnect.")
                user_client.connect(host, port, USERNAME, PASSWORD.toCharArray())
            }
        }
    }
    throw last_exception
}

def robustlyDeleteROIs(ImageWrapper image_wrp, Client user_client, List<ROIWrapper> rois) {
    for (waiting_time in waiting_times) {
        try {
            // Remove existing ROIs
            // image_wrp.getROIs(user_client).each{ user_client.delete(it) }
            // Caused failure due to too high number of 'servantsPerSession'
            // Which reached 10k
            // I use see https://github.com/GReD-Clermont/simple-omero-client/issues/59
            user_client.delete((Collection<ROIWrapper>) rois)
            return
        } catch(Exception e) {
            println("Could not remove ROIs for " + image_wrp + " waiting " + waiting_time + " minutes and trying again.")
            println e
            TimeUnit.MINUTES.sleep(waiting_time)
            last_exception = e
            if (!user_client.isConnected()) {
                println("Has been deconnected. Will reconnect.")
                user_client.connect(host, port, USERNAME, PASSWORD.toCharArray())
            }
        }
    }
    throw last_exception
}

def robustlyAddAndReplaceTable(GenericRepositoryObjectWrapper obj_wrp, Client user_client, TableWrapper table) {
    for (waiting_time in waiting_times) {
        try {
            obj_wrp.addAndReplaceTable(user_client, table)
            return
        } catch(Exception e) {
            println("Could not add table to " + obj_wrp + " waiting " + waiting_time + " minutes and trying again.")
            println e
            TimeUnit.MINUTES.sleep(waiting_time)
            last_exception = e
            if (!user_client.isConnected()) {
                println("Has been deconnected. Will reconnect.")
                user_client.connect(host, port, USERNAME, PASSWORD.toCharArray())
            }
        }
    }
    throw last_exception
}

def robustlytoImagePlus(ImageWrapper image_wrp, Client user_client) {
    for (waiting_time in waiting_times) {
        try {
            return image_wrp.toImagePlus(user_client)
        } catch(Exception e) {
            println("Could not convert to image plus " + image_wrp + " waiting " + waiting_time + " minutes and trying again.")
            println e
            TimeUnit.MINUTES.sleep(waiting_time)
            last_exception = e
            if (!user_client.isConnected()) {
                println("Has been deconnected. Will reconnect.")
                user_client.connect(host, port, USERNAME, PASSWORD.toCharArray())
            }
        }
    }
    throw last_exception
}

def robustlysaveROIs(ImageWrapper image_wrp, Client user_client, List<ROIWrapper> rois) {
    for (waiting_time in waiting_times) {
        try {
            image_wrp.saveROIs(user_client, rois)
            return
        } catch(Exception e) {
            println("Could not add ROIs to " + image_wrp + " waiting " + waiting_time + " minutes and trying again.")
            println e
            TimeUnit.MINUTES.sleep(waiting_time)
            last_exception = e
            if (!user_client.isConnected()) {
                println("Has been deconnected. Will reconnect.")
                user_client.connect(host, port, USERNAME, PASSWORD.toCharArray())
            }
        }
    }
    throw last_exception
}

def robustlyNewTableWrapper(Client user_client, ResultsTable results, Long imageId, List<? extends Roi> ijRois, String roiProperty) {
    for (waiting_time in waiting_times) {
        try {
            return new TableWrapper(user_client, results, imageId, ijRois, roiProperty)
        } catch(Exception e) {
            println("Could not generate new table for image " + imageId + " waiting " + waiting_time + " minutes and trying again.")
            println e
            TimeUnit.MINUTES.sleep(waiting_time)
            last_exception = e
            if (!user_client.isConnected()) {
                println("Has been deconnected. Will reconnect.")
                user_client.connect(host, port, USERNAME, PASSWORD.toCharArray())
            }
        }
    }
    throw last_exception
}

def robustlyAddRows(TableWrapper table, Client user_client, ResultsTable results, Long imageId, List<? extends Roi> ijRois, String roiProperty) {
    for (waiting_time in waiting_times) {
        try {
            table.addRows(user_client, results, imageId, ijRois, roiProperty)
            return
        } catch(Exception e) {
            if (e.getClass().equals(java.lang.IllegalArgumentException)) {
                throw e
            }
            println("Could not add rows for image " + imageId + " waiting " + waiting_time + " minutes and trying again.")
            println e
            TimeUnit.MINUTES.sleep(waiting_time)
            last_exception = e
            if (!user_client.isConnected()) {
                println("Has been deconnected. Will reconnect.")
                user_client.connect(host, port, USERNAME, PASSWORD.toCharArray())
            }
        }
    }
    throw last_exception
}

def processDataset(Client user_client, DatasetWrapper dataset_wrp,
                   File ilastik_project, String ilastik_project_type,
                   Integer ilastik_label_OI,
                   Double probability_threshold, Double radius_median,
                   Double min_size_particle, Boolean get_spine,
                   Double minimum_diameter_um, Double closeness_tolerance_um, Double min_similarity,
                   String ilastik_project_short_name,
                   File output_directory,
                   Boolean headless_mode, Boolean debug, String tool_version,
                   Boolean use_existing, String final_object, Boolean rescue,
                   Integer ilastik_label_BG, Double probability_threshold_BG,
                   Boolean keep_only_largest, String segmentation_method,
                   Boolean replace_at_runtime,
                   String thresholding_method, String options_do,
                   Integer options_iteration, Integer options_count,
                   Boolean fill_holes_before_median) {
    robustlyGetAll(dataset_wrp, "image", user_client).each{ ImageWrapper img_wrp ->
        if (replace_at_runtime) {
            List<ROIWrapper> rois = robustlyGetROIs(image_wrp, user_client)
            if (!rois.isEmpty()) {
                robustlyDeleteROIs(image_wrp, user_client, rois)
            }
        }
        processImage(user_client, img_wrp,
                     ilastik_project, ilastik_project_type,
                     ilastik_label_OI, probability_threshold,
                     radius_median, min_size_particle, get_spine,
                     minimum_diameter_um, closeness_tolerance_um, min_similarity,
                     ilastik_project_short_name,
                     output_directory,
                     headless_mode, debug, tool_version,
                     use_existing, final_object, rescue,
                     ilastik_label_BG, probability_threshold_BG,
                     keep_only_largest, segmentation_method,
                     thresholding_method, options_do,
                     options_iteration, options_count,
                     fill_holes_before_median)
    }
}

def processSinglePlate(Client user_client, PlateWrapper plate_wrp,
                       File ilastik_project, String ilastik_project_type,
                       Integer ilastik_label_OI,
                       Double probability_threshold, Double radius_median,
                       Double min_size_particle, Boolean get_spine,
                       Double minimum_diameter_um, Double closeness_tolerance_um, Double min_similarity,
                       String ilastik_project_short_name,
                       File output_directory,
                       Boolean headless_mode, Boolean debug, String tool_version, Boolean use_existing,
                       String final_object, Boolean rescue,
                       Integer ilastik_label_BG, Double probability_threshold_BG,
                       Boolean keep_only_largest, String segmentation_method,
                       Boolean replace_at_runtime,
                       String thresholding_method, String options_do,
                       Integer options_iteration, Integer options_count,
                       Boolean fill_holes_before_median) {
    robustlyGetAll(plate_wrp, "well", user_client).each{ well_wrp ->
        processSingleWell(user_client, well_wrp,
                     ilastik_project, ilastik_project_type,
                     ilastik_label_OI, probability_threshold,
                     radius_median, min_size_particle, get_spine,
                     minimum_diameter_um, closeness_tolerance_um, min_similarity,
                     ilastik_project_short_name,
                     output_directory,
                     headless_mode, debug, tool_version,
                     use_existing, final_object, rescue,
                     ilastik_label_BG, probability_threshold_BG,
                     keep_only_largest, segmentation_method,
                     replace_at_runtime,
                     thresholding_method, options_do,
                     options_iteration, options_count,
                     fill_holes_before_median)
    }
}

def processSingleWell(Client user_client, WellWrapper well_wrp,
                      File ilastik_project, String ilastik_project_type,
                      Integer ilastik_label_OI,
                      Double probability_threshold, Double radius_median,
                      Double min_size_particle, Boolean get_spine,
                      Double minimum_diameter_um, Double closeness_tolerance_um, Double min_similarity,
                      String ilastik_project_short_name,
                      File output_directory,
                      Boolean headless_mode, Boolean debug, String tool_version, Boolean use_existing,
                      String final_object, Boolean rescue,
                      Integer ilastik_label_BG, Double probability_threshold_BG,
                      Boolean keep_only_largest, String segmentation_method,
                      Boolean replace_at_runtime,
                      String thresholding_method, String options_do,
                      Integer options_iteration, Integer options_count,
                      Boolean fill_holes_before_median) {
    well_wrp.getWellSamples().each{
        ImageWrapper img_wrp = it.getImage()
        if (replace_at_runtime) {
            List<ROIWrapper> rois = robustlyGetROIs(img_wrp, user_client)
            if (!rois.isEmpty()) {
                robustlyDeleteROIs(img_wrp, user_client, rois)
            }
        }
        processImage(user_client, img_wrp,
                     ilastik_project, ilastik_project_type,
                     ilastik_label_OI, probability_threshold,
                     radius_median, min_size_particle, get_spine,
                     minimum_diameter_um, closeness_tolerance_um, min_similarity,
                     ilastik_project_short_name,
                     output_directory,
                     headless_mode, debug, tool_version,
                     use_existing, final_object, rescue,
                     ilastik_label_BG, probability_threshold_BG,
                     keep_only_largest, segmentation_method,
                     thresholding_method, options_do,
                     options_iteration, options_count,
                     fill_holes_before_median)
    }
}

def processImage(Client user_client, ImageWrapper image_wrp,
                 File ilastik_project, String ilastik_project_type, // String ilastik_strategy,
                 Integer ilastik_label_OI,
                 Double probability_threshold, Double radius_median, Double min_size_particle,
                 Boolean get_spine,
                 Double minimum_diameter_um, Double closeness_tolerance_um, Double min_similarity,
                 String ilastik_project_short_name,
                 File output_directory,
                 Boolean headless_mode, Boolean debug, String tool_version,
                 Boolean use_existing, String final_object, Boolean rescue,
                 Integer ilastik_label_BG, Double probability_threshold_BG,
                 Boolean keep_only_largest, String segmentation_method,
                 String thresholding_method, String options_do,
                 Integer options_iteration, Integer options_count,
                 Boolean fill_holes_before_median) {

    IJ.run("Close All", "")
    IJ.run("Clear Results")
    // Clean ROI manager
    if (!headless_mode) {
        rm = new RoiManager()
        rm = rm.getRoiManager()
        rm.reset()
    }

    // Print image information
    println "\n Image infos"
    String image_basename = image_wrp.getName()
    println ("Image_name : " + image_basename + " / id : " + image_wrp.getId())
    List<DatasetWrapper> dataset_wrp_list = robustlyGetAll(image_wrp, "dataset", user_client)

    // if the image is part of a dataset
    if(!dataset_wrp_list.isEmpty()){
        dataset_wrp_list.each{
            println("dataset_name : "+it.getName()+" / id : "+it.getId())
        }
        robustlyGetAll(image_wrp, "project", user_client).each{
            println("Project_name : "+it.getName()+" / id : "+it.getId())
        }
    }

    // if the image is part of a plate
    else {
        robustlyGetAll(image_wrp, "well", user_client).each{
            println ("Well_name : "+it.getName() +" / id : "+ it.getId())
        }
        robustlyGetAll(image_wrp, "plate", user_client).each{
            println ("plate_name : "+it.getName() + " / id : "+ it.getId())
        }
        robustlyGetAll(image_wrp, "screen", user_client).each{
            println ("screen_name : "+it.getName() + " / id : "+ it.getId())
        }
    }

    println "Getting image from OMERO"

    ImagePlus imp = robustlytoImagePlus(image_wrp, user_client)
	// ImagePlus imp = IJ.openImage("/home/ldelisle/Desktop/EXP095_LE_PEG_CTGF_PLATE_120h.companion.ome [C2_1_merge].tif")

    if (!headless_mode) {
        imp.show()
    }

    // get imp info
    int[] dim_array = imp.getDimensions()
    int nC = dim_array[2]
    int nT = dim_array[4]

    // Get scale from omero
    PixelsWrapper pixels = image_wrp.getPixels()
    LengthI pixel_size = pixels.getPixelSizeX()
    Double scale = pixel_size.getValue()
    String scale_unit = pixel_size.getUnit().toString()
    // Find the Greys channel:
    int ilastik_input_ch = 1
    ImageProcessor ip
    if (nC > 1) {
        for (int i = 1; i <= nC; i ++) {
            imp.setC(i)
            println "Set channel to "+ i
            ip = imp.getChannelProcessor()
            println ip.getLut().toString()
            // ip.getLut().toString() gives
            // rgb[0]=black, rgb[255]=white, min=3.0000, max=255.0000
            // rgb[0]=black, rgb[255]=green, min=1950.0000, max=2835.0000
            if (ip.getLut().toString().contains("white")) {
                ilastik_input_ch = i
                break
            }
        }
    }
    // Define what will be defined in all cases:
    double pixelWidth
    ImagePlus mask_imp
    List<ROIWrapper> updatedRoisW
    List<Roi> updatedRois
    // Or in both:
    TableWrapper my_table
    Boolean use_roi_name = true

    if (use_existing || rescue) {
        // get the list of image tables
        // store the one with table_name
        robustlyGetTables(image_wrp, user_client).each{ TableWrapper t_wrp ->
            if (t_wrp.getName() == table_name){
                my_table = t_wrp
            }
        }
        if (rescue && my_table == null) {
            // We need to run the segmentation
            use_existing = false
            rescue = false
            rois = robustlyGetROIs(image_wrp, user_client)
            if (!rois.isEmpty()) {
                // Clean existing ROIs
                robustlyDeleteROIs(image_wrp, user_client, rois)
            }
        } else if (rescue && my_table != null) {
            // We just need to generate a table
            use_existing = true
            get_spine = false
        } else if ((!rescue) && my_table == null) {
            throw new Exception("There is no table named " + table_name + " you need to rerun segmentation.")
        }
    }
    if (!use_existing) {
        // We compute the segmentation
        if (segmentation_method == "ilastik") {
            File output_path = new File (output_directory, image_basename+"_ilastik_" + ilastik_project_short_name + "_output.tif" )
            ImagePlus predictions_imp
            FileSaver fs
            if(output_path.exists()) {
                println "USING EXISTING ILASTIK OUTPUT"
                predictions_imp = IJ.openImage( output_path.toString() )
            } else {
                /**
                *  ilastik
                */
                println "Starting ilastik"

                // get ilastik predictions for each time point of the Time-lapse but all at the same time
                ImagePlus ilastik_input_original = new Duplicator().run(imp, ilastik_input_ch, ilastik_input_ch, 1, 1, 1, nT);

                ImagePlus gb_imp = ilastik_input_original.duplicate()
                IJ.run(gb_imp, "Gaussian Blur...", "sigma=100 stack")
                ImagePlus ilastik_input = ImageCalculator.run(ilastik_input_original, gb_imp, "Divide create 32-bit stack")
                if (!headless_mode) {ilastik_input.show()}
                // can't work without displaying image
                // IJ.run("Run Pixel Classification Prediction", "projectfilename="+ilastik_project+" inputimage="+ilastik_input.getTitle()+" pixelclassificationtype=Probabilities");
                //
                // to use in headless_mode more we need to use a commandservice
                def predictions_imgPlus
                if (ilastik_project_type == "Regular") {
                    predictions_imgPlus = cmds.run( IlastikPixelClassificationCommand.class, false,
                                                    'inputImage', ilastik_input,
                                                    'projectFileName', ilastik_project,
                                                    'pixelClassificationType', "Probabilities").get().getOutput("predictions")
                } else {
                    predictions_imgPlus = cmds.run( IlastikAutoContextCommand.class, false,
                                                    'inputImage', ilastik_input,
                                                    'projectFileName', ilastik_project,
                                                    'AutocontextPredictionType', "Probabilities").get().getOutput("predictions")
                }
                // to convert the result to ImagePlus : https://gist.github.com/GenevieveBuckley/460d0abc7c1b13eee983187b955330ba
                predictions_imp = ImageJFunctions.wrap(predictions_imgPlus, "predictions")

                predictions_imp.setTitle("ilastik_output")

                // save file
                fs = new FileSaver(predictions_imp)
                fs.saveAsTiff(output_path.toString() )
            }
            if (!headless_mode) {  predictions_imp.show()   }

            /**
            * From the "ilastik predictions of the Time-lapse" do segmentation and cleaning
            */

            // Get a stack of ROI for background:
            if (ilastik_label_BG != 0) {
                ImagePlus mask_imp_BG = new Duplicator().run(predictions_imp, ilastik_label_BG, ilastik_label_BG, 1, 1, 1, nT)
                // Apply threshold:
                IJ.setThreshold(mask_imp_BG, probability_threshold_BG, 100.0000)
                Prefs.blackBackground = true
                IJ.run(mask_imp_BG, "Convert to Mask", "method=Default background=Dark black")
                if (!headless_mode) {  mask_imp_BG.show() }
                IJ.run(mask_imp_BG, "Analyze Particles...", "stack show=Overlay")
                Overlay ov_BG = mask_imp_BG.getOverlay()
                Overlay ov_BG_Combined = new Overlay()
                for (int t=1;t<=nT;t++) {
                    // Don't ask me why we need to refer to Z pos and not T/Frame
                    ArrayList<Roi> all_rois_inT = ov_BG.findAll{ roi -> roi.getZPosition() == t}
                    println "There are " + all_rois_inT.size() + " in time " + t
                    if (all_rois_inT.size() > 0) {
                        ShapeRoi current_roi = new ShapeRoi(all_rois_inT[0] as Roi)
                        for (i = 1; i < all_rois_inT.size(); i++) {
                            current_roi = current_roi.or(new ShapeRoi(all_rois_inT[i] as Roi))
                        }
                        // Update the position before adding to the ov_BG_Combined
                        current_roi.setPosition( ilastik_input_ch, 1, t)
                        current_roi.setName("Background_t" + t)
                        ov_BG_Combined.add(current_roi)
                    }
                }
                IJ.run("Clear Results")
                println "Store " + ov_BG_Combined.size() + " BG ROIs on OMERO"
                // Save ROIs to omero
                robustlysaveROIs(image_wrp, user_client, ROIWrapper.fromImageJ(ov_BG_Combined as List))
            }

            // Get only the channel for the gastruloid/background prediction
            mask_imp = new Duplicator().run(predictions_imp, ilastik_label_OI, ilastik_label_OI, 1, 1, 1, nT);

            // Apply threshold:
            IJ.setThreshold(mask_imp, probability_threshold, 100.0000);
            Prefs.blackBackground = true;
            IJ.run(mask_imp, "Convert to Mask", "method=Default background=Dark black");

        } else {
            // Get only the channel with bright field
            mask_imp = new Duplicator().run(imp, ilastik_input_ch, ilastik_input_ch, 1, 1, 1, nT);
            // Run convert to mask
            Thresholder my_thresholder = new Thresholder()
            my_thresholder.setMethod(thresholding_method)
            my_thresholder.setBackground("Light")
            Prefs.blackBackground = true
            my_thresholder.convertStackToBinary(mask_imp)
        }
        // This title will appear in the result table
        mask_imp.setTitle(image_basename)
        if (!headless_mode) {  mask_imp.show() }
        IJ.run(mask_imp, "Options...", "iterations=" + options_iteration + " count=" + options_count + " black do=" + options_do + " stack")
        if (fill_holes_before_median) {
            IJ.run(mask_imp, "Fill Holes", "stack")
        }
        println "Smoothing mask"

        // Here I need to check if we first fill holes or first do the median
        IJ.run(mask_imp, "Median...", "radius=" + radius_median + " stack");

        IJ.run(mask_imp, "Fill Holes", "stack");

        // find gastruloids and measure them

        IJ.run("Set Measurements...", "area feret's perimeter shape display redirect=None decimal=3")

        IJ.run(mask_imp, "Set Scale...", "distance=1 known=" + scale + " unit=micron")
        pixelWidth = mask_imp.getCalibration().pixelWidth
        println "pixelWidth is " + pixelWidth
        // Exclude the edge
        IJ.run(mask_imp, "Analyze Particles...", "size=" + min_size_particle + "-Infinity stack exclude show=Overlay");

        println "Found " + rt.size() + " ROIs"

        Overlay ov = mask_imp.getOverlay()
        // We store in clean_overlay all gastruloids to measure
        // They must have names and appropriate position
        Overlay clean_overlay = new Overlay()
        if (keep_only_largest) {
            // Let's keep only the largest area for each time:
            Roi largest_roi_inT
            for (int t=1;t<=nT;t++) {
                // Don't ask me why we need to refer to Z pos and not T/Frame
                ArrayList<Roi> all_rois_inT = ov.findAll{ roi -> roi.getZPosition() == t}
                // When there is a single time the ROI has ZPosition to 0:
                if (nT == 1) {
                	all_rois_inT = (ov as List)
                    if (all_rois_inT == null) {
                        all_rois_inT = []
                    }
                }
                println "There are " + all_rois_inT.size() + " in time " + t
                if (all_rois_inT.size() > 0) {
                    largest_roi_inT = Collections.max(all_rois_inT, Comparator.comparing((roi) -> roi.getStatistics().area ))
                    largest_roi_inT.setName("Gastruloid_t" + t + "_id1")
                } else {
                    // We arbitrary design a ROI of size 1x1
                    largest_roi_inT = new Roi(0,0,1,1)
                    largest_roi_inT.setName("GastruloidNotFound_t" + t)
                }
                // Update the position before adding to the clean_overlay
                largest_roi_inT.setPosition( ilastik_input_ch, 1, t)
                clean_overlay.add(largest_roi_inT)
                if (!headless_mode) {
                    rm.addRoi(largest_roi_inT)
                }
            }
        } else {
            // We keep all
            // We store the last number given:
            int[] lastID = new int[nT]
            ov.each{ Roi roi ->
                // Don't ask me why we need to refer to Z pos and not T/Frame
                t = roi.getZPosition()
                // When there is a single time the ROI has ZPosition to 0:
                if (nT == 1) {
                	t = 1
                }
                id = lastID[t - 1] + 1
                roi.setName("Gastruloid_t" + t + "_id" + id)
                // Increase lastID:
                lastID[t - 1] += 1
                // Update the position before adding to the clean_overlay
                roi.setPosition( ilastik_input_ch, 1, t)
                clean_overlay.add(roi)
                if (!headless_mode) {
                    rm.addRoi(roi)
                }
            }
            // Fill timepoints with no ROI with notfound:
            Roi roi
            for (int t=1;t<=nT;t++) {
                if (lastID[t - 1] == 0) {
                    // We arbitrary design a ROI of size 1x1
                    roi = new Roi(0,0,1,1)
                    roi.setName("GastruloidNotFound_t" + t)
                    // Update the position before adding to the clean_overlay
                    roi.setPosition( ilastik_input_ch, 1, t)
                    clean_overlay.add(roi)
                }

            }
        }
        // Measure this new overlay:
        rt = clean_overlay.measure(imp)

        // Get Date
        Date date = new Date()
        String now = date.format("yyyy-MM-dd_HH-mm")

        // Add Date, version and params
        for ( int row = 0;row<rt.size();row++) {
            rt.setValue("Unit", row, scale_unit)
            rt.setValue("Date", row, now)
            rt.setValue("Version", row, tool_version)
            if (segmentation_method == "ilastik") {
                rt.setValue("IlastikProject", row, ilastik_project_short_name)
                rt.setValue("ProbabilityThreshold", row, probability_threshold)
                rt.setValue("ThresholdingMethod", row, "NA")
            } else {
                rt.setValue("IlastikProject", row, "NA")
                rt.setValue("ProbabilityThreshold", row, "NA")
                rt.setValue("ThresholdingMethod", row, thresholding_method)
            }
            rt.setValue("OptionsDo", row, options_do)
            rt.setValue("OptionsIterations", row, options_iteration)
            rt.setValue("OptionsCount", row, options_count)
            rt.setValue("FillHolesBefore", row, "" + fill_holes_before_median)
            rt.setValue("RadiusMedian", row, radius_median)
            rt.setValue("MinSizeParticle", row, min_size_particle)
            rt.setValue("MinDiameter", row, minimum_diameter_um)
            rt.setValue("ClosenessTolerance", row, closeness_tolerance_um)
            rt.setValue("MinSimilarity", row, min_similarity)
            String label = rt.getLabel(row)
            rt.setValue("BaseImage", row, label.split(":")[0])
            rt.setValue("ROI", row, label.split(":")[1])
            // In simple-omero-client
            // Strings that can be converted to double are stored in double
            // in omero so to create the super_table we need to store all
            // them as Double:
            rt.setValue("Time", row, label.split(":")[1].split("_t")[-1].split("_id")[0] as Double)
            rt.setValue("ROI_type", row, label.split(":")[1].split("_t")[0])
            Roi current_roi = clean_overlay[row]
            Double[] centroid = current_roi.getContourCentroid()
            rt.setValue("XCentroid", row, centroid[0])
            rt.setValue("YCentroid", row, centroid[1])
            assert label.split(":")[1] == current_roi.getName() : "Name in ov does not match name in rt";
        }
        println "Store " + clean_overlay.size() + " ROIs on OMERO"
        // Save ROIs to omero
        robustlysaveROIs(image_wrp, user_client, ROIWrapper.fromImageJ(clean_overlay as List))

        // Get them back with IDs:
        updatedRoisW = robustlyGetROIs(image_wrp, user_client)
        updatedRois = ROIWrapper.toImageJ(updatedRoisW, "ROI")
    } else {
        // reinitialize the rt
        rt = new ResultsTable()
        // The first column (index 0) of the result table is the image ID
        // The second column (index 1) is the ROI ID
        // Add all others values
        for (icol = 2; icol < my_table.getColumnCount(); icol ++) {
            colname = my_table.getColumnName(icol)
            for (row = 0; row < my_table.getRowCount(); row ++) {
                rt.setValue(colname, row, my_table.getData(row, icol))
            }
        }
        // Add ROI column
        use_roi_name = false
        for ( int row = 0;row<rt.size();row++) {
            rt.setValue("ROI", row, my_table.getData(row, 1).getId())
        }
        if (!rescue) {
            // Get the ROI ids associated with the measures of the table
            Long[] gastruloid_roi_ids = (my_table.getData()[1]).collect{
                it.getId()
            }
            // Sort the array:
            Arrays.sort(gastruloid_roi_ids)
            // Get Date
            Date date = new Date()
            String now = date.format("yyyy-MM-dd_HH-mm")

            // Add Date, version and params
            for ( int row = 0;row<rt.size();row++) {
                rt.setValue("Date_rerun_spine", row, now)
                rt.setValue("Version_rerun_spine", row, tool_version)
                rt.setValue("MinDiameter", row, minimum_diameter_um)
                rt.setValue("ClosenessTolerance", row, closeness_tolerance_um)
                rt.setValue("MinSimilarity", row, min_similarity)
            }
            // Remove any roi which is not gastruloid:
            println "Remove ROIs other than segmentation results and tables"
            // In order to reduce the number of 'servantsPerSession'
            // Which reached 10k and then caused failure
            // I store them in a list
            ArrayList<ROIWrapper> ROIW_list_to_delete = []
            robustlyGetROIs(image_wrp, user_client).each{
                if (Arrays.binarySearch(gastruloid_roi_ids, it.getId()) < 0) {
                    // user_client.delete(it)
                    String roi_name = it.toImageJ().get(0).getName()
                    if (!roi_name.startsWith("Background_t")) {
                        ROIW_list_to_delete.add(it)
                    }
                }
            }
            // Then I should use
            // user_client.delete(ROIW_list_to_delete)
            // Because of https://github.com/GReD-Clermont/simple-omero-client/issues/59
            // I use
            if (ROIW_list_to_delete.size() > 0) {
                robustlyDeleteROIs(image_wrp, user_client,  ROIW_list_to_delete)
            }
            robustlyDeleteTables(image_wrp, user_client)

            // Retrieve the ROIs from omero:
            updatedRoisW = robustlyGetROIs(image_wrp, user_client)
            updatedRois = ROIWrapper.toImageJ(updatedRoisW, "ROI")
            // Create a clean mask
            mask_imp = IJ.createImage("CleanMask", "8-bit black", imp.getWidth(), imp.getHeight(), nT);
            if (nT > 1) {
                HyperStackConverter.toHyperStack(mask_imp, 1, 1, nT, "xyctz", "Color");
            }
            if (!headless_mode) {mask_imp.show()}
            for (roi in updatedRois) {
                t = roi.getTPosition()
                Overlay t_ov = new Overlay(roi)
                // Fill the frame t with the roi
                mask_imp.setT(t)
                t_ov.fill(mask_imp,  Color.white, Color.black)
            }
            IJ.run(mask_imp, "Set Scale...", "distance=1 known=" + scale + " unit=micron")
            pixelWidth = mask_imp.getCalibration().pixelWidth
            println "pixelWidth is " + pixelWidth

        } else {
            // Retrieve the ROIs from omero:
            updatedRoisW = robustlyGetROIs(image_wrp, user_client)
            updatedRois = ROIWrapper.toImageJ(updatedRoisW, "ROI")
        }
    }
    if (get_spine) {
        // println use_roi_name
        // Scan ROIs and
        // Put them in HashMap
        Map<String, Roi> gastruloid_rois = new HashMap<>()
        for (roi_i = 0; roi_i < updatedRois.size(); roi_i ++) {
            Roi roi = updatedRois[roi_i]
            roi_name = roi.getName()
            if (roi_name.toLowerCase().startsWith("gastruloid") && !roi_name.toLowerCase().startsWith("gastruloidnotfound")) {
                // println "Putting " + roi_name + " in table."
                if (use_roi_name) {
                    assert !gastruloid_rois.containsKey(roi_name); "Duplicated gastruloid ROI name"
                    gastruloid_rois.put(roi_name, roi)
                } else {
                    // println "ID is: " + updatedRoisW[roi_i].getId()
                    gastruloid_rois.put(updatedRoisW[roi_i].getId(), roi)
                }
            }
        }
        for (int row = 0 ; row < rt.size();row++) {
            String roi_type = rt.getStringValue("ROI_type", row)
            if (roi_type == "Gastruloid") {
                // Find the corresponding ROI
                String roi_name
                Roi current_roi
                if (use_roi_name) {
                    roi_name = rt.getStringValue("ROI", row)
                    current_roi = gastruloid_rois.get(roi_name)
                } else {
                    Long roi_id = rt.getValue("ROI", row) as int
                    current_roi = gastruloid_rois.get(roi_id)
                    roi_name = current_roi.getName()
                }
                println roi_name

                assert current_roi != null; "The ROI of row " + row + "is not on OMERO"
                t = current_roi.getTPosition()
                assert t == rt.getValue("Time", row) as int; "T position does not match Time in rt"
                /**
                * The MaxInscribedCircles magic is here
                */
                
                ImagePlus mask_imp_single = new Duplicator().run(mask_imp, 1, 1, 1, 1, t, t)
                mask_imp_single.setRoi(current_roi)
                
                isSelectionOnly = true
                isGetSpine = true
                appendPositionToName = false
                MaxInscribedCircles mic = MaxInscribedCircles.builder(mask_imp_single)
                    .minimumDiameter((int)(minimum_diameter_um / pixelWidth))
                    .useSelectionOnly(isSelectionOnly)
                    .getSpine(isGetSpine)
                    .spineClosenessTolerance((int)(closeness_tolerance_um / pixelWidth))
                    .spineMinimumSimilarity(min_similarity)
                    .appendPositionToName(appendPositionToName)
                    .build()
                println "Get spines"
                mic.process()
                List<Roi> circles_t = mic.getCircles()
                Roi spine_roi = mic.getSpines()[0]

                /**
                *  For each Time-point, find the :
                *  - the largest cicle
                *  - the spine, and the coordinates of end-points
                *  Measure distances and inverses spine roi if necessary
                *  Add value to table with Elongation Index
                */

                if (circles_t.size() > 0) {
                    Roi largestCircle_roi = circles_t[0]
                    largestCircle_roi.setPosition( ilastik_input_ch, 1, t)
                    println largestCircle_roi
                    def xC = largestCircle_roi.x + largestCircle_roi.width/2
                    def yC = largestCircle_roi.y + largestCircle_roi.height/2
                    println "Largest Circle center : "  + xC + "," + yC
                    double circle_roi_radius = largestCircle_roi.width/2
                    ArrayList<Roi> rois_to_add_to_omero
                    rt.setValue("LargestRadius", row, circle_roi_radius * pixelWidth)
                    if (debug) {
                        circles_t.each{
                        	it.setPosition(ilastik_input_ch, 1, t)
                        	it.setName(it.getName() + "_" + roi_name)
                        }
                        // First put all circles to omero:
                        robustlysaveROIs(image_wrp, user_client, ROIWrapper.fromImageJ(circles_t as List))
                        if (!headless_mode) {
                            (circles_t as List).each{ rm.addRoi(it)}
                        }
                    } else {
                        // First put the largest circle to omero:
                        largestCircle_roi.setName(largestCircle_roi.getName() + "_" + roi_name)
                        robustlysaveROIs(image_wrp, user_client, ROIWrapper.fromImageJ([largestCircle_roi] as List))
                        if (!headless_mode) {
                            rm.addRoi(largestCircle_roi)
                        }
                    }

                    // get the Spine, and its points
                    println "Spine is " + spine_roi
                    if (spine_roi != null){
                        //println spine_roi
                        println "Get points coo"
                        Double[] spine_xs = spine_roi.getFloatPolygon().xpoints as List
                        Double[] spine_ys = spine_roi.getFloatPolygon().ypoints as List
                        //println spine_xs
                        //println spine_ys

                        // Measure distance between spine end-points and center of the largest circle
                        Double d1 = Math.sqrt( Math.pow(spine_xs[0]  - xC,2) + Math.pow(spine_ys[0]  - yC,2) )
                        Double d2 = Math.sqrt( Math.pow(spine_xs[-1] - xC,2) + Math.pow(spine_ys[-1] - yC,2) )
                        //println d1
                        //println d2

                        if (d2 < d1){
                            println "re-orient spine"
                            // make a new polyline roi from a list of points
                            spine_name = spine_roi.getName()
                            spine_roi = new PolygonRoi(spine_xs.reverse() as int[], spine_ys.reverse() as int[], spine_xs.size(), PolygonRoi.POLYLINE)
                            spine_roi.setName(spine_name)
                        } else {
                            println "orientation of spine is ok"
                        }

                        double line_roi_length = spine_roi.getLength()
                        rt.setValue("SpineLength", row, line_roi_length * pixelWidth)
                        rt.setValue("ElongationIndex", row, line_roi_length / (2*circle_roi_radius))
                        spine_roi.setPosition( ilastik_input_ch, 1, t)
                        spine_roi.setName(spine_roi.getName() + "_" + roi_name)
                        robustlysaveROIs(image_wrp, user_client, ROIWrapper.fromImageJ([spine_roi] as List))
                        if (!headless_mode) {
                            rm.addRoi(spine_roi)
                        }
                    } else {
                        rt.setValue("SpineLength", row, 0)
                        rt.setValue("ElongationIndex", row, 1)
                    }
                } else {
                    rt.setValue("LargestRadius", row, 0)
                    rt.setValue("SpineLength", row, 0)
                    rt.setValue("ElongationIndex", row, 0)
                }
            } else {
                rt.setValue("LargestRadius", row, 0)
                rt.setValue("SpineLength", row, 0)
                rt.setValue("ElongationIndex", row, -1)
            }
        }
    }

    // Create an omero table:
    println "Create an omero table"
    TableWrapper table_wrp = robustlyNewTableWrapper(user_client, rt, image_wrp.getId(), updatedRois, "ROI")

    if (!rescue) {
        // upload the table on OMERO
        table_wrp.setName(table_name)
        robustlyAddAndReplaceTable(image_wrp, user_client, table_wrp)
    }
    // add the same infos to the super_table
    if (super_table == null) {
        println "super_table is null"
        super_table = table_wrp
    } else {
        println "adding rows"
        robustlyAddRows(super_table, user_client, rt, image_wrp.getId(), updatedRois, "ROI")
    }
    println super_table.getRowCount()
    println "Writting measurements to file"
    rt.save(output_directory.toString() + '/' + image_basename + "__Results.csv" )

    // Put all ROIs in overlay:
    Overlay global_overlay = new Overlay()
    ROIWrapper.toImageJ(robustlyGetROIs(image_wrp, user_client), "ROI").each{
        global_overlay.add(it)
    }

    imp.setOverlay(global_overlay)

    // save file
    fs = new FileSaver(imp)
    output_path = new File (output_directory, image_basename + ".tiff" )
    fs.saveAsTiff(output_path.toString() )

    return
}

// In simple-omero-client
// Strings that can be converted to double are stored in double
// In order to build the super_table, tool_version should stay String
String tool_version = "White_v20240214"

// User set variables

#@ String(visibility=MESSAGE, value="Inputs", required=false) msg
#@ String(label="User name") USERNAME
#@ String(label="PASSWORD", style='PASSWORD', value="", persist=false) PASSWORD
#@ String(label="File path with omero credentials") credentials
#@ String(label="omero host server") host
#@ Integer(label="omero host server port", value=4064) port
#@ String(label="Object", choices={"image","dataset","well","plate"}) object_type
#@ Long(label="ID", value=119273) id

#@ String(visibility=MESSAGE, value="Parameters for segmentation/ROI", required=false) msg2
#@ Boolean(label="Use existing segmentation (values below in the section will be ignored)") use_existing
#@ Boolean(label="Replace ROIs and Tables") replace_at_runtime
#@ String(label="Segmentation Method", choices={"convert_to_mask","ilastik"}) segmentation_method
#@ Boolean(label="<html>Run in rescue mode<br/>(only segment images without tables)</html>", value=false) rescue
#@ String(label="Options do", choices={"Nothing", "Erode", "Dilate", "Open", "Close", "Outline", "Fill Holes", "Skeletonize"}) options_do
#@ Integer(label="Options iteration", min=1, value=1) options_iteration
#@ Integer(label="Options count", min=1, max=8, value=1) options_count
#@ Boolean(label="Fill holes before Median", value=false) fill_holes_before_median
#@ Double(label="Radius for median (=smooth the mask)", min=1, value=10) radius_median
#@ Double(label="Minimum surface for Analyze Particle", value=20000) min_size_particle
#@ Boolean(label="Keep only one gastruloid per timepoint", value=true) keep_only_largest

#@ String(visibility=MESSAGE, value="Parameters for ilastik (ignore if you choose convert_to_mask)", required=false) msg2_1
#@ File(label="Ilastik project") ilastik_project
#@ String(label="Ilastik project short name") ilastik_project_short_name
#@ String(label="Ilastik project type", choices={"Regular", "Auto-context"}, value="Regular") ilastik_project_type
#@ Integer(label="Ilastik label of interest", min=1, value=1) ilastik_label_OI
#@ Double(label="Probability threshold for ilastik", min=0, max=1, value=0.65) probability_threshold

#@ String(visibility=MESSAGE, value="Parameters for convert_to_mask (ignore if you choose ilastik)", required=false) msg2_2
#@ String(label="Thresholding method", choices={"Default", "Huang", "Intermodes", "IsoData", "IJ_IsoData", "Li", "MaxEntropy", "Mean", "MinError", "Minimum", "Moments", "Otsu", "Percentile", "RenyiEntropy", "Shanbhag", "Triangle", "Yen"}) thresholding_method

#@ String(visibility=MESSAGE, value="Parameters for segmentation/ROI of background", required=false) msg3
#@ Integer(label="Ilastik label of background (put 0 if not present)", min=0, value=1) ilastik_label_BG
#@ Double(label="Probability threshold for background in ilastik", min=0, max=1, value=0.8) probability_threshold_BG

#@ String(visibility=MESSAGE, value="Parameters for elongation index", required=false) msg4
#@ Boolean(label="Compute spine", value=true) get_spine
#@ Double(label="Minimum diameter of inscribed circles (um)", min=0, value=40) minimum_diameter_um
#@ Double(label="Closeness Tolerance (Spine) (um)", min=0, value=50) closeness_tolerance_um
#@ Double(label="Min similarity (Spine)", min=-1, max=1, value=0.1) min_similarity

#@ String(visibility=MESSAGE, value="Parameters for output", required=false) msg5
#@ File(style = "directory", label="Directory where measures are put") output_directory
#@ Boolean(label="<html>Run in debug mode<br/>(get all inscribed circles)</html>", value=false) debug

// Handle incompatibilities:
if (rescue && use_existing) {
    throw new Exception("rescue and use_existing modes are incompatible")
}
if (replace_at_runtime && use_existing) {
    throw new Exception("replace_at_runtime and use_existing modes are incompatible")
}
if (use_existing && !get_spine) {
    throw new Exception("use_existing mode requires get_spine")
}


#@ ResultsTable rt
#@ CommandService cmds
#@ ConvertService cvts
#@ DatasetService ds
#@ DatasetIOService io

// Detect if is headless
// java.awt.GraphicsEnvironment.checkheadless_mode(GraphicsEnvironment.java:204)
Boolean headless_mode = GraphicsEnvironment.isHeadless()
if (headless_mode) {
    println "Running in headless mode"
}

// Define rm even if in headless mode
def rm
if (!headless_mode){
    // Reset RoiManager and Result table if not in headless
    rm = new RoiManager()
    rm = rm.getRoiManager()
    rm.reset()
    // Reset the table
    if (rt != null) {
        rt.reset()
    }
}

if (PASSWORD == "") {
    File cred_file = new File(credentials)
    if (!cred_file.exists()) {
        throw new Exception("Password or credential file need to be set.")
    }
    String creds = FileUtils.readFileToString(cred_file, "UTF-8")
    if (creds.split("\n").size() < 2) {
        throw new Exception("Credential file requires 2 lines")
    }
    USERNAME = creds.split("\n")[0]
    PASSWORD = creds.split("\n")[1]
}

// Connection to server
Client user_client = new Client()
user_client.connect(host, port, USERNAME, PASSWORD.toCharArray())

// This super_table is a global variable and will be filled each time an image is processed.
super_table = null
// table_name is also a global variable
table_name = "Measurements_from_Galaxy_phase"
if (user_client.isConnected()) {
    println "\nConnected to "+host
    println "Results will be in " + output_directory.toString()

    try {

        switch (object_type) {
            case "image":
                ImageWrapper image_wrp = robustlyGetOne(id, "image", user_client)
                if (!use_existing) {
                    List<TableWrapper> tables = robustlyGetTables(image_wrp, user_client)
                    if (!tables.isEmpty()) {
                        if (replace_at_runtime) {
                            robustlyDeleteTables(image_wrp, user_client)
                        } else {
                            throw new Exception("There should be no table associated to the image before segmentation. Please clean the image.")
                        }
                    }
                    if (!rescue) {
                        List<ROIWrapper> rois = robustlyGetROIs(image_wrp, user_client)
                        if (!rois.isEmpty()) {
                            if (replace_at_runtime) {
                                robustlyDeleteROIs(image_wrp, user_client, rois)
                            } else {
                                throw new Exception("There should be no ROIs associated to the image before segmentation. Please clean the image.")
                            }
                        }
                    }
                }
                processImage(user_client, image_wrp,
                    ilastik_project, ilastik_project_type,
                    ilastik_label_OI,
                    probability_threshold, radius_median, min_size_particle,
                    get_spine, minimum_diameter_um, closeness_tolerance_um, min_similarity,
                    ilastik_project_short_name,
                    output_directory,
                    headless_mode, debug, tool_version,
                    use_existing, "image", rescue,
                    ilastik_label_BG, probability_threshold_BG,
                    keep_only_largest, segmentation_method,
                    thresholding_method, options_do,
                    options_iteration, options_count,
                    fill_holes_before_median)
                break
            case "dataset":
                DatasetWrapper dataset_wrp = robustlyGetOne(id, "dataset", user_client)
                if (use_existing || replace_at_runtime) {
                    // Remove the tables associated to the dataset
                    robustlyDeleteTables(dataset_wrp, user_client)
                } else if (rescue) {
                        List<TableWrapper> tables = robustlyGetTables(dataset_wrp, user_client)
                        if (!tables.isEmpty()) {
                            throw new Exception("There should be no table associated to the dataset before running rescue mode.")
                        }
                } else {
                    if (robustlyHasAnyTable(dataset_wrp, "dataset", user_client) || robustlyHasAnyROI(dataset_wrp, user_client)) {
                        throw new Exception("ROI or table found in dataset or images. They should be deleted before running analysis.")
                    }
                }
                processDataset(user_client, dataset_wrp,
                     ilastik_project, ilastik_project_type,
                     ilastik_label_OI,
                     probability_threshold, radius_median, min_size_particle,
                     get_spine, minimum_diameter_um, closeness_tolerance_um, min_similarity,
                     ilastik_project_short_name,
                     output_directory,
                     headless_mode, debug, tool_version,
                     use_existing, "dataset", rescue,
                     ilastik_label_BG, probability_threshold_BG,
                     keep_only_largest, segmentation_method,
                     replace_at_runtime,
                     thresholding_method, options_do,
                     options_iteration, options_count,
                     fill_holes_before_median)
                // upload the table on OMERO
                super_table.setName(table_name + "_global")
                robustlyAddAndReplaceTable(dataset_wrp, user_client, super_table)
                break
            case "well":
                WellWrapper well_wrp = robustlyGetOne(id, "well", user_client)
                if (use_existing || replace_at_runtime) {
                    // Remove the tables associated to the well
                    robustlyDeleteTables(well_wrp, user_client)
                } else if (rescue) {
                        List<TableWrapper> tables = robustlyGetTables(well_wrp, user_client)
                        if (!tables.isEmpty()) {
                            throw new Exception("There should be no table associated to the well before running rescue mode.")
                        }
                } else {
                    if (robustlyHasAnyTable(well_wrp, "well", user_client) || robustlyHasAnyROI(well_wrp, user_client)) {
                        throw new Exception("ROI or table found in well or images. They should be deleted before running analysis.")
                    }
                }
                processSingleWell(user_client, well_wrp,
                     ilastik_project, ilastik_project_type,
                     ilastik_label_OI,
                     probability_threshold, radius_median, min_size_particle,
                     get_spine, minimum_diameter_um, closeness_tolerance_um, min_similarity,
                     ilastik_project_short_name,
                     output_directory,
                     headless_mode, debug, tool_version,
                     use_existing, "well", rescue,
                     ilastik_label_BG, probability_threshold_BG,
                     keep_only_largest, segmentation_method,
                     replace_at_runtime,
                     thresholding_method, options_do,
                     options_iteration, options_count,
                     fill_holes_before_median)
                // upload the table on OMERO
                super_table.setName(table_name + "_global")
                robustlyAddAndReplaceTable(well_wrp, user_client, super_table)
                break
            case "plate":
                PlateWrapper plate_wrp = robustlyGetOne(id, "plate", user_client)
                if (use_existing || replace_at_runtime) {
                    // Remove the tables associated to the plate
                    robustlyDeleteTables(plate_wrp, user_client)
                } else if (rescue) {
                        List<TableWrapper> tables = robustlyGetTables(plate_wrp, user_client)
                        if (!tables.isEmpty()) {
                            throw new Exception("There should be no table associated to the plate before running rescue mode.")
                        }
                } else {
                    if (robustlyHasAnyTable(plate_wrp, "plate", user_client) || robustlyHasAnyROI(plate_wrp,  user_client)) {
                        throw new Exception("ROI or table found in plate or images. They should be deleted before running analysis.")
                    }
                }
                processSinglePlate(user_client, plate_wrp,
                     ilastik_project, ilastik_project_type,
                     ilastik_label_OI,
                     probability_threshold, radius_median, min_size_particle,
                     get_spine, minimum_diameter_um, closeness_tolerance_um, min_similarity,
                     ilastik_project_short_name,
                     output_directory,
                     headless_mode, debug, tool_version,
                     use_existing, "plate", rescue,
                     ilastik_label_BG, probability_threshold_BG,
                     keep_only_largest, segmentation_method,
                     replace_at_runtime,
                     thresholding_method, options_do,
                     options_iteration, options_count,
                     fill_holes_before_median)
                // upload the table on OMERO
                super_table.setName(table_name + "_global")
                robustlyAddAndReplaceTable(plate_wrp, user_client, super_table)
                break
        }

    } catch(Exception e) {
        println("Something went wrong: " + e)
        e.printStackTrace()

        if (headless_mode){
            // This is due to Rank Filter + GaussianBlur
            System.exit(1)
        }
    } finally {
        user_client.disconnect()
        println "Disonnected " + host
    }
    if (headless_mode) {
        // This is due to Rank Filter + GaussianBlur
        System.exit(0)
    }

} else {
    throw new Exception("Not able to connect to " + host)
}

return
