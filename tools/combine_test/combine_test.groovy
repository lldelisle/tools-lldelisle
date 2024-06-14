import emblcmci.BleachCorrection_ExpoFit

import fr.igred.omero.Client
import fr.igred.omero.roi.ROIWrapper
import fr.igred.omero.repository.ImageWrapper

import ij.CompositeImage
import ij.ImageStack
import ij.ImagePlus
import ij.IJ
import ij.plugin.Binner
import ij.plugin.Concatenator
import ij.plugin.Duplicator
import ij.plugin.HyperStackConverter
import ij.process.ImageProcessor
import ij.process.ImageStatistics
import ij.gui.Roi
import ij.gui.Overlay
import ij.gui.PolygonRoi
import ij.io.FileSaver

import java.awt.GraphicsEnvironment
import java.awt.Color
import java.io.File

import org.apache.commons.io.FileUtils

channel_name_to_keyword = new HashMap<>()
channel_name_to_keyword.put("Grays", "white")
channel_name_to_keyword.put("Green", "green")
channel_name_to_keyword.put("Red", "red")

def combine_multiple_imps(
	ArrayList<ImagePlus> imps, int n_cols, int n_rows,
	Boolean headless_mode
) {
	println "Combine all imps"
	// Check they all have the same dimensions
	int first_bitdepths = imps[0].getBitDepth()
	int[] dim_array = imps[0].getDimensions()
	Boolean is_hyperstack = imps[0].isHyperStack()
	imps.each{
		if (it.getBitDepth() != first_bitdepths) {
			throw new Exception (it.getTitle() + "does not have the same bitdepths as the first image")
		}
		if (is_hyperstack && it.getDimensions() != dim_array) {
			throw new Exception (it.getTitle() + "does not have the same dimensions as the first image")
		}
	}
	
	// Get all stacks
	ArrayList<ImageStack> stacks = imps.collect{it.getStack()}
	// Create a new stack
	ImageStack final_stack = new ImageStack(
		dim_array[0] * n_cols,
		dim_array[1] * n_rows,
		stacks[0].getColorModel()
	)
	// Fill the new stack and empty the stacks
	ImageProcessor ip = stacks[0].getProcessor(1)
	int d = stacks[0].getSize()
	for (int i=1; i<=d; i++) {
		ImageProcessor final_ip = ip.createProcessor(
			dim_array[0] * n_cols,
			dim_array[1] * n_rows
		)
		int current_row = 0
		int current_col = 0
		for (int k=0; k<stacks.size(); k++) {
			final_ip.insert(
				stacks[k].getProcessor(1),
				dim_array[0] * current_col,
				dim_array[1] * current_row)
			stacks[k].deleteSlice(1)
			current_col += 1
			if (current_col == n_cols) {
				current_col = 0
				current_row += 1
			}
		}
		final_stack.addSlice(null, final_ip)
	}
	ImagePlus final_imp = imps[0].createImagePlus()
	final_imp.setStack(final_stack)
	if (is_hyperstack)
		final_imp.setDimensions(dim_array[2],dim_array[3],dim_array[4])
	if (imps[0].isComposite()) {
		final_imp = new CompositeImage(final_imp, imps[0].getCompositeMode())
		final_imp.setDimensions(dim_array[2],dim_array[3],dim_array[4])
	}
	final_imp.setTitle("Combined Stacks")
	if (!headless_mode) {
		final_imp.show()
		imps.each{
			it.changes = false
			it.close()
		}
	}
	return final_imp
}

def add_ROIs_from_omero_to_combined(
	ImagePlus final_imp, int n_cols, int n_rows,
	ArrayList<ImageWrapper> image_wrps,
	int single_width, int single_height,
	String starts_with, Client user_client,
	Integer shrink
) {
	println "Add ROIs from OMERO"
	Overlay ov = final_imp.getOverlay()
	if (ov == null) {
		ov = new Overlay()
		// Attach it to image before adding ROIs
		final_imp.setOverlay(ov)
	}
	int current_row = 0
	int current_col = 0
	for (int i=0;i<image_wrps.size();i++) {
		// Gather all ROIs into an overlay
		// to be able to translate them
		Overlay temp_overlay = new Overlay()
		ArrayList<ROIWrapper> omeroRW = image_wrps[i].getROIs(user_client)
		if (omeroRW.size() > 0) {
			ArrayList<Roi> omeroRois = ROIWrapper.toImageJ(omeroRW, "ROI")
			omeroRois.each {
				if(it.getName().toLowerCase().startsWith(starts_with)) {
					if (shrink != 1) {
						// I assume it is a polygon
						x = it.getFloatPolygon().xpoints
						y = it.getFloatPolygon().ypoints
						new_x = x.collect{
							(it as float) / shrink
						}
						new_y = y.collect{
							(it as float) / shrink
						}
						PolygonRoi newRoi = new PolygonRoi(
							new_x as float[],
							new_y as float[],
							new_x.size(),
							Roi.POLYGON
						)
						newRoi.setPosition( it.getCPosition(), 1, it.getTPosition())
						newRoi.setName(it.getName())
						temp_overlay.add(newRoi)
					} else {
						temp_overlay.add(it)
					}
				}
			}
		}
		temp_overlay.translate(
			single_width * current_col as double,
			single_height * current_row as double
		)
		// Add it to the overlay on the final_imp
		temp_overlay.each {
			ov.add(it)
		}
		current_col += 1
		if (current_col == n_cols) {
			current_col = 0
			current_row += 1
		}
	}
	return ov
}

