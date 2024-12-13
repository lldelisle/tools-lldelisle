/**
 *
 * The purpose of this script is to combine a series of time-lapse images into
 * one file per image with possibly multiple channels and multiple time points
 *
 * To make the script run
 *  1. Create a parent folder (base_dir) and a output folder (output_dir)
 *  2. The struction of the base_dir must be: one directory per final image and per channel. All the directories should be: `unique_identifier` `suffix specific to channel`.
 *  3. The image names will be sorted before being merged.
 *  4. The images must be regular tif.
 *
 * The expected outputs are:
 *  1. In the output_dir one tiff per `unique_identifier` (potentially multi-T and potentially multi-C)
 */

#@ File(style="directory", label="Directory with one directory per final image and per channel") base_dir
#@ File(style="directory", label="Output directory (must exist)") output_dir
#@ String(label="Suffix for white channel directory", value="_BF_max", help="Leave empty if you are not interested") suffix_white
#@ String(label="Suffix for fluo channel(s) directory", value="_Fluo_max", help="Leave empty if you are not interested") suffix_fluo
#@ String(label="Pattern for green channel images", value="_H2B-GFP", help="Leave empty if you are not interested") pattern_green
#@ String(label="Pattern for red channel images", value="_RFP670", help="Leave empty if you are not interested") pattern_red


/**
 * *****************************************************************************************************************
 * ********************************************* Final Variables **************************************************
 * ********************************************* DO NOT MODIFY ****************************************************
 * ****************************************************************************************************************
 */

// Version number = date of last modif
VERSION = "20241213.2"

/**
 * *****************************************************************************************************************
 * **************************************** Beginning of the script ***********************************************
 * ****************************************************************************************************************
 */

