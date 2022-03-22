// This macro was written thanks to
// https://code.adonline.id.au/imagej-batch-process-headless/
// and
// https://visikol.com/2019/02/building-an-imagej-macro-for-batch-processing-of-images-from-imaging-well-plates/
// The analysis part of the macro is from Pierre Osteil adapted from Morten et al. 2018 https://doi.org/10.1038/s41598-017-18815-8

// Specify global variables

tool_version = "20220322";

parameter_string = getArgument();
print(parameter_string);
arglist = split(parameter_string, ",");

inputDirectory = arglist[0];
outputDirectory = arglist[1];
suffix = arglist[2];
// maxThreshold = arglist[3];

setBatchMode(true); 

close("\\Others");
close("Summary");

print("Will process the folder")

processFolder(inputDirectory);

print("Finished processed the folder");

setBatchMode(false); // Now disable BatchMode since we are finished

// Scan folders/subfolders/files to locate files with the correct suffix

function processFolder(input) {
    list = getFileList(input);
    print(list.length);
    for (i = 0; i < list.length; i++) {
        print(list[i]);
        if(File.isDirectory(input + "/" + list[i])){
            print('Folder');
            processFolder("" + input + "/" + list[i]);          
        }
        if(endsWith(list[i], suffix)){
            print('Process');
            processImage("" + input + "/" + list[i]);          
        }
    }
}


function processImage(imageFile)
{
    print(imageFile);
    run("Clear Results");
    print("nRes:" + nResults);
    open(imageFile);
    // Get the filename from the title of the image that's open for adding to the results table
    // We do this instead of using the imageFile parameter so that the
    // directory path is not included on the table
    filename = getTitle();
    
    print(filename);
    
    // Perform the analysis
    rename("original-image");
  	run("Duplicate...", "title=Image");
  	run("8-bit");
  	run("Sharpen");
  	run("Median...", "radius=1");
  	run("Duplicate...", "title=temp");
  	run("Maximum...", "radius=5");
  	run("Morphological Reconstruction", "marker=temp mask=Image type=[By Erosion] connectivity=4");
  	// Close previously used images:
  	selectWindow("Image");
  	close();
  	selectWindow("temp");
  	close();
  	selectWindow("temp-rec");
  	// Go on with analysis
  	run("Duplicate...", "title=temp2");
  	run("Minimum...", "radius=5");
  	run("Morphological Reconstruction", "marker=temp2 mask=temp-rec type=[By Dilation] connectivity=4");
  	// Close previously used images:
  	selectWindow("temp-rec");
  	close();
  	selectWindow("temp2");
  	close();
  	selectWindow("temp2-rec");
  	run("Invert LUT");
  	setAutoThreshold("MaxEntropy dark");
  	run("Convert to Mask");
  	run("Fill Holes");

    //Get the measurements
    rename(filename);
    run("Set Measurements...", "area feret's perimeter shape display redirect=None decimal=3");
    run("Set Scale...", "distance=1 known=0.92 unit=micron"); //objective 4x 2.27 on TC microscope for x10 use 0.92 (measured by PierreO)
    // run("Analyze Particles...", "size=10000-400000 circularity=0.05-1.00 display clear exclude add"); // add clear to have one Result file per image
    run("Analyze Particles...", "size=10000-400000 circularity=0.05-1.00 display clear exclude show=Overlay"); // add clear to have one Result file per image
    print("nRes:" + nResults);
    
    if (nResults == 0) {
        print("Running low contrast analysis");
        // Another analysis is performed:
        // First close all unused windows:
  	    selectWindow(filename);
  	    close();
  	    selectWindow("original-image");
        rename(filename);
        run("8-bit"); 
        run("Enhance Contrast", "saturated=0.35");
        run("Sharpen"); 
        run("Median...", "radius=1");
        run("Maximum...", "radius=5");
        setAutoThreshold("Li dark");
        run("Convert to Mask");
        run("Dilate");
        run("Dilate");
        run("Dilate");
        run("Fill Holes");
        
        //Get the measurements
        run("Set Measurements...", "area feret's perimeter shape display redirect=None decimal=3");
        run("Set Scale...", "distance=1 known=0.92 unit=micron"); //objective 4x 2.27 on TC microscope for x10 use 0.92 (measured by PierreO)
        // run("Analyze Particles...", "size=10000-400000 circularity=0.05-1.00 display clear exclude add"); // add clear to have one Result file per image
        run("Analyze Particles...", "size=10000-400000 circularity=0.05-1.00 display clear exclude show=Overlay"); // add clear to have one Result file per image
        print("nRes:" + nResults);
    } else {
        // Close the original-image
  	    selectWindow("original-image");
  	    close();
  	    selectWindow(filename);
    }
    // Get date:
    getDateAndTime(year, month, dayOfWeek, dayOfMonth, hour, minute, second, msec);
    TimeString = ""+year+"-";
    if (month<10) {TimeString = TimeString+"0";}
    TimeString = TimeString+month+"-";
    if (dayOfMonth<10) {TimeString = TimeString+"0";}
    TimeString = TimeString+dayOfMonth+"_";
    if (hour<10) {TimeString = TimeString+"0";}
    TimeString = TimeString+hour+"-";
    if (minute<10) {TimeString = TimeString+"0";}
    TimeString = TimeString+minute;
    // Now loop through each of the new results, and add the time to the "Date" column
    for (row = 0; row < nResults; row++)
    {
        setResult("Date", row, TimeString);
        setResult("Version", row, tool_version);
        // setResult("MaxThreshold", row, maxThreshold);
    }

    // Save the results data
    saveAs("results", outputDirectory + "/" + filename + "__Results.csv");
    // Now loop through Overlay:
    print("Overlay size:" + Overlay.size);
    for (i=0; i<Overlay.size; i++) {
        Overlay.activateSelection(i);
        getSelectionCoordinates(x,y);
        print("Opening file");
        f = File.open(outputDirectory + "/" + filename + "__" + i + "_roi_coordinates.txt");
        for (j=0; j<x.length; j++){
            print(f, x[j] + "\t" + y[j]);        
            print(f, x[j] + "\t" + y[j]);        
            print(f, x[j] + "\t" + y[j]);        
        }
        File.close(f);
    }
    print("Selection OK");
    close();  // Closes the current image
    print("OK");
}
 