// Compute median
def median(numArray) {

	Arrays.sort(numArray)
	double median
	if (numArray.size() % 2 == 0) {
		median = ((double)numArray[numArray.size() / 2] + (double)numArray[numArray.size() / 2 - 1]) / 2
	} else {
		median = (double) numArray[numArray.size() / 2]
	}
	return median
}

// This is from Romain Guiet, BIOP, EPFL
// Amended by Lucille
/*
 * - imp : a time-lapse image (imp.getNFrames() > 1 )
 * - mediaChangeTime : the time-point(s) afer which the media was changed 
 * Please seperate values using commas "," eg "7,15" , in that example it means that the media was changed after time-point 7 and  15 a  fit should be computed from 1-7 , 8-15, 16-end) 
 * - normalizeChunks : Boolean whether all chunks should be normalized (using median)
 * - ref_value : the reference value to target, if null will be the median of the first chunk
 * Returns an ImagePlus
 */
def bleachCorrSingleChannel(
	ImagePlus imp,
	String mediaChangeTime,
	Boolean normalizeChunks,
	Double ref_value=null
){
	// from user input prepare array with time-points when media was change
	ArrayList<int> time_array = [0] // initialize at 0
	if (!mediaChangeTime.equals("")) {
		mediaChangeTime.split(",").each{
			time_array.add(it as int)
		}
	}
	time_array.add( imp.getNFrames() )// finalize with last time-point
	println time_array
	// check the time_array is sorted:
	(0..time_array.size()-2).each{
		if (time_array[it+1] <= time_array[it]) throw new Exception("Invalid mediaChangeTime: should be sorted without final index.")
	}
	// process the time-lapse, eventually by "block" defined above
	ArrayList<ImagePlus> corr_imps = []
	(0..time_array.size()-2).each{
		//println time_array[it]+1 +","+time_array[it+1]
		// Create an ImagePlus with the "block"
		ImagePlus corr_imp = new Duplicator().run(imp, 1, 1, 1 , 1 , time_array[it]+1 , time_array[it+1] )
		if (time_array[it]+1 != time_array[it+1]) {
			// If more than one frame, compute Bleach correction
			def BCE = new BleachCorrection_ExpoFit(corr_imp) 
			BCE.core()
		}
		if (normalizeChunks) {
			// Before adding to the ArrayList, it will be normalized to the median
			// values of the first chunk
			if (it == 0 && ref_value == null) {
				// Get the median
				ArrayList<Float> all_values = []
				for (int i = 0; i < corr_imp.getStackSize(); i++) {
					ImageProcessor curip = corr_imp.getImageStack().getProcessor(i + 1)
					ImageStatistics imgstat = curip.getStatistics()
					all_values.add(imgstat.mean)
				}
				ref_value = median(all_values)
				// println ref_value
			} else {
				// First get the median
				all_values = []
				for (int i = 0; i < corr_imp.getStackSize(); i++) {
					curip = corr_imp.getImageStack().getProcessor(i + 1)
					imgstat = curip.getStatistics()
					all_values.add(imgstat.mean)
				}
				cur_value = median(all_values)
				// println "New value is" + cur_value
				double ratio = ref_value / cur_value
				println ratio
				// Apply the correction
				for (int i = 0; i < corr_imp.getStackSize(); i++) {
					curip = corr_imp.getImageStack().getProcessor(i + 1)
					curip.multiply(ratio)
				}
			}
		}
		// Add the corrected normalized images to the list
		corr_imps.add(corr_imp)
	}
	// Concatenate:
	ImagePlus merged_corr_imp = Concatenator.run(corr_imps as ImagePlus[]);

	return merged_corr_imp
}