try {

	println "Beginning of the script"

	IJ.run("Close All", "")

	// Find all directories
	File[] dir_list = base_dir.listFiles()

	// The images are stored in a TreeMap where
	// keys are unique_identifier
	// values are a TreeMap that we call channelMap where:
	// keys are colors (Green, Grays, Red)
	// values are an ImagePlus (T-stack)
	Map<Integer, Map<String, ImagePlus>> samplesMap = new TreeMap<>()
	List<String> dir_suffix_list = [suffix_white, suffix_fluo]
	List<String> dir_channels_list = ["Grays", "Fluo"]

	List<String> fluo_pattern_list = [pattern_green, pattern_red]
	List<String> fluo_channels_list = ["Green", "Red"]

	// Loop over directories:
	for (File current_directory : dir_list) {
		// Ignore if it is not a directory
		if (! current_directory.isDirectory()) {
			continue
		}
		String current_directory_name = current_directory.getName()
		// Check if it matches one of the suffix
		String final_color = ""
		// And find the unique identifier:
		String unique_identifier = ""
		for(int i = 0; i < dir_suffix_list.size(); i++){
			if (dir_suffix_list[i] != "" && current_directory_name.endsWith(dir_suffix_list[i])) {
				final_color = dir_channels_list[i]
				unique_identifier = current_directory_name.replace(dir_suffix_list[i], "")
				continue
			}
		}
		if (final_color == "") {
			println current_directory_name + " do not match any suffix."
			continue
		}
		if (! samplesMap.containsKey(unique_identifier) ) {
			// Initiate the Map
			samplesMap.put(unique_identifier, new TreeMap<>())
		}
		// Generate the ImagePlus
		if (final_color == "Fluo") {
			for(int i = 0; i < fluo_pattern_list.size(); i++){
				// Use pattern for each color
				if (fluo_pattern_list[i] != "") {
					println "Processing " + unique_identifier + " " + fluo_pattern_list[i]
					samplesMap.get(unique_identifier).put(
						fluo_channels_list[i],
						FolderOpener.open(
							current_directory.getAbsolutePath(),
							" filter=" + fluo_pattern_list[i]
						)
					)
					// println samplesMap.get(unique_identifier).get(fluo_channels_list[i]).getDimensions()
					if (!GraphicsEnvironment.isHeadless()){
						samplesMap.get(unique_identifier).get(fluo_channels_list[i]).show()
					}
				}
			}
		} else {
			// It is easy as all images are used
			println "Processing " + unique_identifier + " Greys"
			samplesMap.get(unique_identifier).put(final_color, FolderOpener.open(current_directory.getAbsolutePath()))
			// println samplesMap.get(unique_identifier).get(final_color).getDimensions()
			if (!GraphicsEnvironment.isHeadless()){
				samplesMap.get(unique_identifier).get(final_color).show()
			}
		}
	}

	// Explore the HashMap and save to tiff
	for(String unique_identifier : samplesMap.keySet()){
		println "Merging " + unique_identifier
		// get the channel map
		Map<String, ImagePlus> channelsMap = samplesMap.get(unique_identifier)
		ArrayList<String> channels = []
		ArrayList<ImagePlus> current_images = []
		int ref_nT = 0
		boolean all_compatibles = true

		for(String channel : channelsMap.keySet()){
			channels.add(channel)
			current_images.add(channelsMap.get(channel))
			if (ref_nT == 0) {
				ref_nT = channelsMap.get(channel).nSlices
			} else {
				if (ref_nT != channelsMap.get(channel).nSlices) {
					all_compatibles = false
				}
			}
		}
		
		ImagePlus final_imp
		if (all_compatibles) {
			// Merge all
			ImagePlus merged_imps = Concatenator.run(current_images as ImagePlus[])
			// Re-order to make a multi-channel, time-lapse image
			if (channels.size() == 1 && ref_nT == 1) {
				final_imp = merged_imps
			} else {
				try {
					final_imp = HyperStackConverter.toHyperStack(merged_imps, channels.size() , 1, ref_nT, "xytcz", "Color")
					// set LUTs
					(0..channels.size()-1).each{
						final_imp.setC(it + 1)
						IJ.run(final_imp, channels[it], "")
						final_imp.resetDisplayRange()
					}
				} catch(Exception e) {
					println "Could not create the hyperstack for " + unique_identifier + ": " + e
					continue
				}
			}
		} else {
			println "Not all channels have the same number of slices:"
			(0..channels.size()-1).each{
				println "Channel " + channels[it] + " has " + current_images[it].getDimensions() + " whCZT."
			}
			if (channelsMap.containsKey("Greys")) {
				println "Will keep only Greys channel"
				final_imp = channelsMap.get("Greys")
			} else {
				println "Will keep only " + channels[0] + " channel"
				final_imp = current_images[0]
				IJ.run(final_imp, channels[0], "")
			}
			final_imp.resetDisplayRange()
		}
		// Save to tiff
		final_imp.setTitle(unique_identifier)

		if (!GraphicsEnvironment.isHeadless()){
			final_imp.show()
		}

		def fs = new FileSaver(final_imp)
		File output_path = new File (output_dir ,final_imp.getTitle()+"_merge.tif" )
		fs.saveAsTiff(output_path.toString() )

	}
	println "End of the script"

} catch (Throwable e) {
	println("Something went wrong: " + e)
	e.printStackTrace()
	throw e

	if (GraphicsEnvironment.isHeadless()){
		// Force to give exit signal of error
		System.exit(1)
	}

}

return

/**
 * ****************************************************************************************************************
 * ******************************************* End of the script **************************************************
 *
 * ****************************************************************************************************************
 *
 * *********************************** Helpers and processing methods *********************************************
 * ***************************************************************************************************************
 */

import ij.IJ
import ij.ImagePlus
import ij.io.FileSaver
import ij.io.Opener
import ij.plugin.Concatenator
import ij.plugin.FolderOpener
import ij.plugin.HyperStackConverter
import ij.process.LUT

import java.awt.GraphicsEnvironment
import java.io.File
