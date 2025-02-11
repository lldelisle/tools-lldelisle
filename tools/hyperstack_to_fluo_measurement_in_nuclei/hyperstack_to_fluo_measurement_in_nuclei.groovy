// This macro was written by Lucille Delisle

// Last modification: 2023-08-09

// This macro works both in headless
// or GUI

// The input image(s) may have multiple z-stacks
// The input image(s) may have multiple channels
// The channel(s) to quantify on are set as parameter

// In both modes,
// The result table is written to {image_basename}__Results.csv
// One table per image
// The measures are: Area,Date,Version,Mean_ChannelX,Mean_ChannelY,...

import ij.ImagePlus
import ij.gui.Overlay
// import ij.gui.PolygonRoi
import ij.gui.Roi
// import ij.gui.ShapeRoi
// import ij.gui.TextRoi
import ij.IJ
// import ij.io.FileSaver
// import ij.measure.ResultsTable
import ij.plugin.Duplicator
import ij.plugin.frame.RoiManager
import ij.plugin.Thresholder
import ij.Prefs
// import ij.util.FontUtil

// import java.awt.Color
import java.awt.GraphicsEnvironment
import java.io.File
// import java.util.concurrent.TimeUnit

// import loci.plugins.in.ImporterOptions

// import org.apache.commons.io.FilenameUtils
// import org.apache.commons.io.FileUtils

// def writeResultsFromOv(Client user_client, ImagePlus imp, Integer quantif_ch,
//                        Integer t, Overlay ov,
//                        String now, String tool_version, Integer n_pieces,
//                        Long roi_gastruloid_id, String roi_gastruloid_name,
//                        ImageWrapper image_wpr, String table_id) {
//     imp.setPosition(quantif_ch, 1, t)
//     println "set overlay"
//     imp.setOverlay(ov)
//     // Measure on each item of the overlay
//     def rt_profil = ov.measure(imp)
//     // Debug
//     // println "Columns are:" + rt_profil.getColumnHeadings()
//     if (rt_profil.getColumnIndex("Group") != ResultsTable.COLUMN_NOT_FOUND) {
//         println "Remove Group column"
//         rt_profil.deleteColumn("Group")
//     }

//     // Add Date, version and params
//     for ( int row = 0;row<rt_profil.size();row++) {
//         rt_profil.setValue("Date", row, now)
//         rt_profil.setValue("Version", row, tool_version)
//         rt_profil.setValue("NPieces", row, n_pieces)
//         rt_profil.setValue("Channel", row, quantif_ch)
//         rt_profil.setValue("Time", row, t)
//         String label = rt_profil.getLabel(row)
//         rt_profil.setValue("BaseImage", row, label.split(":")[0])
//         rt_profil.setValue("ROI", row, label.split(":")[1])
//         rt_profil.setValue("ROI_type", row, label.split(":")[1].split("_t")[0])
//         rt_profil.setValue("ROI_Gastruloid_ID", row, roi_gastruloid_id)
//         rt_profil.setValue("ROI_Gastruloid_name", row, roi_gastruloid_name)

//     }

//     // Debug
//     // println "Now columns are:" + rt_profil.getColumnHeadings()

//     println "Store " + ov.size() + " ROIs on OMERO"

//     // Save ROIs to omero
//     robustlysaveROIs(image_wpr, user_client, ROIWrapper.fromImageJ(ov as List))

//     // Get them back with IDs:
//     ArrayList<Roi> updatedRois = []
//     // println "Now there is"
//     updatedRois = ROIWrapper.toImageJ(robustlyGetROIs(image_wpr, user_client), "ROI")
//     println "Writting measurements to file"
//     String image_basename = image_wpr.getName()
//     rt_profil.save(output_directory.toString() + '/' + image_basename+"__"+table_id+"_profil_Results.csv" )
//     if (table_wpr == null) {
//         table_wpr = robustlyNewTableWrapper(user_client, rt_profil, image_wpr.getId(), updatedRois, "ROI")
//         // add the same infos to the super_table
//         if (super_table == null) {
//             println "super_table is null"
//             super_table = robustlyNewTableWrapper(user_client, rt_profil, image_wpr.getId(), updatedRois, "ROI")
//         } else {
//             println "adding rows to super_table"
//             robustlyAddRows(super_table, user_client, rt_profil, image_wpr.getId(), updatedRois, "ROI")
//         }
//     } else {
//         println "adding rows to table_wpr"
//         robustlyAddRows(table_wpr, user_client, rt_profil, image_wpr.getId(), updatedRois, "ROI")
//         println "adding rows to super_table"
//         robustlyAddRows(super_table, user_client, rt_profil, image_wpr.getId(), updatedRois, "ROI")
//     }
// }

