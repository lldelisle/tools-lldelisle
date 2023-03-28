import emblcmci.BleachCorrection_ExpoFit

import ij.IJ
import ij.ImagePlus
import ij.ImageStack
import ij.gui.Overlay
import ij.gui.TextRoi
import ij.measure.Calibration
import ij.plugin.Concatenator
import ij.plugin.Duplicator
import ij.plugin.HyperStackConverter
import ij.plugin.filter.AVI_Writer
import ij.plugin.ScaleBar
import ij.process.ImageProcessor
import ij.process.ImageStatistics
import ij.util.FontUtil

import java.awt.*
import java.awt.GraphicsEnvironment
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.regex.Matcher
import java.util.regex.Pattern

/** pattern for date */
REGEX_FOR_DATE = ".*_([0-9]{4})y([0-9]{2})m([0-9]{2})d_([0-9]{2})h([0-9]{2})m"

ALTERNATIVE_REGEX_FOR_DATE = ".*_([0-9]{2})d([0-9]{2})h([0-9]{2})m"

Pattern date_pattern = Pattern.compile(REGEX_FOR_DATE)

Map<String, String> channel_name_to_keyword = new HashMap<>()
channel_name_to_keyword.put("Grays", "white")
channel_name_to_keyword.put("Green", "green")
channel_name_to_keyword.put("Red", "red")

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
 * Returns an ImagePlus
 */
