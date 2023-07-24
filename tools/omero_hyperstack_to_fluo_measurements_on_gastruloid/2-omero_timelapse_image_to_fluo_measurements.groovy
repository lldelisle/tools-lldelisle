// This macro was written by Lucille Delisle
// A block was taken from https://www.geeksforgeeks.org/minimum-distance-from-a-point-to-the-line-segment-using-vectors/
// written by 29AjayKumar
// A block was written by Romain Guiet (BIOP, EPFL)
// Interactions with omero are largely inspired by
// templates available at https://github.com/BIOP/OMERO-scripts/tree/main/Fiji

// Last modification: 2023-07-24

// This macro works both in headless
// or GUI

// The input image(s) must be on omero
// The input image(s) may have multiple time stacks
// The input image(s) may have multiple channels
// The channel to quantify on are set as parameter
// The input image(s) must have ROIs on omero
// Gastruloids ROI names must start with gastruloid (case insensitive)
// Spine ROI names must start with spine (case insensitive)

// In both modes,
// The result table is written to {image_basename}__{t}_profil_Results.csv
// One table per time
// The measures are: Area,Mean,Min,Max,Date,Version,NPieces,Channel,Segment
// A single table per image is sent to omero

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
import ij.gui.TextRoi
import ij.IJ
import ij.io.FileSaver
import ij.measure.ResultsTable
import ij.plugin.frame.RoiManager
import ij.util.FontUtil

import java.awt.Color
import java.awt.GraphicsEnvironment
import java.io.File
import java.util.concurrent.TimeUnit

import loci.plugins.in.ImporterOptions

import org.apache.commons.io.FilenameUtils
import org.apache.commons.io.FileUtils

// Global variable with times in minutes to wait:
waiting_times = [0, 10, 60, 360, 600]