def fill_mean_std_values(ImagePlus quantif_imp, Integer quantif_ch,
                     Overlay overlay,
                     Map<String, Map<Integer, Double>> mean_values,
                     Map<String, Map<Integer, Double>> stdev_values,
                      Map<String, Map<Integer, Double>> mode_values,
                       Map<String, Map<Integer, Double>> median_values) {
    // Set the good position
    quantif_imp.setPosition(overlay[0].getPosition())
    // Set the overlay
    quantif_imp.setOverlay(overlay)
    // Measure
    current_rt = overlay.measure(quantif_imp)
    // Put measures in the HashMap
    for ( int row = 0;row<current_rt.size();row++) {
        String label = current_rt.getLabel(row)
        String roi_name = label.split(":")[1]
        if (!mean_values.containsKey(roi_name)) {
            mean_values.put(roi_name, new HashMap<>())
        }
        if (!stdev_values.containsKey(roi_name)) {
            stdev_values.put(roi_name, new HashMap<>())
        }
if (!mode_values.containsKey(roi_name)) {
            mode_values.put(roi_name, new HashMap<>())
        }
if (!median_values.containsKey(roi_name)) {
            median_values.put(roi_name, new HashMap<>())
        }
        assert !mean_values.get(roi_name).containsKey(quantif_ch)
        mean_values.get(roi_name).put(quantif_ch, current_rt.getValue("Mean", row))
        stdev_values.get(roi_name).put(quantif_ch, current_rt.getValue("StdDev", row))
        median_values.get(roi_name).put(quantif_ch, current_rt.getValue("Median", row))
        mode_values.get(roi_name).put(quantif_ch, current_rt.getValue("Mode", row))
    }
    return
}