def bleachCorr(ImagePlus imp, String mediaChangeTime, Boolean normalizeToFirstChunk){
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
	double ref_value = 0.0
	(0..time_array.size()-2).each{
		//println time_array[it]+1 +","+time_array[it+1]
		// Create an ImagePlus with the "block"
		ImagePlus corr_imp = new Duplicator().run(imp, 1, 1, 1 , 1 , time_array[it]+1 , time_array[it+1] )
		if (time_array[it]+1 != time_array[it+1]) {
			// If more than one frame, compute Bleach correction
			def BCE = new BleachCorrection_ExpoFit(corr_imp) 
			BCE.core()
		}
		if (normalizeToFirstChunk) {
			// Before adding to the ArrayList, it will be normalized to the median
			// values of the first chunk
			if (it == 0) {
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

// Returns a date from a label and a date_pattern
def getDate(String label, Pattern date_pattern){
	Matcher date_m = date_pattern.matcher(label)
	LocalDateTime dateTime
	if (date_m.matches()) {
		if (date_m.groupCount() == 5) {
			dateTime = LocalDateTime.parse(date_m.group(1) + "-" + date_m.group(2) + "-" + date_m.group(3) + "T" + date_m.group(4) + ":" + date_m.group(5))
		} else {
			dateTime = LocalDateTime.parse("1970-01-" + 1 + (date_m.group(1) as int) + "T" + date_m.group(2) + ":" + date_m.group(3))
		}
	}
	return dateTime
}

// Returns a string with the time using the original name formatted (H)HH:MM
def getStringFromImp(ImagePlus imp, ImageStack stack, LocalDateTime dateTime_ref, int start_hour, Pattern date_pattern){
	int currentSlice = imp.getCurrentSlice()
	String label = stack.getShortSliceLabel(currentSlice)
	LocalDateTime dateTime = getDate(label, date_pattern)
	if (dateTime != null) {
		int diff = ChronoUnit.MINUTES.between(dateTime_ref, dateTime) as int
		int minutes_time = start_hour * 60 + diff
		int hours = minutes_time / 60; //since both are ints, you get an int
		int minutes = minutes_time % 60;
		String my_string = hours.toString().padLeft(2, '0') + ":" + minutes.toString().padLeft(2, '0')
		return my_string
	} else {
		return ""
	}
}

#@ File(label="Input image to convert") image_filename
#@ Integer(label="Time of the first time point (in hour)") starting_time_hour
#@ Integer(label="x-position of the time", value=5) x
#@ Integer(label="y-position of the time", value=105) y
#@ Integer(label="Number of frames per second in avi", value=2) frame_per_second
#@ String(label="Channel to be bleach corrected", choices={"None", "Green", "Red", "Grays"}) bleach_cor_channel
#@ String(label="Indices after which the media was changed separated by comma (1-based)", value="") media_change_points
#@ Boolean(label="Normalize each chunk to the first chunk", value="false") normalize_to_first_chunk
#@ String(label="Channels to include in the movie separated by comma among Grays,Green,Red") channels_in_film_comma
#@ Boolean(label="Display scalebar", value="false") display_scalebar
#@ Double(label="Scale (size of 1 pixel in um)", value=1.24) scale
#@ Double(label="minimum display value for Grays (-1 for auto)", value=-1) min_grey
#@ Double(label="maximum display value for Grays (-1 for auto)", value=-1) max_grey
#@ Double(label="minimum display value for Green (-1 for auto)", value=-1) min_green
#@ Double(label="maximum display value for Green (-1 for auto)", value=-1) max_green
#@ Double(label="minimum display value for Red (-1 for auto)", value=-1) min_red
#@ Double(label="maximum display value for Red (-1 for auto)", value=-1) max_red

IJ.run("Close All", )

// Convert to list
String[] channels_in_film = channels_in_film_comma.split(',')
// Open the image
ImagePlus imp = IJ.openImage( image_filename.getAbsolutePath() )

if (! GraphicsEnvironment.isHeadless()){
	imp.show()
}
// get imp info
int[] dim_array = imp.getDimensions()
int nC = dim_array[2]
int nT = dim_array[4]

// Store the imp of each channel in array:
ArrayList<ImagePlus> images = []

// Store min_max in hashmap
Map<String, ArrayList<Double>> min_max = new HashMap<>()
min_max.put("Grays", Arrays.asList(min_grey, max_grey))
min_max.put("Green", Arrays.asList(min_green,max_green))
min_max.put("Red", Arrays.asList(min_red,max_red))

// Loop over channel color provided by the user
for (channel_color in channels_in_film) {
	found_channel = false
	// Loop over the available channels to find the good one
	for (int i = 1; i <= nC; i ++) {
	    imp.setC(i)
	    ImageProcessor ip = imp.getChannelProcessor()
	    // ip.getLut().toString() gives
	    // rgb[0]=black, rgb[255]=white, min=3.0000, max=255.0000
	    // rgb[0]=black, rgb[255]=green, min=1950.0000, max=2835.0000
	    if (ip.getLut().toString().contains(channel_name_to_keyword.get(channel_color))) {
	    	found_channel = true
	        ImagePlus imp_temp = new Duplicator().run(imp, i, i, 1, 1, 1, nT)
	        if (bleach_cor_channel == channel_color) {
	        	ImagePlus corrected_imp = bleachCorr(imp_temp, media_change_points, normalize_to_first_chunk)
	        	images.add(corrected_imp)
	        } else {
	        	images.add(imp_temp)
	        }
	    }
	}
	assert found_channel : "The channel "+channel_color+ " was not found in images"
}
// Merge all
ImagePlus merged_imps = Concatenator.run(images as ImagePlus[]);
// Re-order to make a multi-channel, time-lapse image
ImagePlus final_imp = HyperStackConverter.toHyperStack(merged_imps, images.size() , 1, nT, "xytcz", "Composite");
// Put back the good color and threshold
for (int j = 0; j < channels_in_film.size(); j ++) {
	final_imp.setC(j + 1)
	IJ.run(final_imp, channels_in_film[j], "")
	// Set the display range
	// First put auto
	final_imp.resetDisplayRange()
	// Replace auto by provided by user:
	min_max_values = min_max.get(channels_in_film[j])
	if (min_max_values[0] == -1) {
		min_value = final_imp.getDisplayRangeMin()
	} else {
		min_value = min_max_values[0]
	}
	if (min_max_values[1] == -1) {
		max_value = final_imp.getDisplayRangeMax()
	} else {
		max_value = min_max_values[1]
	}
	println "Setting minmax for channel " + channels_in_film[j] + " to " + min_value + "," + max_value
	final_imp.setDisplayRange(min_value, max_value)
}
// Go back to the first time point
final_imp.setT(1)
// Go back to the first channel
final_imp.setC(1)
Overlay ov = new Overlay()
// Attach it to image before adding ROIs
// If not they will not appear in movie
final_imp.setOverlay(ov)
// In future version it would be good to get the overlay of imp:

//Overlay ov = imp.getOverlay()
// but the channel of each roi needs to be adapted

// Get the reference time:
ImageStack stack = final_imp.getStack();
int currentSlice = final_imp.getCurrentSlice();
String label = stack.getShortSliceLabel(currentSlice);
LocalDateTime dateTime_ref = getDate(label, date_pattern)
if (dateTime_ref == null) {
	date_pattern = Pattern.compile(ALTERNATIVE_REGEX_FOR_DATE)
	dateTime_ref = getDate(label, date_pattern)
}
if (dateTime_ref != null) {
	for (int i = 1; i<= nT; i++) {
		// Process each frame
		final_imp.setT(i)
		label = getStringFromImp(final_imp, stack, dateTime_ref, starting_time_hour, date_pattern)
		if (final_imp.nChannels > 1) {
			// Use overlay:
			txtroi = new TextRoi(x, y,  label,  new Font("SansSerif", Font.PLAIN, 100) )
		    txtroi.setPosition(1, 1, i)
		    ov.add( txtroi  )
		} else {
			// Use image processor:
			final_imp.setT(i)
			ImageProcessor ip = final_imp.getProcessor()
			ip.setFont(new Font("SansSerif", Font.PLAIN, 100))
			ip.setAntialiasedText(true)
	        ip.moveTo(x, y)
	        ip.drawString(label)
		}
	}
}
// Add the scalebar
if (display_scalebar) {
	IJ.run(final_imp, "Set Scale...", "distance=" + scale + " known=1 unit=um global")
	// This does not work in headless:
	// IJ.run(final_imp, "Scale Bar...", "width=100 height=100 thickness=10 font=50 color=White background=None location=[Lower Right] horizontal bold serif label")
	ScaleBar sb = new ScaleBar()
	sb.imp = final_imp
	def config = new ScaleBar.ScaleBarConfiguration()
	sb.updateScalebar(true)
	// Set the config
	config.hBarWidth = 100 // default -1
	config.vBarHeight = 100 // default -1
	config.barThicknessInPixels = 10 // default 4
	config.fontSize = 50 // default 14
	config.serifFont = true // default false
	config.useOverlay = false // default true
	config.labelAll = true // default false
	// This is already the default but in case:
	config.color = "White"
	config.bcolor = "None"
	config.location = "Lower Right"
	config.showHorizontal = true
	config.showVertical = false
	config.boldText = true
	config.hideText = false
	sb.config = config
	sb.updateScalebar(!config.labelAll)
}
if (! GraphicsEnvironment.isHeadless()){
	final_imp.show()
}
AVI_Writer avi_writer = new AVI_Writer()
Calibration cal = final_imp.getCalibration()
cal.fps = frame_per_second
final_imp.setCalibration(cal)
avi_writer.writeImage(final_imp, imp.getTitle() + ".avi", AVI_Writer.JPEG_COMPRESSION, 2)