def bleachCorrImp(
	ImagePlus imp,
	String bleach_cor_channel,
	String mediaChangeTime,
	Boolean normalizeChunks,
	Double ref_value=null
) {
	println "Compute bleach correction"
	// get imp info
	int[] dim_array = imp.getDimensions()
	int nC = dim_array[2]
	int nT = dim_array[4]
	// Loop over the available channels to find the good one
	// Store the imp of each channel in array:
	ArrayList<ImagePlus> images = []
	for (int i = 1; i <= nC; i ++) {
	    imp.setC(i)
	    ImageProcessor ip = imp.getChannelProcessor()
	    ImagePlus imp_temp = new Duplicator().run(imp, i, i, 1, 1, 1, nT)
	    // ip.getLut().toString() gives
	    // rgb[0]=black, rgb[255]=white, min=3.0000, max=255.0000
	    // rgb[0]=black, rgb[255]=green, min=1950.0000, max=2835.0000
	    if (ip.getLut().toString().contains(channel_name_to_keyword.get(bleach_cor_channel))) {
			ImagePlus corrected_imp = bleachCorrSingleChannel(imp_temp, mediaChangeTime, normalizeChunks, ref_value)
			images.add(corrected_imp)
		} else {
			images.add(imp_temp)
		}
	}
	// Merge all
	ImagePlus merged_imps = Concatenator.run(images as ImagePlus[]);
	// Re-order to make a multi-channel, time-lapse image
	ImagePlus final_imp = HyperStackConverter.toHyperStack(merged_imps, nC, 1, nT, "xytcz", "Composite");
	// The colors are not the good ones but we don't change them
	// Go back to the first time point
	final_imp.setT(1)
	// Go back to the first channel
	final_imp.setC(1)
	return final_imp
}

// User set variables

#@ String(visibility=MESSAGE, value="Inputs", required=false) msg
#@ String(label="User name") USERNAME
#@ String(label="PASSWORD", style='PASSWORD', value="", persist=false) PASSWORD
#@ String(label="File path with omero credentials") credentials
#@ String(label="omero host server") host
#@ Integer(label="omero host server port", value=4064) port
#@ String(label="comma separated image IDs", value="") image_ids_comma
#@ Integer(label="Number of columns", value=1) n_cols
#@ Integer(label="Number of rows", value=1) n_rows

#@ String(visibility=MESSAGE, value="Options", required=false) msg
#@ Integer(label="Number of pixels to bin", value="1") shrink
#@ String(label="Channel to be bleach corrected", choices={"None", "Green", "Red", "Grays"}) bleach_cor_channel
#@ String(label="Indices after which the media was changed separated by comma (1-based)", value="") media_change_points
#@ Double(label="Reference value for bleach corrected channel", value="500") ref_value
#@ Boolean(label="get gastruloid segmentation", value=false) get_gastru

#@ String(visibility=MESSAGE, value="Parameters for output", required=false) msg5
#@ File(style = "directory", label="Directory where measures are put") output_directory

if (image_ids_comma.split(",").size() > n_rows * n_cols) {
	throw new Exception("Too many images compared to number of rows and columns")
}

// Detect if is headless
// java.awt.GraphicsEnvironment.checkheadless_mode(GraphicsEnvironment.java:204)
Boolean headless_mode = GraphicsEnvironment.isHeadless()
if (headless_mode) {
	println "Running in headless mode"
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

if (user_client.isConnected()) {
	println "\nConnected to "+host
	println "Images will be in " + output_directory.toString()

    try {
		// Get all image_wrappers
		println "Get image wrappers"
		ArrayList<ImageWrapper> image_wrps = image_ids_comma.split(",").collect{
			user_client.getImage(it as int)
		}
		// Get all image plus
		println "Get images"
		ArrayList<ImagePlus> imps = image_wrps.collect{
			println it
			it.toImagePlus(user_client)
		}

		if (shrink != 1) {
			println "Shrinking"
			imps.each{
				// I don't manage to make it properly...
				IJ.run(it, "Bin...", "x="+shrink+" y="+shrink+" z=1 bin=Average");
			}
		}

		// if (!headless_mode) {
		// 	imps.each{it.show()}
		// }
		int[] dim_array = imps[0].getDimensions()
		if (bleach_cor_channel != "None") {
			ArrayList<ImagePlus> new_imps = []
			for (ImagePlus imp in imps) {
				new_imps.add(
					bleachCorrImp(imp, bleach_cor_channel, media_change_points, true, ref_value)
				)
			}
			imps = new_imps
		}

		ImagePlus final_imp = combine_multiple_imps(imps, n_cols, n_rows, headless_mode)

		if (get_gastru) {	
			// Get Gastruloids from omero:
			Overlay ov = add_ROIs_from_omero_to_combined(
				final_imp, n_cols, n_rows,
				image_wrps,
				dim_array[0], dim_array[1],
				"gastruloid_", user_client,
				shrink
			)
			// Make the overlay more visible
			ov.setStrokeColor(java.awt.Color.blue)
			ov.setStrokeWidth(3)
		}
		println "Writting image"
		// Write to file
		File output_path = new File (output_directory, "combined.tiff" )
		// save file
		FileSaver fs = new FileSaver(final_imp)
		fs.saveAsTiff(output_path.toString())
		println "Done"
		return

    } catch(Exception e) {
        println("Something went wrong: " + e)
        e.printStackTrace()
    } finally {
        user_client.disconnect()
        println "Disonnected " + host
    }
} else {
    throw new Exception("Not able to connect to " + host)
}

return