def processImage(File image_filename, File output_directory,
                 Integer dapi_ch, String thresholding_method,
                 String quantif_chs, Boolean headless_mode,
                 String tool_version) {

    IJ.run("Close All", "")
    IJ.run("Clear Results")
    // Clean ROI manager
    if (!headless_mode) {
        rm = new RoiManager()
        rm = rm.getRoiManager()
        rm.reset()
    }

    ImagePlus imp = IJ.openImage( image_filename.getAbsolutePath() )
    image_basename = imp.getTitle()

    if (!headless_mode) {
        imp.show()
    }

    // get imp info
    dim_array = imp.getDimensions()

    // First segment on DAPI

    // Get only the channel with DAPI
    ImagePlus mask_imp = new Duplicator().run(imp, dapi_ch, dapi_ch, 1, dim_array[3], 1, dim_array[4])
    // Run convert to mask
    Thresholder my_thresholder = new Thresholder()
    my_thresholder.setMethod(thresholding_method)
    my_thresholder.setBackground("Light")
    Prefs.blackBackground = true
    my_thresholder.convertStackToBinary(mask_imp)
    // This title will appear in the result table
    mask_imp.setTitle(image_basename)
    if (!headless_mode) {  mask_imp.show() }
    // Run Watershed
    IJ.run(mask_imp, "Watershed", "stack")
    // Run analyze particles but add it to the overlay
    IJ.run("Set Measurements...", "area display redirect=None decimal=3");
    IJ.run(mask_imp, "Analyze Particles...", "size=25-200 stack show=Overlay")
    Overlay ov = mask_imp.getOverlay()
    Map<Integer, Roi> overlay_per_position = new HashMap<>()
    i = 1
    ov.each { Roi roi ->
        roi.name = "ROI_" + i + "_" + roi.getPosition()
        if (!headless_mode) {
            rm.addRoi(roi)
        }
        if (!overlay_per_position.containsKey(roi.getPosition())) {
            Overlay new_ov = new Overlay()
            new_ov.add(roi)
            overlay_per_position.put(roi.getPosition(), new_ov)
        } else {
            overlay_per_position.get(roi.getPosition()).add(roi)
        }
        i += 1
    }
    // Get Date
    Date date = new Date()
    String now = date.format("yyyy-MM-dd_HH-mm")
    // Initiate the result table on the mask
    rt = ov.measure(mask_imp)
    Map<String, Map<Integer, Double>> mean_values = new HashMap<>()
    Map<String, Map<Integer, Double>> std_values = new HashMap<>()
   	Map<String, Map<Integer, Double>> median_values = new HashMap<>()
    Map<String, Map<Integer, Double>> mode_values = new HashMap<>()
    IJ.run(imp, "Median...", "radius=1");
    IJ.run("Set Measurements...", "mean standard modal median display redirect=None decimal=3");
    for (quantif_ch in quantif_chs.split(",")) {
        quantif_ch_int = quantif_ch as int
        ImagePlus quantif_imp = new Duplicator().run(imp, quantif_ch_int, quantif_ch_int, 1, dim_array[3], 1, dim_array[4])
        for (position in overlay_per_position.keySet()) {
            fill_mean_std_values(quantif_imp, quantif_ch_int,
                             overlay_per_position.get(position),
                             mean_values, std_values, mode_values, median_values)
        }
    }
    // Add Date, version and params
    for ( int row = 0;row<rt.size();row++) {
        rt.setValue("Date", row, now)
        rt.setValue("Version", row, tool_version)
        rt.setValue("Thresholding_method", row, thresholding_method)
        String label = rt.getLabel(row)
        rt.setValue("BaseImage", row, label.split(":")[0])
        String roi_name = label.split(":")[1]
        rt.setValue("ROI", row, roi_name)
        rt.setLabel(label.split(":")[0] + ":" + roi_name, row)
        for (quantif_ch in mean_values.get(roi_name).keySet()) {
            rt.setValue("Mean_channel" + quantif_ch, row, mean_values.get(roi_name).get(quantif_ch))
            rt.setValue("Std_channel" + quantif_ch, row, std_values.get(roi_name).get(quantif_ch))
            rt.setValue("Median_channel" + quantif_ch, row, median_values.get(roi_name).get(quantif_ch))
            rt.setValue("Mode_channel" + quantif_ch, row, mode_values.get(roi_name).get(quantif_ch))
        }
    }
    rt.save(output_directory.toString() + '/' + image_basename + "__Results.csv" )
    if(!headless_mode) {
    	rm.save(output_directory.toString() + '/' + image_basename + "__ROI.zip")
    }
    return
}

// In simple-omero-client
// Strings that can be converted to double are stored in double
// In order to build the super_table, tool_version should stay String
String tool_version = "AllFluoNuclei_v20240729"

// User set variables

#@ String(visibility=MESSAGE, value="Inputs", required=false) msg
#@ File(label="Input image to convert") image_filename

#@ String(visibility=MESSAGE, value="Parameters for quantification", required=false) msg2
#@ Integer(label="Index of the channel with DAPI (1-based)", value="1") dapi_ch
#@ String(label="Thresholding method", choices={"Default", "Huang", "Intermodes", "IsoData", "IJ_IsoData", "Li", "MaxEntropy", "Mean", "MinError", "Minimum", "Moments", "Otsu", "Percentile", "RenyiEntropy", "Shanbhag", "Triangle", "Yen"}) thresholding_method
#@ String(label="Indices of the channels to quantify (1-based) separated by commas", value="1") quantif_chs
#@ String(visibility=MESSAGE, value="Parameters for output", required=false) msg4
#@ File(style = "directory", label="Directory where measures are put") output_directory

IJ.run("Close All", )

// java.awt.GraphicsEnvironment.checkheadless_mode(GraphicsEnvironment.java:204)
Boolean headless_mode = GraphicsEnvironment.isHeadless()
if (headless_mode) {
    println "Running in headless mode"
}

// Define rm even if in headless mode
def rm
if (!headless_mode){
    // Reset RoiManager if not in headless
    rm = new RoiManager()
    rm = rm.getRoiManager()
    rm.reset()
}

processImage(image_filename, output_directory,
             dapi_ch, thresholding_method,
             quantif_chs, headless_mode,
             tool_version)

return