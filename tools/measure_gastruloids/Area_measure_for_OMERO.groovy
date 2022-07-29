// This macro was written by the BIOP (https://github.com/BIOP)
// Lucille Delisle modified to support headless

// This macro will use ilastik to detect ROIs
// measure and compute elongation index

// This macro works both in headless (write rois to files)
// or GUI (put rois in data manager)

// In headless,
// The coordinates of each rois are written in
// output_directory, one ROI per file, the file
// name is {image_basename}__{roi_index}_roi_coordinates.txt
// On each roi, the elongation index is computed.
// The coordinates of each circle (x, y, diameter)
// and the x, y of each point of the spine
// are written into a file:
// {image_basename}__{roi_index}_elongation_rois.txt

// In both modes,
// The result table is written to {image_basename}__Results.csv
// The measures are: Area, Perim., Circ.,Feret,FeretX,FeretY,FeretAngle,MinFeret,AR,Round,Solidity,Date,Version,Largest_Radius,Spine_length,Elongation_index
// Spine_length and Elongation_index are set to 0 if only 1 circle fits into the ROI.

import ij.*
import ij.process.*
import ij.gui.*
import ij.io.FileSaver
import ij.measure.Measurements

import ij.plugin.*
import ij.plugin.frame.*
import ij.plugin.filter.GaussianBlur
import ij.plugin.filter.ParticleAnalyzer

import java.awt.*;

import org.ilastik.ilastik4ij.ui.* 

import org.apache.commons.io.FilenameUtils

import net.imglib2.img.display.imagej.ImageJFunctions

import java.awt.GraphicsEnvironment

// For the elongation index:
import ch.epfl.biop.MaxInscribedCircles;
import ch.epfl.biop.CirlcesBasedSpine;
import ch.epfl.biop.CirlcesBasedSpine$Settings;

import ij.plugin.frame.RoiManager;

/**
 * Color balance , modified from beanshell by BIOP
 */

def colorBalance(imp){
    // White Balance based on ROI
    
    // Make sure it is RGB
    if (imp.getType() != ImagePlus.COLOR_RGB) {
        return;
    }
    //get ROI or make one if not available
    Roi theRoi = imp.getRoi();
    if (theRoi == null) {
        IJ.log("No ROI, making a square at (0,0) of width 65 px"); 
        theRoi = new Roi(0, 0, 65,65, imp);
    }
    //Remove ROI before duplication
    imp.killRoi();
    
    ImagePlus imp2 = imp.duplicate();
    imp2.setTitle("Color Balanced "+imp.getTitle());
    
    // Make a 3 slice stack
    ImageConverter ic = new ImageConverter(imp2);
    ic.convertToRGBStack();
    imp2.setRoi(theRoi);
    statOptions = Measurements.MEAN+Measurements.MEDIAN;
    
    // Calculate mean/median of each color
    imp2.setPosition(1); //R
    ImageStatistics isR = imp2.getStatistics(statOptions);
    imp2.setPosition(2); //G
    ImageStatistics isG = imp2.getStatistics(statOptions);
    imp2.setPosition(3); //B
    ImageStatistics isB = imp2.getStatistics(statOptions);
    
    //IJ.log("R:"+isR.mean+", G:"+isG.mean+", B:"+isB.mean);    
    double[] rgb = [isR.mean , isG.mean , isB.mean];
    
    // find largest value.
    double maxVal = 0;
    int idx = -1;
    double scale;
    for(i=0; i<3;i++) {
        if (rgb[i] > maxVal) {
            idx = i;
            maxVal = rgb[i];
            scale = 255/maxVal;
        }
    }
    
    // Remove ROI again to make sure we apply the multiplication to the whole image
    imp2.killRoi();
    
    for (i=0; i<3; i++) {
        imp2.setPosition(i+1);
        ip = imp2.getProcessor();
        val = maxVal/rgb[i]*scale;
        IJ.log(""+val+", "+rgb[i]+", "+maxVal);
        ip.multiply(maxVal/rgb[i]*scale); //Scaling the other channels to the largest one.
    }
    
    // Convert it back
    ic = new ImageConverter(imp2);
    ic.convertRGBStackToRGB();
    
    //Show the image
    return imp2;
}


