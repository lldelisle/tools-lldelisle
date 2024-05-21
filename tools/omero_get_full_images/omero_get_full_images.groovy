// This macro was written by
// Lucille Delisle using templates available at
// https://github.com/BIOP/OMERO-scripts/tree/025047955b5c1265e1a93b259c1de4600d00f107/Fiji

// Last modification: 2024-05-21

// The input image(s) may have multiple time stacks
// and multiple channels

// This macro works both in headless
// or GUI

// In both modes,
// Images of all omero object are written to the output directory

import fr.igred.omero.Client
import fr.igred.omero.repository.DatasetWrapper
import fr.igred.omero.repository.GenericRepositoryObjectWrapper
import fr.igred.omero.repository.ImageWrapper
import fr.igred.omero.repository.PlateWrapper
import fr.igred.omero.repository.WellWrapper

import ij.ImagePlus
import ij.IJ
import ij.io.FileSaver

import java.awt.GraphicsEnvironment
import java.io.File
import java.util.concurrent.TimeUnit

import org.apache.commons.io.FileUtils


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

def processDataset(Client user_client, DatasetWrapper dataset_wrp,
                   File output_directory,
                   Boolean headless_mode) {
    robustlyGetAll(dataset_wrp, "image", user_client).each{ ImageWrapper img_wrp ->
        processImage(user_client, img_wrp,
                     output_directory,
                     headless_mode)
    }
}

def processSinglePlate(Client user_client, PlateWrapper plate_wrp,
                       File output_directory,
                       Boolean headless_mode) {
    robustlyGetAll(plate_wrp, "well", user_client).each{ well_wrp ->
        processSingleWell(user_client, well_wrp,
                     output_directory,
                     headless_mode)
    }
}

def processSingleWell(Client user_client, WellWrapper well_wrp,
                      File output_directory,
                      Boolean headless_mode) {
    well_wrp.getWellSamples().each{
        ImageWrapper img_wrp = it.getImage()
        processImage(user_client, img_wrp,
                     output_directory,
                     headless_mode)
    }
}

def processImage(Client user_client, ImageWrapper image_wrp,
                 File output_directory,
                 Boolean headless_mode) {
    IJ.run("Close All", "")

    // Print image information
    println "\n Image infos"
    String image_basename = image_wrp.getName()
    println ("Image_name : " + image_basename + " / id : " + image_wrp.getId())

    println "Getting image from OMERO"

    ImagePlus imp = robustlytoImagePlus(image_wrp, user_client)
	// ImagePlus imp = IJ.openImage("/home/ldelisle/Desktop/EXP095_LE_PEG_CTGF_PLATE_120h.companion.ome [C2_1_merge].tif")

    if (!headless_mode) {
        imp.show()
    }

    // Write to file
    File output_path = new File (output_directory, image_basename + ".tiff" )
                // save file
    FileSaver fs = new FileSaver(imp)
    fs.saveAsTiff(output_path.toString())
    return
}

// User set variables

#@ String(visibility=MESSAGE, value="Inputs", required=false) msg
#@ String(label="User name") USERNAME
#@ String(label="PASSWORD", style='PASSWORD', value="", persist=false) PASSWORD
#@ String(label="File path with omero credentials") credentials
#@ String(label="omero host server") host
#@ Integer(label="omero host server port", value=4064) port
#@ String(label="Object", choices={"image","dataset","well","plate"}) object_type
#@ Long(label="ID", value=119273) id

#@ String(visibility=MESSAGE, value="Parameters for output", required=false) msg5
#@ File(style = "directory", label="Directory where measures are put") output_directory

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

        switch (object_type) {
            case "image":
                ImageWrapper image_wrp = robustlyGetOne(id, "image", user_client)
                processImage(user_client, image_wrp,
                    output_directory,
                    headless_mode)
                break
            case "dataset":
                DatasetWrapper dataset_wrp = robustlyGetOne(id, "dataset", user_client)
                processDataset(user_client, dataset_wrp,
                     output_directory,
                     headless_mode)
                break
            case "well":
                WellWrapper well_wrp = robustlyGetOne(id, "well", user_client)
                processSingleWell(user_client, well_wrp,
                     output_directory,
                     headless_mode)
                break
            case "plate":
                PlateWrapper plate_wrp = robustlyGetOne(id, "plate", user_client)
                processSinglePlate(user_client, plate_wrp,
                     output_directory,
                     headless_mode)
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