def robustlyGetAll(GenericRepositoryObjectWrapper obj_wpr, String object_type, Client user_client) {
    for (waiting_time in waiting_times) {
        try {
            wrappers = null
            switch (object_type) {
                case "image":
                    wrappers = obj_wpr.getImages(user_client)
                    break
                case "dataset":
                    wrappers = obj_wpr.getDatasets(user_client)
                    break
                case "well":
                    wrappers = obj_wpr.getWells(user_client)
                    break
                case "project":
                    wrappers = obj_wpr.getProjects(user_client)
                    break
                case "plate":
                    wrappers = obj_wpr.getPlates(user_client)
                    break
                case "screen":
                    wrappers = obj_wpr.getScreens(user_client)
                    break
            }
            return wrappers
        } catch(Exception e) {
            println("Could not get " + object_type + " for " + obj_wpr + " waiting " + waiting_time + " minutes and trying again.")
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

def robustlytoImagePlus(ImageWrapper image_wpr, Client user_client) {
    for (waiting_time in waiting_times) {
        try {
            return image_wpr.toImagePlus(user_client)
        } catch(Exception e) {
            println("Could not convert to image plus " + image_wpr + " waiting " + waiting_time + " minutes and trying again.")
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

def robustlyGetROIs(ImageWrapper image_wpr,Client user_client) {
    for (waiting_time in waiting_times) {
        try {
            return image_wpr.getROIs(user_client)
        } catch(Exception e) {
            println("Could not get ROIs for " + image_wpr + " waiting " + waiting_time + " minutes and trying again.")
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

def robustlyGetTables(GenericRepositoryObjectWrapper obj_wpr,Client user_client) {
    for (waiting_time in waiting_times) {
        try {
            return obj_wpr.getTables(user_client)
        } catch(Exception e) {
            println("Could not get tables for " + obj_wpr + " waiting " + waiting_time + " minutes and trying again.")
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

def robustlysaveROIs(ImageWrapper image_wpr, Client user_client, List<ROIWrapper> rois) {
    for (waiting_time in waiting_times) {
        try {
            image_wpr.saveROIs(user_client, rois)
            return
        } catch(Exception e) {
            println("Could not add ROIs to " + image_wpr + " waiting " + waiting_time + " minutes and trying again.")
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

def robustlyAddAndReplaceTable(GenericRepositoryObjectWrapper obj_wpr, Client user_client, TableWrapper table) {
    for (waiting_time in waiting_times) {
        try {
            obj_wpr.addAndReplaceTable(user_client, table)
            return
        } catch(Exception e) {
            println("Could not add table to " + obj_wpr + " waiting " + waiting_time + " minutes and trying again.")
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

def robustlyDeleteROIs(ImageWrapper image_wpr, Client user_client, List<ROIWrapper> rois) {
    for (waiting_time in waiting_times) {
        try {
            // Remove existing ROIs
            // image_wpr.getROIs(user_client).each{ user_client.delete(it) }
            // Caused failure due to too high number of 'servantsPerSession'
            // Which reached 10k
            // I use see https://github.com/GReD-Clermont/simple-omero-client/issues/59
            user_client.delete((Collection<ROIWrapper>) rois)
            return
        } catch(Exception e) {
            println("Could not remove ROIs for " + image_wpr + " waiting " + waiting_time + " minutes and trying again.")
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

def robustlyDeleteTableWrapper(Client user_client, TableWrapper table_wpr) {
    for (waiting_time in waiting_times) {
        try {
            user_client.delete(table_wpr)
            return
        } catch(Exception e) {
            println("Could not remove table " + table_wpr + " waiting " + waiting_time + " minutes and trying again.")
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

// This block was taken from https://www.geeksforgeeks.org/minimum-distance-from-a-point-to-the-line-segment-using-vectors/
// written by 29AjayKumar
class pair
{
    double F, S;
    public pair(double F, double S)
    {
        this.F = F;
        this.S = S;
    }
    public pair() {
    }
}
 
// Function to return the minimum distance
// between a line segment AB and a point E
def minDistance(pair A, pair B, pair E)
{
 
    // vector AB
    pair AB = new pair();
    AB.F = B.F - A.F;
    AB.S = B.S - A.S;
 
    // vector BP
    pair BE = new pair();
    BE.F = E.F - B.F;
    BE.S = E.S - B.S;
 
    // vector AP
    pair AE = new pair();
    AE.F = E.F - A.F;
    AE.S = E.S - A.S;
 
    // Variables to store dot product
    double AB_BE, AB_AE;
 
    // Calculating the dot product
    AB_BE = (AB.F * BE.F + AB.S * BE.S);
    AB_AE = (AB.F * AE.F + AB.S * AE.S);
 
    // Minimum distance from
    // point E to the line segment
    double reqAns = 0;
 
    // Case 1
    if (AB_BE > 0)
    {
 
        // Finding the magnitude
        double y = E.S - B.S;
        double x = E.F - B.F;
        reqAns = Math.sqrt(x * x + y * y);
    }
 
    // Case 2
    else if (AB_AE < 0)
    {
        double y = E.S - A.S;
        double x = E.F - A.F;
        reqAns = Math.sqrt(x * x + y * y);
    }
 
    // Case 3
    else
    {
 
        // Finding the perpendicular distance
        double x1 = AB.F;
        double y1 = AB.S;
        double x2 = AE.F;
        double y2 = AE.S;
        double mod = Math.sqrt(x1 * x1 + y1 * y1);
        reqAns = Math.abs(x1 * y2 - y1 * x2) / mod;
    }
    return reqAns;
}
// end of block

/**
 * helper function to get coordonates that split a polyline into n pieces so return 2 arrays of size n+1
 * For example:
 *           B   C   
 *           * --*
 *          /    |
 *         /     | M    the ROI ABCD
 *      A *      |      cut into 3 should gives [[[xA,xB], [xB, xC, xM], [xM, xC], [[yA,yB], [yB, yC, yM], [yM, yD]]]
 *               |
 *               * C
 */

def getCooPoly(PolygonRoi test, int n) {
    // The final x and y will be arrays where each element
    // is an array with coordinates of the ROI which correspond to a piece
    // of x
    x = []
    y = []
    xpoints = test.getFloatPolygon().xpoints
    ypoints = test.getFloatPolygon().ypoints
    total_lines = xpoints.size() - 1
    // The total length using each point may be different from the length provided by `.getLength()`
    // Evaluate the total length useach each point:
    total_length = 0
    for (current_line = 0; current_line < total_lines; current_line ++) {
        start_line_x = xpoints[current_line]
        start_line_y = ypoints[current_line]
        end_line_x = xpoints[current_line + 1]
        end_line_y = ypoints[current_line + 1]
        line_length = Math.sqrt( Math.pow(start_line_x  - end_line_x,2) + Math.pow(start_line_y  - end_line_y,2) )
        total_length += line_length
    }
    // println total_length
    // println test.getLength()

    // current_x and current_y will have all coordinates to build the ROI with the current piece.
    current_x = []
    current_y = []
    // current_line is the current line of the ROI test which we are testing
    current_line = 0
    // current_length is the length of the piece we are building
    current_length = 0
    start_line_x = xpoints[current_line]
    start_line_y = ypoints[current_line]
    // The first piece starts with the first coordinate of the first line
    current_x.add(start_line_x)
    current_y.add(start_line_y)
    end_line_x = xpoints[current_line + 1]
    end_line_y = ypoints[current_line + 1]
    line_length = Math.sqrt( Math.pow(start_line_x  - end_line_x,2) + Math.pow(start_line_y  - end_line_y,2) )
    // we loop over the index of the final pieces starting at 1
    for (istop = 1; istop < n; istop++) {
        // the final piece istop should go from
        // total_length / n * (istop - 1) to total_length / n * istop
        target_length = total_length / n * istop
        // We compare the the length of the ROI formed by the points in current_x current_y
        // Where we add the length of the current line
        // with the target length
        while (current_length + line_length < target_length) {
            // We can integrate the current_line to the piece
            // Go to the next line:
            current_line ++
            // Add the length of the previous line
            current_length += line_length
            // Get the start of the new line (= end of previous line)
            start_line_x = xpoints[current_line]
            start_line_y = ypoints[current_line]
            // Add the coordinate to the piece
            current_x.add(start_line_x)
            current_y.add(start_line_y)
            end_line_x = xpoints[current_line + 1]
            end_line_y = ypoints[current_line + 1]
            // Adjust the line_length to the new line
            line_length = Math.sqrt( Math.pow(start_line_x  - end_line_x,2) + Math.pow(start_line_y  - end_line_y,2) )
        }
        // The target_length is between start_line and end_line:
        // Get the proportion of the line to integrate into this piece:
        prop_line = (target_length - current_length) / line_length
        // Compute the coordinate of this point
        target_x = start_line_x + (end_line_x - start_line_x) * prop_line
        target_y = start_line_y + (end_line_y - start_line_y) * prop_line
        // Add it to the current piece
        current_x.add(target_x as int)
        current_y.add(target_y as int)
        // Add the piece to x and y
        x.add(current_x)
        y.add(current_y)
        // Start a new piece with this point
        current_x = [target_x as int]
        current_y = [target_y as int]
    }
    // To build the last piece, no need to compare lengths
    // Just add all points to the piece
    while(current_line != total_lines) {
        // Go to the next line:
        current_line ++
        start_line_x = xpoints[current_line]
        start_line_y = ypoints[current_line]
        current_x.add(start_line_x)
        current_y.add(start_line_y)
    }
    // Add the final piece to x and y
    x.add(current_x)
    y.add(current_y)
    return([x,y])
}


/** Find the index i of the coordinates of poly_x poly_y
* where poly_x poly_y are the coordinates of a closed polygon
* for which the line composed of the point i i+1
* which is the closest to the point A
* depends on the class pair
* and the function minDistance
*/
def find_index(poly_x, poly_y, pair A) {
    // Initiate to non existing index
    // And a large distance
    closest_seg_start = -1
    dist = 1000000
    for (i = 0; i < (poly_x.size() - 1); i++) {
        // For each line of the ROI poly_x poly_y
        // Compute the distance with pair A
        pair pairstart = new pair(poly_x[i], poly_y[i])
        pair pairend = new pair(poly_x[i+1], poly_y[i+1])
        current_dist = minDistance(pairstart, pairend, A)
        // If it is the minimum update dist and closest_seg_start
        if (current_dist < dist) {
            dist = current_dist
            closest_seg_start = i
        }
    }
    // Do the last comp:
    // The line which joins the last point to the first point
    pair pairstart = new pair(poly_x[-1], poly_y[-1])
    pair pairend = new pair(poly_x[0], poly_y[0])
    current_dist = minDistance(pairstart, pairend, A)
    if (current_dist < dist) {
        dist = current_dist
        closest_seg_start = i
    }
    return closest_seg_start
}


def processDataset(Client user_client, DatasetWrapper dataset_wpr,
                   File output_directory, Integer quantif_ch,
                   Integer n_pieces,
                   Boolean headless_mode, String tool_version) {
    robustlyGetAll(dataset_wpr, "image", user_client).each{ ImageWrapper img_wpr ->
        processImage(user_client, img_wpr,
                     output_directory, quantif_ch,
                     n_pieces,
                     headless_mode, tool_version)
    }
}

def processSinglePlate(Client user_client, PlateWrapper plate_wpr,
                       File output_directory, Integer quantif_ch,
                       Integer n_pieces,
                       Boolean headless_mode, String tool_version) {
    robustlyGetAll(plate_wpr, "well", user_client).each{ well_wpr ->
        processSingleWell(user_client, well_wpr,
                          output_directory, quantif_ch,
                          n_pieces,
                          headless_mode, tool_version)
    }
}

def processSingleWell(Client user_client, WellWrapper well_wpr,
                      File output_directory, Integer quantif_ch,
                      Integer n_pieces,
                      Boolean headless_mode, String tool_version) {
    well_wpr.getWellSamples().each{
        processImage(user_client, it.getImage(),
                     output_directory, quantif_ch,
                     n_pieces,
                     headless_mode, tool_version)
    }
}

def processImage(Client user_client, ImageWrapper image_wpr,
                 File output_directory, Integer quantif_ch,
                 Integer n_pieces,
                 Boolean headless_mode, String tool_version) {

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
    String image_basename = image_wpr.getName()
    println ("Image_name : " + image_basename + " / id : " + image_wpr.getId())
    List<DatasetWrapper> dataset_wpr_list = robustlyGetAll(image_wpr, "dataset", user_client)

    // if the image is part of a dataset
    if(!dataset_wpr_list.isEmpty()){
        dataset_wpr_list.each{
            println("dataset_name : "+it.getName()+" / id : "+it.getId())
        }
        robustlyGetAll(image_wpr, "project", user_client).each{
            println("Project_name : "+it.getName()+" / id : "+it.getId())
        }
    }

    // if the image is part of a plate
    else {
        robustlyGetAll(image_wpr, "well", user_client).each{
            println ("Well_name : "+it.getName() +" / id : "+ it.getId())
        }
        robustlyGetAll(image_wpr, "plate", user_client).each{
            println ("plate_name : "+it.getName() + " / id : "+ it.getId())
        }
        robustlyGetAll(image_wpr, "screen", user_client).each{
            println ("screen_name : "+it.getName() + " / id : "+ it.getId())
        }
    }
    // Get Gastruloids and Spines from omero:
    println "Get ROIs from OMERO"
    List<ROIWrapper> omeroRW = robustlyGetROIs(image_wpr, user_client)
    List<Roi> omeroRois = ROIWrapper.toImageJ(omeroRW, "ROI")
    println "Found " + omeroRois.size()

    if (omeroRois.size() == 0) {
        return
    }

    println "Get tables from OMERO"
    // get the list of image tables
    // remove the one with table_name
    robustlyGetTables(image_wpr, user_client).each{ TableWrapper t_wpr ->
        println "Found table: " + t_wpr.getName()
        if (t_wpr.getName() == table_name){
            robustlyDeleteTableWrapper(user_client, t_wpr)
        }
    }
    // Define an omero table
    TableWrapper table_wpr
    // Scan ROIs and
    // Put them in HashMap
    Map<String, Roi> gastruloid_rois = new HashMap<>();
    Map<String, Roi> spine_rois = new HashMap<>();
    Map<String, Boolean> spine_rois_on_fluo = new HashMap<>();
    for (roi in omeroRois) {
        current_t = roi.getTPosition()
        roi_name = roi.getName()
        // println "Found ROI: " + roi_name

        if (roi_name.toLowerCase().startsWith("gastruloid") && !roi_name.toLowerCase().startsWith("gastruloidnotfound")) {
        	// println "This is a gastruloid of time " + current_t
            gastruloid_rois.put(current_t, roi)
        }
        if (roi_name.toLowerCase().startsWith("spine")) {
        	// println "This is a spine of time " + current_t
            if (roi.getCPosition() != quantif_ch) {
                spine_rois.put(current_t, roi)
            } else {
                spine_rois_on_fluo.put(current_t, true)
            }
        }
    }
    
    if ( gastruloid_rois.size() == 0) {
    	println "No gastruloid found"
    	return
    }

    // Get the image only if there are gastruloids

    ImagePlus imp = robustlytoImagePlus(image_wpr, user_client)

    if (!headless_mode) {
        imp.show()
    }

    // get imp info
    dim_array = imp.getDimensions();
    nT = dim_array[4]


    // Remove all existing ROIs for the image relative to fluo measurements:
    rois_to_delete = []

    for (int t = 1; t <= nT; t++ ){
        // Remove existing ROIs
        // println "Time " + t
        omeroRW.each{
            String roi_name = it.toImageJ().get(0).getName()
            if ((roi_name == "WholeGastruloid_t" + t) ||
                (roi_name == "Full_t" + t) ||
                (roi_name == "NonGastruloid_t" + t) ||
                (roi_name.startsWith("Segment_") &&
                    roi_name.endsWith("_t" + t))) {
                    // println "Add " + roi_name + " to ROIs to remove."
                    rois_to_delete.add(it)
            }
        }
    }
    println "Remove ROIs from previous experiments on OMERO"
    // Remove all of them at once to reduce the number of servantsPerSession:
    robustlyDeleteROIs(image_wpr, user_client, rois_to_delete)

    /**
     * Measure on ROI along spine
     * Make a table with the values for each t
     * Add Overlay
     */

    IJ.run("Set Measurements...", "area mean min display redirect=None decimal=3");
    // to_omero_ov contains all ROIs that
    // are not quantified but will be shiped to OMERO
    Overlay to_omero_ov = new Overlay()
    // Get Date
    Date date = new Date()
    String now = date.format("yyyy-MM-dd_HH-mm")

    for (int t = 1; t <= nT; t++ ){
    	println "Analysis on t: " + t
        // We need to create one overlay per frame
        // In order to measure it
        Overlay ov = new Overlay()
        gastruloid_roi = gastruloid_rois.get(t)
        if ( gastruloid_roi != null) {
        	println "Gastruloid found"
            // There is a gastruloid
            gastruloid_roi.setName("WholeGastruloid_t" + t)
            gastruloid_roi.setPosition( quantif_ch, 1, t)
            ov.add(gastruloid_roi)
            full = new Roi(0, 0, imp.getWidth(), imp.getHeight())
            full.setName("Full_t" + t)
            full.setPosition( quantif_ch, 1, t)
            ov.add(full)
            non_gastruloid = new ShapeRoi(full).not(new ShapeRoi(gastruloid_roi))
            non_gastruloid.setName("NonGastruloid_t" + t)
            non_gastruloid.setPosition( quantif_ch, 1, t)
            ov.add(non_gastruloid)

            println "Current t = " + t
            spine_roi = spine_rois.get(t)
            println "spine is " + spine_roi
            if (spine_roi != null) {
                // Extract coordinates of the spine
                spine_xs = spine_roi.getFloatPolygon().xpoints
                spine_ys = spine_roi.getFloatPolygon().ypoints

                // A is first point
                pair A = new pair(spine_xs[0], spine_ys[0])
                // P is last point
                pair P = new pair(spine_xs[-1], spine_ys[-1])

                // Extract coordinates of the ROI
                gastruloid_x = gastruloid_roi.getFloatPolygon().xpoints
                gastruloid_y = gastruloid_roi.getFloatPolygon().ypoints

                // Find where integrate A and P in the ROI
                // println "Find ap on gastruloid"
                closest_seg_start_A = find_index(gastruloid_x, gastruloid_y, A)
                closest_seg_start_P = find_index(gastruloid_x, gastruloid_y, P)

                // Create 2 polylines going from A to P using both sides of polygon
                println "Split gastruloid ROI into 2 lines"
                // First going increasing:
                poly1_x = [A.F]
                poly1_y = [A.S]
                if (closest_seg_start_A + 1 < closest_seg_start_P) {
                    // Just goes from one to the other
                    poly1_x += Arrays.copyOfRange(gastruloid_x as int[], closest_seg_start_A + 1, closest_seg_start_P + 1) as List
                    poly1_y += Arrays.copyOfRange(gastruloid_y as int[], closest_seg_start_A + 1, closest_seg_start_P + 1) as List
                } else {
                    // Goes from one to the end and from the start to the second one
                    poly1_x += Arrays.copyOfRange(gastruloid_x as int[], closest_seg_start_A + 1, gastruloid_x.size()) as List
                    poly1_x += Arrays.copyOfRange(gastruloid_x as int[], 0, closest_seg_start_P + 1) as List
                    poly1_y += Arrays.copyOfRange(gastruloid_y as int[], closest_seg_start_A + 1, gastruloid_x.size()) as List
                    poly1_y += Arrays.copyOfRange(gastruloid_y as int[], 0, closest_seg_start_P + 1) as List
                }
                poly1_x.add(P.F)
                poly1_y.add(P.S)
                poly1 = new PolygonRoi(poly1_x as int[], poly1_y as int[], poly1_x.size(), Roi.POLYLINE)
                // Second going decreasing:
                // Because it is easier we build it P to A
                // And then revert it:
                // Building
                poly2_x = [P.F]
                poly2_y = [P.S]
                if (closest_seg_start_P + 1 <= closest_seg_start_A) {
                    // Just goes from one to the other
                    poly2_x += Arrays.copyOfRange(gastruloid_x as int[], closest_seg_start_P + 1, closest_seg_start_A + 1) as List
                    poly2_y += Arrays.copyOfRange(gastruloid_y as int[], closest_seg_start_P + 1, closest_seg_start_A + 1) as List
                } else {
                    // Goes from one to the end and from the start to the second one
                    poly2_x += Arrays.copyOfRange(gastruloid_x as int[], closest_seg_start_P + 1, gastruloid_x.size()) as List
                    poly2_x += Arrays.copyOfRange(gastruloid_x as int[], 0, closest_seg_start_A + 1) as List
                    poly2_y += Arrays.copyOfRange(gastruloid_y as int[], closest_seg_start_P + 1, gastruloid_x.size()) as List
                    poly2_y += Arrays.copyOfRange(gastruloid_y as int[], 0, closest_seg_start_A + 1) as List
                }
                poly2_x.add(A.F)
                poly2_y.add(A.S)
                // Reverting
                poly2_x = poly2_x.reverse()
                poly2_y = poly2_y.reverse()
                poly2 = new PolygonRoi(poly2_x as int[], poly2_y as int[], poly2_x.size(), Roi.POLYLINE)

                // Now I cut into n_pieces pieces the 3 polylines:
                // println "CUT"
                cut_spine = getCooPoly(spine_roi, n_pieces)
                cut_poly1 = getCooPoly(poly1, n_pieces)
                cut_poly2 = getCooPoly(poly2, n_pieces)

                // For each piece we form the new ROI:
                for (i = 0 ; i < n_pieces; i++) {
                    // println "Reform " + i
                    roi_x = [cut_spine[0][i][0]] + cut_poly1[0][i]+ [cut_spine[0][i][-1]] + cut_poly2[0][i].reverse()
                    roi_y = [cut_spine[1][i][0]] + cut_poly1[1][i] + [cut_spine[1][i][-1]] + cut_poly2[1][i].reverse()
                    new_roi = new PolygonRoi(roi_x as int[], roi_y as int[], roi_x.size(), Roi.POLYGON)
                    // We restrict the piece to the part which
                    // overlaps the gastruloid
                    new_roi2 = new ShapeRoi(new_roi as Roi).and(new ShapeRoi(gastruloid_roi as Roi))
                    new_roi2.setPosition( quantif_ch, 1, t)
                    new_roi2.setName("Segment_" + i + "_t" + t)
                    ov.add(new_roi2)
                    if (!headless_mode) {
                        rm.addRoi(new_roi2)
                    }
                }
                // println "DONE"
                // finally add Rois to Overlay if not already present:
                if (spine_rois_on_fluo.get(t) == null) {
                    to_omero_ov = addText(to_omero_ov, spine_roi, "A", t, 0, A.F, A.S, quantif_ch)
                    to_omero_ov = addText(to_omero_ov, spine_roi, "P", t, -1, P.F, P.S, quantif_ch)
                    spine_roi.setName(spine_roi.getName() + "_on_fluo")
                    spine_roi.setPosition( quantif_ch, 1, t)
                    to_omero_ov.add(spine_roi)
                }
            }
            imp.setPosition(quantif_ch, 1, t)
            println "set overlay"
            imp.setOverlay(ov)
            // Measure on each item of the overlay
            def rt_profil_t = ov.measure(imp)
            // Debug
            // println "Columns are:" + rt_profil_t.getColumnHeadings()
            if (rt_profil_t.getColumnIndex("Group") != ResultsTable.COLUMN_NOT_FOUND) {
                println "Remove Group column"
                rt_profil_t.deleteColumn("Group")
            }

            // Add Date, version and params
            for ( int row = 0;row<rt_profil_t.size();row++) {
                rt_profil_t.setValue("Date", row, now)
                rt_profil_t.setValue("Version", row, tool_version)
                rt_profil_t.setValue("NPieces", row, n_pieces)
                rt_profil_t.setValue("Channel", row, quantif_ch)
                rt_profil_t.setValue("Time", row, t)
                String label = rt_profil_t.getLabel(row)
                rt_profil_t.setValue("BaseImage", row, label.split(":")[0])
                rt_profil_t.setValue("ROI", row, label.split(":")[1])
                rt_profil_t.setValue("ROI_type", row, label.split(":")[1].split("_t")[0])
            }

            // Debug
            // println "Now columns are:" + rt_profil_t.getColumnHeadings()

            println "Store " + ov.size() + " ROIs on OMERO"

            // Save ROIs to omero
            robustlysaveROIs(image_wpr, user_client, ROIWrapper.fromImageJ(ov as List))

            // Get them back with IDs:
            ArrayList<Roi> updatedRois = []
            // println "Now there is"
            ROIWrapper.toImageJ(robustlyGetROIs(image_wpr, user_client), "ROI").each{
            	// println it.getName()
                if ((it.getName() == "WholeGastruloid_t" + t) ||
                    (it.getName() == "Full_t" + t) ||
                    (it.getName() == "NonGastruloid_t" + t) ||
                    (it.getName().startsWith("Segment_") &&
                     it.getName().endsWith("_t" + t))) {
                        updatedRois.add(it)
                }
            }
            println "Writting measurements to file"
            rt_profil_t.save(output_directory.toString() + '/' + image_basename+"__"+t+"_profil_Results.csv" )
            if (table_wpr == null) {
                table_wpr = robustlyNewTableWrapper(user_client, rt_profil_t, image_wpr.getId(), updatedRois, "ROI")
                // add the same infos to the super_table
                if (super_table == null) {
                    println "super_table is null"
                    super_table = robustlyNewTableWrapper(user_client, rt_profil_t, image_wpr.getId(), updatedRois, "ROI")
                } else {
                    println "adding rows"
                    robustlyAddRows(super_table, user_client, rt_profil_t, image_wpr.getId(), updatedRois, "ROI")
                }
            } else {
                println "adding rows"
                robustlyAddRows(table_wpr, user_client, rt_profil_t, image_wpr.getId(), updatedRois, "ROI")
                robustlyAddRows(super_table, user_client, rt_profil_t, image_wpr.getId(), updatedRois, "ROI")
            }
        }
    }

    // upload the table on OMERO
    table_wpr.setName(table_name)
    robustlyAddAndReplaceTable(image_wpr, user_client, table_wpr)

    // Send non-quantified ROIs to OMERO
    println "in non-quantified"
    to_omero_ov.each{
    	println it.getName()
    }
    robustlysaveROIs(image_wpr, user_client, ROIWrapper.fromImageJ(to_omero_ov as List))

    // Put all ROIs in overlay:
    Overlay global_overlay = new Overlay()
    ROIWrapper.toImageJ(robustlyGetROIs(image_wpr, user_client), "ROI").each{
    	if (it.getTypeAsString() == "Text") {
    		it.setFont(new FontUtil().getFont("Arial", 1 , 72 as float ))
    	}
        global_overlay.add(it)
    }

    imp.setOverlay(global_overlay)

    // save file
    def fs = new FileSaver(imp)
    output_path = new File (output_directory ,image_basename+".tif" )
    fs.saveAsTiff(output_path.toString() )

    return
}

// Block written by Romain Guiet
/**
 * helper function to add A and P letter to the image
 */

def addText( ov, roi, txt, pos, idx, x, y, channel){
    if (idx == 0)    roi.defaultColor = new Color(255,128,0)
    else             roi.defaultColor = new Color(0,128,255)

    txtroi = new TextRoi( x , y , 50.0 , 50.0 ,txt ,  new FontUtil().getFont("Arial", 1 , 72 as float ) )
    txtroi.setPosition(channel, 1, pos as int)
    txtroi.setName(txt + "_t" + pos)
    ov.add( txtroi  )

    return ov
}
// End of block


// In simple-omero-client
// Strings that can be converted to double are stored in double
// In order to build the super_table, tool_version should stay String
String tool_version = "Fluo_v20230724"

// User set variables

#@ String(visibility=MESSAGE, value="Inputs", required=false) msg
#@ String(label="User name") USERNAME
#@ String(label="PASSWORD", style='PASSWORD', value="", persist=false) PASSWORD
#@ String(label="File path with omero credentials") credentials
#@ String(label="omero host server") host
#@ Integer(label="omero host server port", value=4064) port
#@ String(label="Object", choices={"image","dataset","well","plate"}) object_type
#@ Long(label="ID", value=119273) id

#@ String(visibility=MESSAGE, value="Parameters for quantification", required=false) msg2
#@ Integer(label="Number of pieces along the spine", value="10") n_pieces
#@ Integer(label="Index of the channel to quantify (1-based)", value="1") quantif_ch

#@ String(visibility=MESSAGE, value="Parameters for output", required=false) msg4
#@ File(style = "directory", label="Directory where measures are put") output_directory

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
table_name = "Measurements_from_Galaxy_fluo"
if (user_client.isConnected()) {
    println "\nConnected to "+host
    println "Results will be in " + output_directory.toString()

    try {
        switch (object_type) {
            case "image":
                ImageWrapper image_wr
                try {
                    image_wpr = user_client.getImage(id)
                } catch(Exception e) {
                    throw Exception("Could not retrieve the image, please check the id.")
                }
                processImage(user_client, image_wpr,
                             output_directory, quantif_ch,
                             n_pieces,
                             headless_mode, tool_version)
                break
            case "dataset":
                DatasetWrapper dataset_wpr
                try {
                    dataset_wpr = user_client.getDataset(id)
                } catch(Exception e) {
                    throw Exception("Could not retrieve the dataset, please check the id.")
                }
                processDataset(user_client, dataset_wpr,
                               output_directory, quantif_ch,
                               n_pieces,
                               headless_mode, tool_version)
                // upload the table on OMERO
                // and replace existing one
                super_table.setName(table_name + "_global")
                robustlyAddAndReplaceTable(dataset_wpr, user_client, super_table)
                break
            case "well":
                WellWrapper well_wpr
                try {
                    well_wpr = user_client.getWells(id)[0]
                } catch(Exception e) {
                    throw Exception("Could not retrieve the well, please check the id.")
                }
                processSingleWell(user_client, well_wpr,
                                  output_directory, quantif_ch,
                                  n_pieces,
                                  headless_mode, tool_version)
                // upload the table on OMERO
                // and replace existing one
                super_table.setName(table_name + "_global")
                robustlyAddAndReplaceTable(well_wpr, user_client, super_table)
                break
            case "plate":
                PlateWrapper plate_wpr
                try {
                    plate_wpr = user_client.getPlates(id)[0]
                } catch(Exception e) {
                    throw Exception("Could not retrieve the plate, please check the id.")
                }
                processSinglePlate(user_client, plate_wpr,
                                   output_directory, quantif_ch,
                                   n_pieces,
                                   headless_mode, tool_version)
                // upload the table on OMERO
                // and replace existing one
                super_table.setName(table_name + "_global")
                robustlyAddAndReplaceTable(plate_wpr, user_client, super_table)
                break
        }
    } catch(Exception e) {
        println("Something went wrong: " + e)
        e.printStackTrace()
        throw new Exception(e)
    } finally {
        user_client.disconnect()
        println "Disonnected " + host
    }
} else {
    throw new Exception("Not able to connect to " + host)
}

return
