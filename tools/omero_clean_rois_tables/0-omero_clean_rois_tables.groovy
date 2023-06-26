// This macro was written by Lucille Delisle
// with templates available at
// https://github.com/BIOP/OMERO-scripts/tree/main/Fiji

// Last modification: 2023-06-23

// This tool will remove all ROIs associated to images
// children of the omero object specified.
// It will also remove tables associated to these objects
// as well as any object above. 
// This macro works both in headless
// or GUI

import fr.igred.omero.annotations.TableWrapper
import fr.igred.omero.Client
import fr.igred.omero.repository.DatasetWrapper
import fr.igred.omero.repository.GenericRepositoryObjectWrapper
import fr.igred.omero.repository.ImageWrapper
import fr.igred.omero.repository.PlateWrapper
import fr.igred.omero.repository.PixelsWrapper
import fr.igred.omero.repository.WellWrapper
import fr.igred.omero.roi.ROIWrapper

import java.util.concurrent.TimeUnit
import java.util.stream.Collectors

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

def robustlyDeleteROIs(ImageWrapper image_wrp, Client user_client) {
    for (waiting_time in waiting_times) {
        try {
            // Remove existing ROIs
            // image_wrp.getROIs(user_client).each{ user_client.delete(it) }
            // Caused failure due to too high number of 'servantsPerSession'
            // Which reached 10k
            // I use see https://github.com/GReD-Clermont/simple-omero-client/issues/59
            user_client.delete((Collection<ROIWrapper>) image_wrp.getROIs(user_client))
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

def cleanDataset(Client user_client, DatasetWrapper dataset_wrp,
                 String final_object) {
    robustlyGetAll(dataset_wrp, "image", user_client).each{ ImageWrapper img_wrp ->
        cleanImage(user_client, img_wrp,
                   final_object)
    }
}

def cleanSinglePlate(Client user_client, PlateWrapper plate_wrp,
                     String final_object) {
    robustlyGetAll(plate_wrp, "well", user_client).each{ well_wrp ->
        cleanSingleWell(user_client, well_wrp,
                        final_object)
    }
}

def cleanSingleWell(Client user_client, WellWrapper well_wrp,
                    String final_object) {
    well_wrp.getWellSamples().each{
        cleanImage(user_client, it.getImage(),
                   final_object)
    }
}

def cleanImage(Client user_client, ImageWrapper image_wrp,
               String final_object) {

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
        if (final_object == "image") {
            // Remove tables from datasets:
            dataset_wrp_list.each{  DatasetWrapper dataset_wrp ->
                robustlyDeleteTables(dataset_wrp, user_client)
            }
        }
        robustlyGetAll(image_wrp, "project", user_client).each{
            println("Project_name : "+it.getName()+" / id : "+it.getId())
        }
    }

    // if the image is part of a plate
    else {
        List<WellWrapper> well_wrps = robustlyGetAll(image_wrp, "well", user_client)
        if (!well_wrps.isEmpty()) {
            WellWrapper well_wrp = well_wrps.get(0)
            println ("Well_name : "+well_wrp.getName() +" / id : "+ well_wrp.getId())
            if (final_object == "image") {
                // Remove tables from well:
                robustlyDeleteTables(well_wrp, user_client)
            }
        }
        List<PlateWrapper> plate_wrps = robustlyGetAll(image_wrp, "plate", user_client)
        if (!plate_wrps.isEmpty()) {
            PlateWrapper plate_wrp = plate_wrps.get(0)
            println ("plate_name : "+plate_wrp.getName() + " / id : "+ plate_wrp.getId())
            if (final_object == "image" || final_object == "well") {
                // Remove tables from plate:
                robustlyDeleteTables(plate_wrp, user_client)
            }
        }

        robustlyGetAll(image_wrp, "screen", user_client).each{
            println ("screen_name : "+it.getName() + " / id : "+ it.getId())
        }
    }
    println "Remove existing ROIs and tables on OMERO"
    // Remove existing ROIs
    robustlyDeleteROIs(image_wrp, user_client)
    robustlyDeleteTables(image_wrp, user_client)
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

    try {

        switch (object_type) {
            case "image":
                ImageWrapper image_wrp = robustlyGetOne(id, "image", user_client)
                cleanImage(user_client, image_wrp, "image")
                println("Cleaned successfully")
                break
            case "dataset":
                DatasetWrapper dataset_wrp = robustlyGetOne(id, "dataset", user_client)
                // Remove all tables from dataset:
                robustlyDeleteTables(dataset_wrp, user_client)
                cleanDataset(user_client, dataset_wrp, "dataset")
                println("Cleaned successfully")
                break
            case "well":
                WellWrapper well_wrp = robustlyGetOne(id, "well", user_client)
                // Remove all tables from well:
                robustlyDeleteTables(well_wrp, user_client)
                cleanSingleWell(user_client, well_wrp, "well")
                println("Cleaned successfully")
                break
            case "plate":
                PlateWrapper plate_wrp = robustlyGetOne(id, "plate", user_client)
                // Remove all tables from Plate:
                robustlyDeleteTables(plate_wrp, user_client)
                cleanSinglePlate(user_client, plate_wrp, "plate")
                println("Cleaned successfully")
                break
        }

    } catch(Exception e) {
        println("Something went wrong: " + e)
        e.printStackTrace()
        throw e
    } finally {
        user_client.disconnect()
        println "Disonnected " + host
    }
} else {
    throw new Exception("Not able to connect to " + host)
}
return