def processDirectory(File input_directory, String suffix, Float scale,
                     Float radius_GB, File ilastik_project, Float probability_threshold, Integer min_size_particle,
                     Integer minimum_diameter, Integer closeness_tolerance, Float min_similarity,
                     File output_directory,
                     Boolean headless_mode, Boolean debug, String tool_version) {
    def file_list = input_directory.listFiles()
    for (int i = 0; i < file_list.length; i++) {
        println file_list[i]
        if (file_list[i].isDirectory()) {
            println "Folder"
            processDirectory(file_list[i], suffix, scale,
                             radius_GB, ilastik_project, probability_threshold, min_size_particle,
                             minimum_diameter, closeness_tolerance, min_similarity,
                             output_directory,
                             headless_mode, debug, tool_version)
        }
        if (FilenameUtils.getExtension(file_list[i].getName()) == suffix) {
            processImage(file_list[i], scale,
                         radius_GB, ilastik_project, probability_threshold, min_size_particle,
                         minimum_diameter, closeness_tolerance, min_similarity,
                         output_directory,
                         headless_mode, debug, tool_version)
        } else {
            println "Skipping not good extension"
        }
    }
}

def processImage(File image, Float scale,
                 Float radius_GB, File ilastik_project, Float probability_threshold, Integer min_size_particle,
                 Integer minimum_diameter, Integer closeness_tolerance, Float min_similarity,
                 File output_directory,
                 Boolean headless_mode, Boolean debug, String tool_version) {

    IJ.run("Close All", "")
    IJ.run("Clear Results")

    def image_name = image.getName();
    def image_basename = FilenameUtils.getBaseName( image_name );
    println "Processing " + image_basename;

    def imp = IJ.openImage( image.toString() );
    if (!headless_mode) {
        imp.show()
    }

    imp.setRoi(0,0,255,255);

    def clrBlcd_imp = colorBalance(imp)
    println "Color balance done !";
    // make RGB to grey
    IJ.run(clrBlcd_imp, "16-bit", "")

    gb_imp = clrBlcd_imp.duplicate()
    gb_imp.setTitle("GB")


    def gb = new GaussianBlur()
    gb.blur( gb_imp.getProcessor() , radius_GB)

    def ff_imp = ImageCalculator.run(clrBlcd_imp, gb_imp, "Divide create 32-bit");
    ff_imp.setTitle(image_basename+"_FF_CB")

    if (!headless_mode) {
        ff_imp.show()
    }

    println "Starting ilastik";

    // can't work without displaying image
    // IJ.run("Run Pixel Classification Prediction", "projectfilename="+ilastik_project+" inputimage="+ff_imp.getTitle()+" pixelclassificationtype=Probabilities");
    //
    // to use in headless_mode more we need to use a commandservice
    def predictions_imgPlus = cmds.run( IlastikPixelClassificationCommand.class , false , 
                                    'inputImage' , ff_imp , 
                                    'projectFileName', ilastik_project , 
                                    'pixelClassificationType', "Probabilities").get().getOutput("predictions")                         
    // to convert the result to ImagePlus : https://gist.github.com/GenevieveBuckley/460d0abc7c1b13eee983187b955330ba
    predictions_imp = ImageJFunctions.wrap(predictions_imgPlus, "predictions") 

    println "Ilastik done !";

    predictions_imp.setTitle("ilastik_output")

    if (!headless_mode) {
        predictions_imp.show()
    }

    mask_ilastik_imp = new Duplicator().run(predictions_imp, 1, 1, 1, 1, 1, 1);
    // This title will appear in the result table
    mask_ilastik_imp.setTitle(image_basename)
    IJ.setThreshold(mask_ilastik_imp, probability_threshold, 1000000000000000000000000000000.0000);
    Prefs.blackBackground = true;
    IJ.run(mask_ilastik_imp, "Convert to Mask", "");

    IJ.run(mask_ilastik_imp, "Options...", "iterations=10 count=3 black do=Open");
    IJ.run(mask_ilastik_imp, "Fill Holes", "");

    IJ.run("Set Measurements...", "area feret's perimeter shape display redirect=None decimal=3");
    IJ.run("Set Scale...", "distance=1 known="+scale+" unit=micron");
    // mask_ilastik_imp.show()
    IJ.run(mask_ilastik_imp, "Analyze Particles...", "size="+ min_size_particle + "-Infinity exclude show=Overlay");
    
    println "Found " + rt.size() + " ROI";
    
    // Get Date
    Date date = new Date()
    String now = date.format("yyyy-MM-dd_HH-mm")

    // Add Date and version
    for ( int row = 0;row<rt.size();row++) {
        rt.setValue("Date", row, now)
        rt.setValue("Version", row, tool_version)
    }

    def overlay = mask_ilastik_imp.getOverlay()
    if (headless_mode) {
        println "Writting ROI to files";
        ( 0..overlay.size()-1 ).collect{
            File file = new File(output_directory.toString() + "/" + image_basename + "__" + it + "_roi_coordinates.txt")
            for (Point p : overlay.get(it)) {
                file.append(p.x + "\t" + p.y + "\n");
            }
        }

    } else {
        mask_ilastik_imp.show()
        def rm = new RoiManager()
        rm = rm.getRoiManager()
        for (j in 0..(overlay.size()-1)){
            rm.addRoi(overlay.get(j))
        }
        println rm.getCount()
    }
    
    // Get elongation index
    println overlay.size()
    def overlay_copy = overlay.toArray()
    println overlay_copy.size()
    println overlay_copy
    println overlay_copy.getClass().getName()
    pixelWidth = mask_ilastik_imp.getCalibration().pixelWidth
    println "Computing elongation index"
    for (i in 0..(overlay_copy.size()-1) ){
        println i
        current_roi = overlay_copy[i]
        mask_ilastik_imp.setRoi(current_roi);
        def ArrayList<Roi> circles = MaxInscribedCircles.findCircles(mask_ilastik_imp, minimum_diameter, true);
        println "Inscribed " + circles.size() + " circles."
        if (circles.size() > 0){
            // get the first roi (largest circle)
            circle_roi = circles.get(0)
            circle_roi_radius = circle_roi.getStatistics().width / 2
            rt.setValue("Largest_Radius", i, circle_roi_radius * pixelWidth)
            if (headless_mode) {
                File file_elong = new File(output_directory.toString() + "/" + image_basename + "__" + i + "_elongation_rois.txt")
                if (debug) {
                    // First put all circles characteristics:
                    for (Roi circle in circles) {
                        width = circle.getStatistics().width
                        x = circle.getStatistics().xCentroid - width / 2
                        y = circle.getStatistics().yCentroid - width / 2
                        file_elong.append(x + "\t" + y + "\t" + width + "\n")
                    }
                } else {
                    // First put the largest circle characteristics:
                    x = circle_roi.getStatistics().xCentroid - circle_roi_radius
                    y = circle_roi.getStatistics().yCentroid - circle_roi_radius
                    file_elong.append(x + "\t" + y + "\t" + 2 * circle_roi_radius + "\n")
                }
            } else {
                def rm = new RoiManager()
                rm = rm.getRoiManager()
                if (debug) {
                    for (Roi circle in circles) {
                        rm.addRoi(circle);
                    }
                } else {
                    rm.addRoi(circle_roi);
                }
            }
            if (circles.size() > 1) {
                circles_copy = circles.clone()
                CirlcesBasedSpine sbp = new CirlcesBasedSpine$Settings(imp)
                .closenessTolerance(closeness_tolerance)
                .minSimilarity(min_similarity)
                .showCircles(false)
                .circles(circles_copy)
                .build();
                try {
                    def PolygonRoi spine = sbp.getSpine();
                    line_roi_length = spine.getLength()
                    rt.setValue("Spine_length", i, line_roi_length * pixelWidth)
                    rt.setValue("Elongation_index", i, line_roi_length / (2*circle_roi_radius))
                    if (headless_mode) {
                        File file_elong = new File(output_directory.toString() + "/" + image_basename + "__" + i + "_elongation_rois.txt")
                        // Then the spine coordinates
                        x = spine.getPolygon().xpoints
                        y = spine.getPolygon().ypoints
                        x.eachWithIndex { current_x, index ->
                            file_elong.append(current_x + "\t" + y[index] + "\n");
                        }
                    } else {
                        def rm = new RoiManager()
                        rm = rm.getRoiManager()
                        rm.addRoi(spine);
                    }
                } catch(Exception e) {
                    println("Could not create spine: ${e}")
                    rt.setValue("Spine_length", i, 0)
                    rt.setValue("Elongation_index", i, 0)
                }
            } else {
                rt.setValue("Spine_length", i, 0)
                rt.setValue("Elongation_index", i, 0)
            }
        } else {
            rt.setValue("Largest_Radius", i, 0)
            rt.setValue("Spine_length", i, 0)
            rt.setValue("Elongation_index", i, 0)
        }
    }
    println "Writting measurements to file";
    rt.save(output_directory.toString() + '/' + image_basename+"__Results.csv" )

}

// Specify global variables

def String tool_version = "20220728"

// User set variables

#@ String(visibility=MESSAGE, value="Inputs", required=false) msg
#@ File(style="directory", label="Directory with images to process") input_directory
#@ String(label="Suffix of images to process", description="objective 4x 2.27 on TC microscope for x10 use 0.92 (measured by PierreO)", value="tif") suffix
#@ Float(label="Scale = measure of a pixel in um", min=0, value=0.92) scale 


#@ String(visibility=MESSAGE, value="Parameters for segmentation/ROI", required=false) msg2
#@ Float(label="Radius of Gaussian Blur", min=0, value=200.0) radius_GB
#@ File(label="Ilastik project") ilastik_project
#@ Float(label="Probability threshold for ilastik", min=0, max=1, value=0.4) probability_threshold
#@ Integer(label="Minimum surface for Analyze Particle", value=5000) min_size_particle

#@ String(visibility=MESSAGE, value="Parameters for elongation index", required=false) msg3
#@ Integer(label="Minimum diameter of inscribed circles", min=0, value=20) minimum_diameter
#@ Integer(label="Closeness Tolerance (Spine)", min=0, value=5) closeness_tolerance
#@ Float(label="Min similarity (Spine)", min=0, max=0, value=0.1) min_similarity

#@ String(visibility=MESSAGE, value="Parameters for output", required=false) msg4
#@ File(style = "directory", label="Directory where measures are put") output_directory
#@ Boolean(label="<html>Run in debug mode<br/>(get all inscribed circles)</html>", value=false) debug

#@ ResultsTable rt
#@ CommandService cmds
#@ ConvertService cvts
#@ DatasetService ds
#@ DatasetIOService io

// java.awt.GraphicsEnvironment.checkheadless_mode(GraphicsEnvironment.java:204)
def Boolean headless_mode = GraphicsEnvironment.isHeadless()

if (!headless_mode){
    def rm = new RoiManager()
    rm = rm.getRoiManager()
    rm.reset()
}

println "Results will be in " + output_directory.toString()

processDirectory(input_directory, suffix, scale,
                 radius_GB, ilastik_project, probability_threshold, min_size_particle,
                 minimum_diameter, closeness_tolerance, min_similarity,
                 output_directory,
                 headless_mode, debug, tool_version)

if (headless_mode){
    // This is due to Gaussian Blur
    System.exit(0)
}
return